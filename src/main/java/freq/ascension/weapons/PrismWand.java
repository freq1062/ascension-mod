package freq.ascension.weapons;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.ItemLore;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.math.Transformation;

import freq.ascension.Ascension;
import freq.ascension.Config;
import freq.ascension.animation.GeometrySource;
import freq.ascension.api.ContinuousTask;
import freq.ascension.api.DelayedTask;
import freq.ascension.orders.Magic;
import freq.ascension.orders.Order;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Brightness;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Display.BlockDisplay;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Prism Wand — mythical weapon for the Magic order.
 *
 * <p>A bow enchanted with Power 5, Flame, and Curse of Vanishing.
 *
 * <p><b>Aimbot Arrow (fully charged shot):</b> When the player fires a fully-charged shot
 * (≥ 20 ticks drawn), the vanilla arrow is cancelled and an aimbot scan runs.
 * If a living entity is within 64 blocks and 5° of the look vector with a clear line of
 * sight, a {@code PrismBolt} — a homing {@link BlockDisplay} of
 * {@code Blocks.PINK_GLAZED_TERRACOTTA} — is launched toward the target at 1.5 blocks/tick.
 * On contact (≤ 0.7 blocks) or after 80 ticks it deals 10 % of the target's max HP as
 * magic damage. A lock-on beam ({@code Blocks.PINK_STAINED_GLASS} BlockDisplay) is also
 * spawned between player eyes and target eyes for 2 seconds.
 *
 * <p>If no target is found a normal Power-5 Flame arrow is fired.
 *
 * <p><b>Melee effect (onAttack):</b> Copies every positive {@link MobEffectInstance} from
 * the victim to the attacker using vanilla stacking rules (addEffect already handles
 * amplifier/duration precedence).
 *
 * <p>Bow detection is handled by {@link freq.ascension.mixin.BowReleaseMixin} which cancels
 * {@code BowItem.releaseUsing} when the draw is full and the player holds this weapon, then
 * delegates to {@link #handleAimbotShot(ServerPlayer, ServerLevel, ItemStack)}.
 */
public class PrismWand implements MythicWeapon {

    public static final PrismWand INSTANCE = new PrismWand();

    /** Maximum targeting range in blocks. */
    private static double MAX_RANGE = 64.0;
    /** Maximum off-axis angle in degrees for aimbot lock. */
    private static double MAX_ANGLE_DEG = 10.0;
    /** PrismBolt movement speed in blocks per tick. */
    private static float BOLT_SPEED = 1.5f;
    /** Fraction of target max HP dealt as spell damage on bolt impact. */
    private static float SPELL_DAMAGE_FRACTION = 0.10f;
    /** Lock-on beam visual thickness. */
    private static final float BEAM_THICKNESS = 0.12f;
    /** PrismBolt visual cube half-size. */
    private static final float BOLT_HALF = 0.15f;
    /** Hot-pink glow colour used on all VFX entities. */
    private static final int PINK_COLOR = 0xFF69B4;

    /**
     * Per-thread player reference set by {@link freq.ascension.mixin.BowReleaseMixin} when
     * a fully-charged PrismWand shot is detected.  The ENTITY_LOAD hook reads it to intercept
     * the spawned Arrow.  Both the mixin inject and addFreshEntity run on the main server
     * thread, so a ThreadLocal is safe.
     */
    public static final ThreadLocal<ServerPlayer> PENDING_AIMBOT =
            ThreadLocal.withInitial(() -> null);

    private static boolean registered = false;

    // ─── Identity ─────────────────────────────────────────────────────────────

    @Override
    public String getWeaponId() {
        return "prism_wand";
    }

    @Override
    public Item getBaseItem() {
        return Items.BOW;
    }

    @Override
    public Order getParentOrder() {
        return Magic.INSTANCE;
    }

    // ─── Item creation ────────────────────────────────────────────────────────

    @Override
    public ItemStack createItem() {
        ItemStack stack = buildBaseItem();
        var enchReg = Ascension.getServer().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        stack.enchant(enchReg.getOrThrow(Enchantments.POWER), 5);
        stack.enchant(enchReg.getOrThrow(Enchantments.FLAME), 1);
        stack.enchant(enchReg.getOrThrow(Enchantments.VANISHING_CURSE), 1);
        stack.set(DataComponents.LORE, new ItemLore(List.of(
            Component.literal("Fully charged shot: homing bolt that deals 10% of").withStyle(s -> s.withItalic(true).withColor(ChatFormatting.GRAY)),
            Component.literal("the target's max HP as magic damage.").withStyle(s -> s.withItalic(true).withColor(ChatFormatting.GRAY)),
            Component.literal("Melee hits steal positive effects from the victim.").withStyle(s -> s.withItalic(true).withColor(ChatFormatting.GRAY))
        )));
        return stack;
    }

    // ─── Melee: copy positive effects ─────────────────────────────────────────

    @Override
    public void onAttack(ServerPlayer attacker, LivingEntity victim, Order.DamageContext ctx) {
        if (ctx.isCancelled()) return;
        for (MobEffectInstance effect : victim.getActiveEffects()) {
            if (!effect.getEffect().value().isBeneficial()) continue;
            // addEffect already applies vanilla stacking rules (higher amp / longer duration wins)
            attacker.addEffect(new MobEffectInstance(effect));
        }
    }

    // ─── Aimbot: entry point called from BowReleaseMixin ──────────────────────

    /**
     * Runs when a fully-charged PrismWand bow shot is detected.  Finds the best aimbot target
     * (angle + LOS) and either spawns the lock-on beam + PrismBolt or fires a plain arrow.
     *
     * @param bowStack the bow ItemStack (used for the fallback arrow's weapon reference)
     */
    public void handleAimbotShot(ServerPlayer player, ServerLevel level, ItemStack bowStack) {
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookDir = player.getLookAngle();

        // Collect candidate entities in a bounding box around the player
        AABB box = new AABB(eyePos.subtract(Config.prismWandRange, Config.prismWandRange, Config.prismWandRange),
                            eyePos.add(Config.prismWandRange, Config.prismWandRange, Config.prismWandRange));
        List<LivingEntity> candidates = level.getEntitiesOfClass(
                LivingEntity.class, box, e -> e != player && e.isAlive());

        // Filter to entities with a clear line of sight
        List<LivingEntity> visible = candidates.stream()
                .filter(e -> hasLineOfSight(level, eyePos, e.getEyePosition(), player))
                .toList();

        LivingEntity target = findTarget(eyePos, lookDir, visible, Config.prismWandRange, Config.prismWandAngleDeg);

        if (target != null) {
            // Play a chime to confirm lock-on before the bolt launches
            level.playSound(null, player.blockPosition(),
                    SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.5f);
            BeamContext beamCtx = spawnLockOnBeam(level, eyePos, target);
            spawnPrismBolt(level, eyePos, target, beamCtx);
        } else {
            fireNormalArrow(player, level);
        }
    }

    // ─── Static helper exposed for testing ────────────────────────────────────

    /**
     * Finds the closest {@link LivingEntity} within {@code maxRange} blocks that lies within
     * {@code maxAngleDeg} degrees of {@code lookDir} from {@code eyePos}.
     *
     * <p>Skips creative-mode {@link ServerPlayer} instances.  Does <em>not</em> perform any
     * block-collision (LOS) check — callers that need LOS should pre-filter {@code candidates}
     * before passing them here.
     *
     * @param eyePos       shooter's eye position
     * @param lookDir      shooter's normalised look direction
     * @param candidates   living entities to evaluate
     * @param maxRange     maximum range in blocks
     * @param maxAngleDeg  maximum off-axis angle in degrees
     * @return the closest qualifying entity, or {@code null} if none
     */
    public static LivingEntity findTarget(Vec3 eyePos, Vec3 lookDir,
            List<LivingEntity> candidates, double maxRange, double maxAngleDeg) {
        double cosMax = Math.cos(Math.toRadians(maxAngleDeg));
        Vec3 look = lookDir.normalize();

        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;

        for (LivingEntity e : candidates) {
            // Skip creative players
            if (e instanceof ServerPlayer sp &&
                    sp.gameMode.getGameModeForPlayer() == net.minecraft.world.level.GameType.CREATIVE) {
                continue;
            }

            Vec3 toEntity = e.getEyePosition().subtract(eyePos);
            double dist = toEntity.length();
            if (dist > maxRange || dist < 1e-6) continue;

            double cosAngle = toEntity.normalize().dot(look);
            if (cosAngle < cosMax) continue;

            if (dist < bestDist) {
                bestDist = dist;
                best = e;
            }
        }
        return best;
    }

    // ─── VFX: lock-on beam ────────────────────────────────────────────────────

    /** Carries the spawned beam entity together with the transform components used to create
     *  it so the shrink animation can preserve rotation without calling getTransformation(). */
    private record BeamContext(BlockDisplay beam, Quaternionf rotation, Vector3f offset) {}

    private static BeamContext spawnLockOnBeam(ServerLevel level, Vec3 eyePos, LivingEntity target) {
        Vec3 targetEye = target.getEyePosition();

        Vector3f from = new Vector3f((float) eyePos.x, (float) eyePos.y, (float) eyePos.z);
        Vector3f toTarget = new Vector3f(
                (float) (targetEye.x - eyePos.x),
                (float) (targetEye.y - eyePos.y),
                (float) (targetEye.z - eyePos.z));
        float dist = toTarget.length();
        if (dist < 1e-4f) return null;

        Quaternionf rotation = GeometrySource.faceVector(new Vector3f(toTarget).normalize());
        float T = BEAM_THICKNESS;
        // Local offset to center the block-display column on the beam axis
        Vector3f offset = rotation.transform(new Vector3f(-T * 0.5f, 0, -T * 0.5f), new Vector3f());

        BlockDisplay beam = EntityType.BLOCK_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
        if (beam == null) return null;
        beam.setBlockState(Blocks.PINK_STAINED_GLASS.defaultBlockState());
        beam.setPos(from.x, from.y, from.z);
        beam.setTransformation(new Transformation(offset, rotation, new Vector3f(T, dist, T), null));
        beam.setGlowingTag(true);
        beam.setGlowColorOverride(PINK_COLOR);
        beam.setBrightnessOverride(Brightness.FULL_BRIGHT);
        beam.setViewRange(5.0f);
        level.addFreshEntity(beam);

        // Auto-remove after 2 seconds (bolt impact may shrink and discard it earlier)
        Ascension.scheduler.schedule(new DelayedTask(40, () -> {
            if (!beam.isRemoved()) beam.discard();
        }));

        return new BeamContext(beam, rotation, offset);
    }

    // ─── VFX: homing PrismBolt ────────────────────────────────────────────────

    private static void spawnPrismBolt(ServerLevel level, Vec3 startPos, LivingEntity target,
            BeamContext beamCtx) {
        BlockDisplay lockOnBeam = beamCtx != null ? beamCtx.beam() : null;
        float half = BOLT_HALF;

        BlockDisplay bolt = EntityType.BLOCK_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
        if (bolt == null) return;
        bolt.setBlockState(Blocks.PINK_GLAZED_TERRACOTTA.defaultBlockState());
        bolt.setPos(startPos.x, startPos.y, startPos.z);
        // Centered cube with no rotation (initial state)
        bolt.setTransformation(new Transformation(
                new Vector3f(-half, -half, -half), new Quaternionf(),
                new Vector3f(half * 2, half * 2, half * 2), null));
        bolt.setGlowingTag(true);
        bolt.setGlowColorOverride(PINK_COLOR);
        bolt.setBrightnessOverride(Brightness.FULL_BRIGHT);
        level.addFreshEntity(bolt);

        int[] alive = {0};
        boolean[] impacted = {false};
        int[] shrinkTick = {0};
        ContinuousTask[] ref = {null};
        ref[0] = new ContinuousTask(1, () -> {
            alive[0]++;
            if (bolt.isRemoved()) {
                ref[0].stop();
                return;
            }

            // Impact shrink phase: linearly reduce X and Z scale over 10 ticks
            if (impacted[0]) {
                shrinkTick[0]++;
                float t = Math.max(0f, 1f - (shrinkTick[0] / 10f));
                float w = (half * 2) * t;
                float wh = w * 0.5f;
                bolt.setTransformation(new Transformation(
                        new Vector3f(-wh, -half, -wh), new Quaternionf(),
                        new Vector3f(w, half * 2, w), null));
                bolt.setTransformationInterpolationDuration(1);
                if (shrinkTick[0] >= 10) {
                    bolt.discard();
                    ref[0].stop();
                }
                return;
            }

            if (!target.isAlive() || alive[0] > 80) {
                bolt.discard();
                ref[0].stop();
                return;
            }

            Vec3 boltPos = bolt.position();
            Vec3 targetPos = target.getEyePosition();
            Vec3 delta = targetPos.subtract(boltPos);
            double dist = delta.length();

            if (dist <= 0.7) {
                // Impact: 10 % max-HP magic damage, then start width-shrink animation
                float dmg = target.getMaxHealth() * Config.prismWandDamageFraction;
                target.hurtServer(level, level.damageSources().magic(), dmg);
                impacted[0] = true;
                // Shrink the lock-on beam to zero scale over 5 ticks, then discard it.
                // Preserve the beam's original rotation and translation so the interpolation
                // only changes scale — using identity rotation here would cause the beam to
                // visually rotate as it shrinks (interpolating from beam-facing to identity).
                // The 40-tick auto-remove DelayedTask is harmless if it fires after discard.
                if (lockOnBeam != null && !lockOnBeam.isRemoved()) {
                    lockOnBeam.setTransformation(new Transformation(
                            beamCtx.offset(), beamCtx.rotation(), new Vector3f(0, 0, 0), null));
                    lockOnBeam.setTransformationInterpolationDelay(0);
                    lockOnBeam.setTransformationInterpolationDuration(5);
                    Ascension.scheduler.schedule(new DelayedTask(6, () -> {
                        if (!lockOnBeam.isRemoved()) lockOnBeam.discard();
                    }));
                }
                return;
            }

            // Move bolt toward target
            Vec3 step = delta.normalize().scale(Config.prismWandBoltSpeed);
            bolt.setPos(boltPos.x + step.x, boltPos.y + step.y, boltPos.z + step.z);

            // Spin the cube around its own center (Y-axis rotation, 10°/tick)
            float angle = alive[0] * 10f;
            Quaternionf rot = new Quaternionf().rotateY((float) Math.toRadians(angle));
            // Correct translation to keep the cube center at the entity origin after rotation
            Vector3f rotatedCenter = rot.transform(new Vector3f(half, half, half), new Vector3f());
            Vector3f translation = new Vector3f(-rotatedCenter.x, -rotatedCenter.y, -rotatedCenter.z);
            bolt.setTransformation(new Transformation(
                    translation, rot, new Vector3f(half * 2, half * 2, half * 2), null));
            bolt.setTransformationInterpolationDuration(1);
        });
        Ascension.scheduler.schedule(ref[0]);
    }

    // ─── Fallback: normal Power-5 Flame arrow ────────────────────────────────

    private static void fireNormalArrow(ServerPlayer player, ServerLevel level) {
        Arrow arrow = new Arrow(level, player, new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
        arrow.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 3.0F, 1.0F);
        arrow.setCritArrow(true);
        // Power 5: base 2.0 + 5*0.5+0.5 = 5.0
        arrow.setBaseDamage(5.0);
        // Flame: set arrow on fire so it ignites targets on hit
        arrow.setRemainingFireTicks(2000);
        level.addFreshEntity(arrow);
    }

    // ─── LOS helper ───────────────────────────────────────────────────────────

    private static boolean hasLineOfSight(ServerLevel level, Vec3 from, Vec3 to,
            ServerPlayer exclude) {
        BlockHitResult hit = level.clip(new ClipContext(
                from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, exclude));
        return hit.getType() == HitResult.Type.MISS;
    }

    // ─── Event registration ───────────────────────────────────────────────────

    /**
     * Registers the {@link ServerEntityEvents#ENTITY_LOAD} hook that intercepts arrows spawned
     * during a PrismWand fully-charged shot (flagged via {@link #PENDING_AIMBOT}).
     * Must be called from {@code Ascension.onInitialize()} before the server starts.
     */
    public static void register() {
        if (registered) return;
        registered = true;

        // When BowItem.releaseUsing fires (see BowReleaseMixin), it sets PENDING_AIMBOT to the
        // player and lets vanilla proceed.  The first Arrow entity to be added to the world
        // on behalf of that player is intercepted here: we discard it and run the aimbot instead.
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (!(entity instanceof Arrow arrow)) return;
            if (!(world instanceof ServerLevel serverLevel)) return;
            if (!arrow.isCritArrow()) return;
            if (!(arrow.getOwner() instanceof ServerPlayer player)) return;

            ServerPlayer pending = PENDING_AIMBOT.get();
            if (pending == null || pending != player) return;
            PENDING_AIMBOT.remove();

            // Discard the vanilla arrow and run the aimbot on the next tick (after
            // BowItem.releaseUsing has fully returned) to avoid modifying world state
            // in the middle of an entity-spawn sequence.
            arrow.discard();
            Ascension.scheduler.schedule(new DelayedTask(1, () ->
                    INSTANCE.handleAimbotShot(player, serverLevel,
                            player.getMainHandItem())));
        });
    }
}
