package freq.ascension.registry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.DoubleUnaryOperator;

import freq.ascension.Utils;
import freq.ascension.managers.ActiveSpell;
import freq.ascension.managers.AscensionData;
import freq.ascension.managers.Spell;
import freq.ascension.managers.SpellCooldownManager;
import freq.ascension.orders.*;
import freq.ascension.Ascension;
import freq.ascension.api.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class SpellRegistry {
    // {id: spell}
    public static final Map<String, Spell> SPELLS = new HashMap<>();
    // {order: {type: [ spell1, spell2 ]}}
    private static final Map<Order, Map<String, List<Spell>>> BY_ORDER_AND_TYPE = new HashMap<>();

    public static void register(Spell spell) {
        SPELLS.put(spell.getId(), spell);
        BY_ORDER_AND_TYPE
                .computeIfAbsent(spell.getOrder(), k -> new HashMap<>())
                .computeIfAbsent(spell.getType(), k -> new ArrayList<>())
                .add(spell);
    }

    public static List<Spell> getEquippableSpells(ServerPlayer player) {
        List<Spell> equippable = new ArrayList<>();
        AscensionData data = (AscensionData) player;

        addSpellsIfPresent(equippable, data.getPassive(), "passive");
        addSpellsIfPresent(equippable, data.getUtility(), "utility");
        addSpellsIfPresent(equippable, data.getCombat(), "combat");

        return equippable;
    }

    private static void addSpellsIfPresent(List<Spell> targetList, Order order, String type) {
        if (order == null)
            return;
        Map<String, List<Spell>> orderSpells = BY_ORDER_AND_TYPE.get(order);
        if (orderSpells == null)
            return;
        List<Spell> spells = orderSpells.get(type);
        if (spells != null) {
            targetList.addAll(spells);
        }
    }

    /*
     * EARTH
     */

    public static void magmaBubble(ServerPlayer p, int radius, float damagePercent, boolean launch) {
        BlockPos center = p.getOnPos();

        // Allow a 1-block tolerance (e.g., slightly above the ground)
        Block blockBelow1 = p.level().getBlockState(center.below()).getBlock();
        Block blockBelow2 = p.level().getBlockState(center.below(2)).getBlock();

        boolean standingOnSolid = !blockBelow1.defaultBlockState().isAir() || !blockBelow2.defaultBlockState().isAir();

        boolean submergedInLava = p.level().getFluidState(center).is(net.minecraft.tags.FluidTags.LAVA)
                || (blockBelow1 != null
                        && p.level().getFluidState(center.below()).is(net.minecraft.tags.FluidTags.LAVA));

        if (!standingOnSolid && !submergedInLava) {
            p.sendSystemMessage(Component.literal("You need solid ground or lava to unleash Magma Bubble."));
            return;
        }

        ActiveSpell as = SpellCooldownManager.addToActiveSpells(p, SpellCooldownManager.get("magma_bubble"));

        // float heightScale = launch ? 1.0f : 0.6f;
        // MagmaSpike.spawnMagmaSpikes(center, heightScale);

        if (launch) {
            Vec3 velocity = p.getDeltaMovement();
            p.setDeltaMovement(velocity.x, Math.max(velocity.y, 1.2), velocity.z);
            p.hasImpulse = true;
            p.setIgnoreFallDamageFromCurrentImpulse(true);
        }

        AABB searchBox = new AABB(center).inflate(radius);
        for (LivingEntity target : p.level().getEntitiesOfClass(LivingEntity.class, searchBox)) {
            if (target != p) {
                Utils.spellDmg(target, p, damagePercent);
            }
        }

        Level level = p.level();

        level.playSound(null, center, SoundEvents.CROSSBOW_LOADING_MIDDLE.value(), SoundSource.PLAYERS, 1.0f,
                0.5f);
        level.playSound(null, center, SoundEvents.AMBIENT_UNDERWATER_LOOP_ADDITIONS_RARE,
                SoundSource.PLAYERS,
                1.0f, 0.625f);
        level.playSound(null, center, SoundEvents.MACE_SMASH_GROUND, SoundSource.PLAYERS, 1.0f,
                launch ? 0.5f : 0.65f);

        as.setInUse(false);
    }

    // Actual supermine handled in EARTH event listeners
    public static void toggleSupermine(ServerPlayer player) {
        ActiveSpell as = SpellCooldownManager.addToActiveSpells(player, SpellCooldownManager.get("supermine"));
        Earth.toggleSupermine(player);
        as.setInUse(false);
    }

    /*
     * SKY
     */

    // Not really a spell - uses simple cooldown tracking
    public static void double_jump(ServerPlayer player, int jumpHeight, boolean slam) {
        UUID playerId = player.getUUID();
        long currentTime = System.currentTimeMillis();
        Long lastUse = Sky.getDoubleJumpCooldowns().get(playerId);
        final long cooldownMs = 8000L;

        if (lastUse != null) {
            long elapsed = currentTime - lastUse;
            if (elapsed < cooldownMs) {
                long remainingMs = cooldownMs - elapsed;
                long remainingSeconds = (long) Math.ceil(remainingMs / 1000.0);
                player.sendSystemMessage(
                        Component.literal("§cDouble Jump is on cooldown! §7(" + remainingSeconds + "s left)"));
                return; // Still on cooldown
            }
        }

        Sky.putDoubleJumpCooldown(playerId, currentTime);

        Vec3 current = player.getDeltaMovement();
        Vec3 forward = player.getLookAngle();
        // Flatten Y for horizontal boost direction
        forward = new Vec3(forward.x, 0, forward.z);

        if (forward.lengthSqr() > 0.2) {
            forward = forward.normalize().scale(0.3);
            current = current.add(forward);
        }

        // Solve initial Y velocity (verticalBoost) for Minecraft-like per-tick physics:
        // y += v; v = (v - 0.08) * 0.98
        DoubleUnaryOperator maxHeightForV0 = (v0) -> {
            double y = 0.0;
            double v = v0;
            double maxY = 0.0;
            for (int i = 0; i < 200; i++) {
                y += v;
                if (y > maxY)
                    maxY = y;
                v = (v - 0.08) * 0.98;
                if (v <= 0.0 && y < maxY)
                    break; // past apex and descending
            }
            return maxY;
        };

        double lo = 0.0;
        double hi = 1.0;
        while (maxHeightForV0.applyAsDouble(hi) < jumpHeight && hi < 5.0) {
            hi *= 1.35;
        }
        for (int i = 0; i < 22; i++) {
            double mid = (lo + hi) * 0.5;
            if (maxHeightForV0.applyAsDouble(mid) >= jumpHeight)
                hi = mid;
            else
                lo = mid;
        }

        double verticalBoost = hi;

        player.setDeltaMovement(current.x, verticalBoost, current.z);
        player.hasImpulse = true;
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
        player.setIgnoreFallDamageFromCurrentImpulse(true);

        if (slam) {
            Ascension.scheduler.schedule(new RepeatedTask(1, 60, (task) -> {
                BlockPos center = player.getOnPos();
                Block blockBelow1 = player.level().getBlockState(center.below()).getBlock();
                boolean onGround = !blockBelow1.defaultBlockState().isAir();
                Level level = player.level();

                if (onGround) {
                    player.setIgnoreFallDamageFromCurrentImpulse(true);
                    level.addParticle(ParticleTypes.POOF, center.getX(), center.getY(), center.getZ(), 0.0, 0.0, 0.0);

                    AABB searchBox = new AABB(center).inflate(2);
                    for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, searchBox)) {
                        if (target != player) {
                            Utils.spellDmg(target, player, 20);
                        }
                    }
                    level.playSound(null, center, SoundEvents.MACE_SMASH_GROUND, SoundSource.PLAYERS, 1.0f, 1.0f);
                    task.cancel();
                }
            }));
        }
    }

    public static void dash(ServerPlayer player, boolean dashDamage, int distance) {
        Level level = player.level();
        ActiveSpell as = SpellCooldownManager.addToActiveSpells(player,
                SpellCooldownManager.get("dash"));

        // Current motion and look direction (flattened)
        Vec3 current = player.getDeltaMovement();
        Vec3 forward = player.getLookAngle();
        forward = new Vec3(forward.x, 0.0, forward.z);

        // Compute desired horizontal speed based on distance and drag (approximation)
        final double drag = 0.91;
        final int dashTicks = 12;
        double desiredHorizontalSpeed = (distance * (1.0 - drag)) / (1.0 - Math.pow(drag, dashTicks));

        Vec3 horiz = new Vec3(current.x, 0.0, current.z);

        if (forward.lengthSqr() > 0.2) {
            Vec3 boost = forward.normalize().scale(desiredHorizontalSpeed);
            horiz = horiz.add(boost);
        } else {
            // If no meaningful forward input, apply boost in player's facing direction
            // anyway
            Vec3 lookFlat = player.getLookAngle();
            lookFlat = new Vec3(lookFlat.x, 0.0, lookFlat.z);
            if (lookFlat.lengthSqr() > 1.0e-6) {
                Vec3 boost = lookFlat.normalize().scale(desiredHorizontalSpeed);
                horiz = horiz.add(boost);
            }
        }

        // Preserve vertical velocity
        Vec3 newVel = new Vec3(horiz.x, current.y, horiz.z);
        player.setDeltaMovement(newVel);
        player.hasImpulse = true;
        player.setIgnoreFallDamageFromCurrentImpulse(true);
        player.connection.send(new ClientboundSetEntityMotionPacket(player));

        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WITHER_SHOOT,
                SoundSource.PLAYERS, 0.5f,
                dashDamage ? 1.0f : 1.35f);

        if (dashDamage) {
            Ascension.scheduler.schedule(new RepeatedTask(1, 60, (task) -> {
                if (player.onGround()) {
                    as.setInUse(false);
                    task.cancel();
                    return;
                }

                AABB box = player.getBoundingBox().inflate(0.5);
                level.getEntitiesOfClass(LivingEntity.class, box)
                        .stream()
                        .filter(entity -> !entity.equals(player))
                        .forEach(entity -> {
                            Utils.spellDmg(entity, player, 20);
                        });
            }));
        } else {
            as.setInUse(false);
        }
    }

    public static void starStrike(ServerPlayer player, boolean augmented) {
        ActiveSpell as = SpellCooldownManager.addToActiveSpells(player,
                SpellCooldownManager.get("star_strike"));

        Level level = player.level();
        double range = augmented ? 60.0 : 30.0;

        // Perform raycast to find target or fallback to max range
        Vec3 eyePos = player.getEyePosition();
        Vec3 viewVec = player.getLookAngle();
        Vec3 endVec = eyePos.add(viewVec.scale(range));
        net.minecraft.world.phys.BlockHitResult hitResult = level.clip(new net.minecraft.world.level.ClipContext(
                eyePos, endVec,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                player));

        Vec3 strikePoint = hitResult.getType() == net.minecraft.world.phys.HitResult.Type.MISS
                ? endVec
                : hitResult.getLocation();

        // Adjust particle center to be slightly centered in the block visual area
        Vec3 particleCenter = strikePoint.add(0.0, -0.5, 0.0);

        final int growTicks = 10;
        final int holdTicks = 10;
        final int totalDuration = 40;

        // Pre-impact rumble sound
        level.playSound(null, strikePoint.x, strikePoint.y, strikePoint.z,
                SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 0.9f, 0.55f);

        // (Optional) Call to external GammaRay visual helper would go here
        // GammaRay.strike(strikePoint, 1.5, growTicks, holdTicks, 12, augmented ?
        // 0x000000 : 0xFFFFFF);

        // Schedule the tick-based effect
        // Using a 1-element array to maintain the tick counter state within the lambda
        final int[] tickCounter = { 0 };

        Ascension.scheduler.schedule(new RepeatedTask(1, totalDuration, (task) -> {
            int ticks = tickCounter[0]++;

            if (ticks >= totalDuration) {
                as.setInUse(false);
                task.cancel();
                return;
            }

            // Visual particles for the 2x2 area
            if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                for (int dx = 0; dx <= 1; dx++) {
                    for (int dz = 0; dz <= 1; dz++) {
                        double px = particleCenter.x + dx - 0.5;
                        double py = particleCenter.y + 0.5;
                        double pz = particleCenter.z + dz - 0.5;
                        serverLevel.sendParticles(ParticleTypes.END_ROD, px, py, pz, 10, 0.2, 0.5, 0.2, 0.01);
                        serverLevel.sendParticles(ParticleTypes.EXPLOSION, px, py, pz, 1, 0.0, 0.0, 0.0, 0.0);
                    }
                }
            }

            // Only deal damage after the growth phase
            if (ticks < growTicks + holdTicks) {
                return;
            }

            AABB box = new AABB(player.getOnPos()).inflate(2.0, 3.0, 2.0);
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box)) {
                if (target == player)
                    continue;

                Utils.spellDmg(target, player, 25.0f);

                // Launch upwards
                Vec3 currentVel = target.getDeltaMovement();
                target.setDeltaMovement(currentVel.x, 0.7, currentVel.z);
                target.hasImpulse = true;

                // Apply Darkness effect
                target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.DARKNESS, 40, 0, true, true, true));
            }
        }));
    }
}
