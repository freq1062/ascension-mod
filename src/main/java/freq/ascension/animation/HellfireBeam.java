package freq.ascension.animation;

import java.util.List;
import java.util.UUID;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import freq.ascension.Ascension;
import freq.ascension.Utils;
import freq.ascension.api.ContinuousTask;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;

/**
 * Fires a horizontal beam of hellfire from the casting player's eye position.
 *
 * <p>The beam travels along the player's look direction, stopping at the first solid block it
 * encounters (max 60 blocks). All {@link LivingEntity} instances within 1 block of the ray
 * receive scaled spell damage; entities closer than 10 blocks receive the full 40 % max-HP hit,
 * while the damage falls off linearly to 0 at 60 blocks.
 */
public class HellfireBeam {

    private HellfireBeam() {}

    /**
     * Fires the Hellfire Beam ability.
     *
     * @param player    the casting player (owner)
     * @param direction normalised look vector
     * @param maxRange  maximum beam range in blocks
     */
    public static void fire(ServerPlayer player, Vec3 direction, double maxRange) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 start = player.getEyePosition();
        Vec3 dir = direction.normalize();

        // Raycast — stop at the first solid block collision.
        Vec3 end = start.add(dir.scale(maxRange));
        BlockHitResult hit = level.clip(
                new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double actualRange = (hit.getType() != HitResult.Type.MISS)
                ? hit.getLocation().distanceTo(start)
                : maxRange;

        // Sounds — all fired simultaneously at the player's position.
        double px = player.getX();
        double py = player.getY();
        double pz = player.getZ();
        level.playSound(null, px, py, pz, SoundEvents.FIRECHARGE_USE,      SoundSource.PLAYERS, 1.2f, 0.70f);
        level.playSound(null, px, py, pz, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 1.0f, 0.50f);
        level.playSound(null, px, py, pz, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.0f, 0.40f);
        level.playSound(null, px, py, pz, SoundEvents.TOTEM_USE,            SoundSource.PLAYERS, 1.0f, 0.50f);

        // VFX — spawnBeamVFX returns data about the glow entity for the rotation task
        GlowEntityData glowData = spawnBeamVFX(level, start, dir, (float) actualRange);
        spawnBeamParticles(level, start, dir, actualRange);
        // Flame explosion at origin
        level.sendParticles(ParticleTypes.FLAME, start.x, start.y, start.z, 15, 0.2, 0.2, 0.2, 0.2);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, start.x, start.y, start.z, 5, 0.1, 0.1, 0.1, 0.05);

        // Rotate + animate the outer glass entity (manages scale AND rotation, replaces VFXBuilder for glow)
        if (glowData != null) {
            final float beamLength = (float) actualRange;
            final float G = glowData.g;
            final Vector3f glowCenter = glowData.center;
            final Quaternionf baseRot = glowData.baseRot;
            float[] glassAngle = {0f};
            int[] glassTick = {0};
            Ascension.scheduler.schedule(new ContinuousTask(1, () -> {
                glassTick[0]++;
                glassAngle[0] += 3f;
                var glassEntity = level.getEntity(glowData.id);
                if (!(glassEntity instanceof net.minecraft.world.entity.Display.BlockDisplay gd) || gd.isRemoved()) return;

                // Compute scale mimicking VFXBuilder keyframes: grow 3 ticks, shrink 10 ticks
                float scaleY;
                int t = glassTick[0];
                if (t <= 3) {
                    scaleY = (t / 3f) * beamLength;
                } else {
                    scaleY = ((13 - t) / 10f) * beamLength;
                }
                scaleY = Math.max(0f, scaleY);

                org.joml.Quaternionf rotY = new org.joml.Quaternionf(baseRot).rotateY((float) Math.toRadians(glassAngle[0]));
                gd.setTransformation(new com.mojang.math.Transformation(
                    glowCenter, rotY, new Vector3f(G, scaleY, G), new Quaternionf()));
                gd.setTransformationInterpolationDelay(0);
                gd.setTransformationInterpolationDuration(1);

                if (t >= 13) gd.discard();
            }) {
                @Override
                public boolean isFinished() {
                    return glassTick[0] >= 13;
                }
            });
        }

        // Damage
        applyBeamDamage(player, level, start, dir, actualRange);
    }

    /**
     * Returns the spell damage (in HP) that should be dealt to a target at the given distance
     * from the caster. Uses {@code maxHp} to scale the percentage damage.
     *
     * <ul>
     *   <li>dist ≤ 10 → 40 % of max HP</li>
     *   <li>10 < dist < 60 → linear falloff: {@code 40% × (60 − dist) / 50}</li>
     *   <li>dist ≥ 60 → 0</li>
     * </ul>
     */
    public static double calculateDamage(double dist, double maxHp) {
        if (dist <= 10.0) return maxHp * 0.40;
        if (dist >= 60.0) return 0.0;
        return maxHp * 0.40 * (60.0 - dist) / 50.0;
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    /** Holds data about the glow (outer glass) display entity for use in the rotation task. */
    private record GlowEntityData(UUID id, Vector3f center, Quaternionf baseRot, float g) {}

    private static GlowEntityData spawnBeamVFX(ServerLevel level, Vec3 start, Vec3 dir, float length) {
        Vector3f startVec = new Vector3f((float) start.x, (float) start.y, (float) start.z);
        Vector3f dirVec   = new Vector3f((float) dir.x, (float) dir.y, (float) dir.z);
        Quaternionf rotation = GeometrySource.faceVector(dirVec);

        // Core — SHROOMLIGHT, 0.5 × 0.5 wide. Grows in 3 ticks, shrinks in 10.
        float T = 0.5f;
        Vector3f coreCenter = rotation.transform(new Vector3f(-T * 0.5f, 0f, -T * 0.5f), new Vector3f());
        new VFXBuilder(level, startVec, Blocks.SHROOMLIGHT.defaultBlockState(),
                VFXBuilder.instant(coreCenter, rotation, new Vector3f(T, 0f, T)))
                .addKeyframeS(coreCenter, null, new Vector3f(T, length, T), 3)
                .addKeyframeS(coreCenter, null, new Vector3f(T, 0f, T), 10, 0);

        // Glow — ORANGE_STAINED_GLASS, 0.8 × 0.8 wide. Created directly so rotation task
        // can manage both scale and rotation without conflicting with VFXBuilder.
        float G = 0.8f;
        Vector3f glowCenter = rotation.transform(new Vector3f(-G * 0.5f, 0f, -G * 0.5f), new Vector3f());
        net.minecraft.world.entity.Display.BlockDisplay glowEntity =
                (net.minecraft.world.entity.Display.BlockDisplay)
                net.minecraft.world.entity.EntityType.BLOCK_DISPLAY.create(
                        level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
        if (glowEntity == null) return null;

        glowEntity.setPos(start.x, start.y, start.z);
        glowEntity.setBlockState(Blocks.ORANGE_STAINED_GLASS.defaultBlockState());
        glowEntity.setGlowingTag(true);
        glowEntity.setBrightnessOverride(new Brightness(15, 15));
        glowEntity.setTransformation(VFXBuilder.instant(glowCenter, rotation, new Vector3f(G, 0f, G)));
        glowEntity.setTransformationInterpolationDuration(1);
        level.addFreshEntity(glowEntity);

        return new GlowEntityData(glowEntity.getUUID(), glowCenter, new Quaternionf(rotation), G);
    }

    private static void spawnBeamParticles(ServerLevel level, Vec3 start, Vec3 dir, double length) {
        Vec3 perp = perpendicularTo(dir);
        int steps = Math.max(1, (int) (length * 2));
        for (int i = 0; i < steps; i++) {
            double t = (double) i / steps * length;
            Vec3 center = start.add(dir.scale(t));
            // Spiral around the beam axis.
            double angle  = t * Math.PI * 3.0;
            Vec3 offset   = rotateAround(perp.scale(0.35), dir, angle);
            Vec3 particle = center.add(offset);
            level.sendParticles(ParticleTypes.FLAME,
                    particle.x, particle.y, particle.z,
                    1, 0.04, 0.04, 0.04, 0.01);
        }
    }

    private static void applyBeamDamage(ServerPlayer player, ServerLevel level,
            Vec3 start, Vec3 dir, double actualRange) {
        Vec3 end  = start.add(dir.scale(actualRange));
        AABB box  = new AABB(start, end).inflate(1.0);

        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, box);
        for (LivingEntity target : candidates) {
            if (target == player) continue;

            // Project target bounding box center onto the ray (eye-level correction).
            Vec3 targetCenter = target.getBoundingBox().getCenter();
            Vec3 toTarget = targetCenter.subtract(start);
            double proj   = toTarget.dot(dir);
            if (proj < 0 || proj > actualRange) continue;

            // Reject if too far from the beam axis (cylinder radius = 1.5 blocks).
            Vec3 closest     = start.add(dir.scale(proj));
            double perpDist  = targetCenter.distanceTo(closest);
            if (perpDist > 1.5) continue;

            double damage = calculateDamage(proj, target.getMaxHealth());
            if (damage <= 0) continue;

            // spellDmg takes a percentage, so convert back.
            float pct = (float) (damage / target.getMaxHealth() * 100.0);
            if (damage > 0) {
                Utils.spellDmg(target, player, pct);
                // Flame explosion VFX at hit entity
                Vec3 hitPos = targetCenter;
                level.sendParticles(ParticleTypes.FLAME, hitPos.x, hitPos.y, hitPos.z, 20, 0.3, 0.3, 0.3, 0.1);
                level.sendParticles(ParticleTypes.EXPLOSION, hitPos.x, hitPos.y, hitPos.z, 3, 0.2, 0.2, 0.2, 0.05);
            }
        }
    }

    /** Returns any vector perpendicular to {@code v}. */
    private static Vec3 perpendicularTo(Vec3 v) {
        Vec3 ref = (Math.abs(v.x) < 0.9) ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        return v.cross(ref).normalize();
    }

    /** Rotates vector {@code v} around unit axis {@code axis} by {@code angle} radians. */
    private static Vec3 rotateAround(Vec3 v, Vec3 axis, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        // Rodrigues' rotation formula
        return v.scale(cos)
                .add(axis.cross(v).scale(sin))
                .add(axis.scale(axis.dot(v) * (1.0 - cos)));
    }
}
