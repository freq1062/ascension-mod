package freq.ascension.animation;

import java.util.concurrent.ThreadLocalRandom;

import org.joml.Vector3f;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

public class Thorns {
    public static void spawnThorns(ServerPlayer player, LivingEntity target, int numSpikes, int durationTicks) {
        Level level = player.level();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        // Get target's bounding box for height calculations
        AABB targetBox = target.getBoundingBox();
        float targetMinY = (float) targetBox.minY;
        float targetMaxY = (float) targetBox.maxY;
        float targetHeight = targetMaxY - targetMinY;

        // Pick 5 random heights within the target's hitbox
        float[] targetHeights = new float[5];
        for (int i = 0; i < 5; i++) {
            targetHeights[i] = targetMinY + (targetHeight * random.nextFloat());
        }

        // Spawn numSpikes vines
        for (int i = 0; i < numSpikes; i++) {
            // 1. Pick a random ground position around the target
            Vector3f targetPos = target.position().toVector3f();
            float radius = 3.0f;
            float rx = (random.nextFloat() - 0.5f) * radius * 2;
            float rz = (random.nextFloat() - 0.5f) * radius * 2;
            Vector3f groundPos = new Vector3f(targetPos.x + rx, targetPos.y, targetPos.z + rz);

            // 2. Snap to surface
            MagmaBubble.resolveSurface(level, groundPos, 4);

            // 3. Pick a random target height from our pre-calculated heights
            float destinationHeight = targetHeights[random.nextInt(targetHeights.length)];
            float overshoot = 1.0f + random.nextFloat(); // 1-2 blocks overshoot
            float vineHeight = (destinationHeight - groundPos.y) + overshoot;

            // Ensure minimum height
            if (vineHeight < 1.0f) {
                vineHeight = 1.0f + random.nextFloat() * 2.0f;
            }

            final float finalVineHeight = vineHeight;

            // 4. Calculate vine parameters
            float thickness = 0.25f + random.nextFloat(0.15f);
            int growDelay = random.nextInt(3); // Stagger the growth
            int growDuration = 6 + random.nextInt(4); // 6-10 ticks to grow
            int retractDuration = 8 + random.nextInt(4); // 8-12 ticks to retract

            // 5. Create the vine animation
            // Direction is straight up
            Vector3f upDir = new Vector3f(0, 1, 0);

            new VFXBuilder(level, groundPos, Blocks.OAK_WOOD.defaultBlockState(),
                    VFXBuilder.instant(new Vector3f(0), GeometrySource.faceVector(upDir),
                            new Vector3f(thickness, 0.0f, thickness)))
                    // Grow phase: extend upward
                    .addKeyframeS(null, null, new Vector3f(thickness, finalVineHeight, thickness), growDuration,
                            growDelay)
                    .withAction(() -> {
                        if (level instanceof ServerLevel sl) {
                            // Spawn particles when vine reaches full height
                            sl.sendParticles(
                                    net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
                                    groundPos.x, groundPos.y + finalVineHeight, groundPos.z,
                                    5, 0.2, 0.2, 0.2, 0.01);
                        }
                    })
                    // Hold phase: stay at full height for durationTicks
                    .addKeyframeS(null, null, new Vector3f(thickness, finalVineHeight, thickness), durationTicks, 0)
                    // Retract phase: shrink back down
                    .addKeyframeS(null, null, new Vector3f(thickness, 0.0f, thickness), retractDuration, 0)
                    .withAction(() -> {
                        if (level instanceof ServerLevel sl) {
                            // Spawn particles when vine retracts
                            sl.sendParticles(
                                    net.minecraft.core.particles.ParticleTypes.COMPOSTER,
                                    groundPos.x, groundPos.y + 0.2, groundPos.z,
                                    3, 0.15, 0.05, 0.15, 0.01);
                        }
                    });
        }
    }
}
