package freq.ascension.animation.dash;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Random;

public final class DashCone {

    private DashCone() {
    }

    public static void spawnDoubleJumpBurst(Player player, boolean god) {
        emitDashCone(player, player.getDeltaMovement(), god ? 20 : 12, god ? 1.4 : 1.0);
        Level level = player.level();
        if (level == null) {
            return;
        }
        
        float pitch = god ? 1.0f : 1.35f;
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WITHER_SHOOT, SoundSource.PLAYERS, 0.5f, pitch);
    }

    public static void emitDashCone(Player player, Vec3 sourceVelocity, int particleCount, double size) {
        if (sourceVelocity == null) {
            sourceVelocity = Vec3.ZERO;
        }
        Vec3 coneDirection = resolveConeDirection(player, sourceVelocity);
        Vec3 offsetDir = coneDirection;
        if (offsetDir.lengthSqr() > 0) {
            offsetDir = offsetDir.normalize().scale(0.5);
        }
        Vec3 origin = player.position().add(offsetDir);
        spawnCone(player.level(), origin, coneDirection, particleCount, sourceVelocity, size);
    }

    private static Vec3 resolveConeDirection(Player player, Vec3 velocity) {
        Vec3 base = velocity == null ? Vec3.ZERO : velocity;
        if (base.lengthSqr() < 1.0E-4) {
            base = player.getLookAngle();
        }
        if (base.lengthSqr() < 1.0E-4) {
            base = new Vec3(0, 1, 0);
        }
        return base.normalize().scale(-1);
    }

    public static void spawnCone(Level level, Vec3 origin, Vec3 direction, int particleCount, Vec3 velocity, double size) {
        if (origin == null || level == null)
            return;

        Vec3 normal = direction == null ? new Vec3(0, 1, 0) : direction;
        if (normal.lengthSqr() < 1.0E-4) {
            normal = new Vec3(0, 1, 0);
        }
        normal = normal.normalize();

        double clampedSize = Math.max(0.5, size);
        int layers = Math.max(3, Math.min(14, (int) Math.ceil(clampedSize * 4)));
        int totalShards = Math.max(layers * 4, particleCount <= 0 ? 12 : particleCount);

        Vec3 drift = velocity == null ? Vec3.ZERO : velocity.scale(0.25);
        if (drift.lengthSqr() > 9.0) {
            drift = drift.normalize().scale(3.0);
        }

        Vec3 axisDir = normal.normalize();
        Vec3[] basis = buildBasis(axisDir);
        double spacing = clampedSize / layers;

        Random random = new Random();

        for (int layer = 0; layer < layers; layer++) {
            double progress = (double) layer / Math.max(1, layers - 1);
            double radius = 0.2 + progress * clampedSize * 0.6;
            int shardsThisLayer = Math.max(4, (int) Math.round((double) totalShards / layers + progress * 4));

            Vec3 ringCenter = origin.add(axisDir.scale(layer * spacing));
            
            for (int shard = 0; shard < shardsThisLayer; shard++) {
                double angle = (Math.PI * 2 * shard) / shardsThisLayer;
                double noise = 0.15 * (random.nextDouble() - 0.5);
                
                Vec3 radial = basis[0].scale(Math.cos(angle)).add(basis[1].scale(Math.sin(angle)));
                Vec3 shardOrigin = ringCenter.add(radial.scale(radius + noise));

                double scaleBase = 0.25 + (1.0 - progress) * 0.15;
                Vector3f baseScale = new Vector3f((float) scaleBase, (float) (scaleBase * 0.7), (float) scaleBase);
                
                BlockState material = (shard + layer) % 2 == 0 ? Blocks.WHITE_WOOL.defaultBlockState() : Blocks.WHITE_STAINED_GLASS.defaultBlockState();
                
                double edgeBias = Math.pow(progress, 1.5);
                double radialDrift = 0.03 + edgeBias * 0.12 * random.nextDouble();
                double tangentialDrift = 0.02 * random.nextDouble();
                
                Vec3 wobble = radial.scale(radialDrift).add(basis[1].scale(tangentialDrift));
                Vec3 layerDrift = drift.add(wobble).add(axisDir.scale(0.04 + edgeBias * 0.08));
                
                double randomRotation = random.nextDouble() * Math.PI * 2;
                
                Vec3 totalVelocity = layerDrift.add(axisDir.scale(0.04));
                
                Quaternionf orientation = createOrientation(axisDir);
                Quaternionf randomRotQ = new Quaternionf().rotateY((float) randomRotation);
                Quaternionf finalRotation = randomRotQ.mul(orientation);
                
                int lifetime = 10 + random.nextInt(Math.max(1, 18 - 10));
                
                new SmokeShard(level, shardOrigin, totalVelocity, lifetime, material, baseScale, finalRotation);
            }
        }
    }

    private static Vec3[] buildBasis(Vec3 axis) {
        Vec3 normalized = axis;
        if (normalized.lengthSqr() < 1.0E-4) {
            normalized = new Vec3(0, 1, 0);
        }
        normalized = normalized.normalize();

        // Check if parallel to Y
        Vec3 reference = Math.abs(normalized.y) < 0.95 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 tangent = reference.cross(normalized);
        if (tangent.lengthSqr() < 1.0E-4) {
            tangent = new Vec3(1, 0, 0).cross(normalized);
        }
        tangent = tangent.normalize();
        Vec3 bitangent = normalized.cross(tangent).normalize();
        return new Vec3[] { tangent, bitangent };
    }

    private static Quaternionf createOrientation(Vec3 normal) {
        Vector3f from = new Vector3f(0f, 1f, 0f);
        Vector3f to = new Vector3f((float) normal.x, (float) normal.y, (float) normal.z);
        to.normalize();

        float dot = from.dot(to);
        if (dot > 0.9999f) {
            return new Quaternionf();
        }
        if (dot < -0.9999f) {
            return new Quaternionf().rotateX((float) Math.PI);
        }

        Vector3f axis = from.cross(to, new Vector3f());
        axis.normalize();
        float angle = (float) Math.acos(dot);
        return new Quaternionf().rotateAxis(angle, axis.x, axis.y, axis.z);
    }
}