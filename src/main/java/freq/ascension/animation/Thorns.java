package freq.ascension.animation;

import java.util.concurrent.ThreadLocalRandom;

import org.joml.Vector3f;

import freq.ascension.Ascension;
import freq.ascension.api.DelayedTask;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

public class Thorns {
    public static void spawnThorns(ServerPlayer player, LivingEntity target, int numSpikes, int durationTicks) {
        Level level = player.level();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        AABB targetBox = target.getBoundingBox();
        float targetCenterX = (float) ((targetBox.minX + targetBox.maxX) / 2);
        float targetCenterZ = (float) ((targetBox.minZ + targetBox.maxZ) / 2);
        float targetMinY = (float) targetBox.minY;

        for (int i = 0; i < numSpikes; i++) {
            // 1. Starting position: within 0.3-0.8 blocks of target center, at ground level
            float angle = random.nextFloat() * (float) (Math.PI * 2);
            float dist = 1.3f + random.nextFloat() * 0.5f;
            float startX = targetCenterX + (float) Math.cos(angle) * dist;
            float startZ = targetCenterZ + (float) Math.sin(angle) * dist;
            Vector3f startPos = new Vector3f(startX, targetMinY, startZ);

            MagmaBubble.resolveSurface(level, startPos, 4);

            // 2. Target point: random height 0.2-2.0 above target.minY at target center
            float targetHeight = targetMinY + 0.2f + random.nextFloat() * 1.8f;
            Vector3f targetPoint = new Vector3f(targetCenterX, targetHeight, targetCenterZ);

            // 3. Direction: from start toward target, normalized
            Vector3f dir = new Vector3f(targetPoint).sub(startPos).normalize();

            float distance = startPos.distance(targetPoint);
            float vineLength = distance + 0.5f + random.nextFloat() * 0.5f;

            float thickness = 0.07f + random.nextFloat() * 0.04f;
            int growDelay = random.nextInt(3);
            int growDuration = 5 + random.nextInt(4);
            int retractDuration = 6 + random.nextInt(4);

            // Main branch (segmented for natural curvature)
            spawnBranch(level, startPos, dir, vineLength, thickness, growDelay, growDuration, durationTicks,
                    retractDuration);

            // 1-2 sub-branches with slight rotation to simulate real branch splits
            int branchCount = 1 + random.nextInt(2);
            for (int b = 0; b < branchCount; b++) {
                Vector3f branchDir = rotateVector(dir,
                        (random.nextFloat() - 0.5f) * 0.4f,
                        (random.nextFloat() - 0.5f) * 0.3f);
                float branchLength = vineLength * (0.5f + random.nextFloat() * 0.3f);
                int branchDelay = growDelay + growDuration / 2;
                spawnBranch(level, startPos, branchDir, branchLength, thickness * 0.7f,
                        branchDelay, growDuration, durationTicks, retractDuration);
            }
        }

        // Vine growth sound + delayed impact
        if (level instanceof ServerLevel sl) {
            sl.playSound(null, targetCenterX, targetMinY, targetCenterZ,
                    SoundEvents.GRASS_BREAK, SoundSource.PLAYERS, 1.0f, 0.7f);
            Ascension.scheduler.schedule(new DelayedTask(3, () ->
                    sl.playSound(null, targetCenterX, targetMinY, targetCenterZ,
                            SoundEvents.VINE_BREAK, SoundSource.PLAYERS, 0.8f, 0.9f)));
            // Leafy particle burst around the target
            sl.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    targetCenterX, targetMinY + 1.0, targetCenterZ, 10, 0.5, 0.5, 0.5, 0.1);
        }
    }

    /**
     * Spawns a single vine branch broken into {@code segments} sub-segments with slight
     * directional deviations between each, giving a naturally curved appearance.
     */
    private static void spawnBranch(Level level, Vector3f startPos, Vector3f dir, float length,
            float thickness, int growDelay, int growDuration, int durationTicks, int retractDuration) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int segments = 3;
        float segmentLength = length / segments;
        Vector3f currentPos = new Vector3f(startPos);
        Vector3f currentDir = new Vector3f(dir);

        for (int s = 0; s < segments; s++) {
            int segDelay = growDelay + (int) (s * (growDuration / (float) segments));
            int segGrow = Math.max(2, growDuration / segments);
            float segThickness = thickness * (1f - s * 0.15f);

            new VFXBuilder(level, currentPos, Blocks.OAK_WOOD.defaultBlockState(),
                    VFXBuilder.instant(new Vector3f(0), GeometrySource.faceVector(currentDir),
                            new Vector3f(segThickness, 0.0f, segThickness)))
                    .addKeyframeS(null, null,
                            new Vector3f(segThickness, segmentLength, segThickness),
                            segGrow, segDelay)
                    .addKeyframeS(null, null,
                            new Vector3f(segThickness, 0.0f, segThickness),
                            retractDuration, durationTicks);

            // Advance position along current direction for the next segment's start
            currentPos = new Vector3f(
                    currentPos.x + currentDir.x * segmentLength,
                    currentPos.y + currentDir.y * segmentLength,
                    currentPos.z + currentDir.z * segmentLength);

            // Bend direction slightly so consecutive segments curve naturally
            currentDir = rotateVector(currentDir,
                    (rng.nextFloat() - 0.5f) * 0.25f,
                    (rng.nextFloat() - 0.5f) * 0.2f);
        }
    }

    private static Vector3f rotateVector(Vector3f v, float yaw, float pitch) {
        float cosY = (float) Math.cos(yaw), sinY = (float) Math.sin(yaw);
        float rx = v.x * cosY - v.z * sinY;
        float rz = v.x * sinY + v.z * cosY;
        float cosP = (float) Math.cos(pitch), sinP = (float) Math.sin(pitch);
        float ry = v.y * cosP - rz * sinP;
        float finalZ = v.y * sinP + rz * cosP;
        return new Vector3f(rx, ry, finalZ).normalize();
    }
}
