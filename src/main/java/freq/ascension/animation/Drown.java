package freq.ascension.animation;

import java.util.ArrayList;
import java.util.List;

import org.joml.Vector3f;

import freq.ascension.Ascension;
import freq.ascension.api.RepeatedTask;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class Drown {

    private static class Droplet {
        Vector3f position;
        long spawnTick;
        int fallDistance;

        Droplet(Vector3f position, long spawnTick, int fallDistance) {
            this.position = position;
            this.spawnTick = spawnTick;
            this.fallDistance = fallDistance;
        }
    }

    /** Five latitude offsets (as fractions of radius) that trace the sphere surface. */
    private static final float[] RING_FRACS = { -1.0f, -0.5f, 0.0f, 0.5f, 1.0f };

    public static void drownSphere(Player player, float radius, int durationTicks) {
        Vector3f playerPos = player.position().toVector3f();
        // Shift the sphere centre to the player's waist rather than feet.
        Vector3f sphereCenter = new Vector3f(playerPos.x, playerPos.y + 1f, playerPos.z);
        Level level = player.level();

        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        int hoverTicks = 20;
        int fallTicks = 30;
        int spawnInterval = 3;

        // Track all active droplets
        List<Droplet> droplets = new ArrayList<>();

        Ascension.scheduler.schedule(new RepeatedTask(0, durationTicks + hoverTicks + fallTicks, (task) -> {
            long tick = task.getTick();

            // ── Existing droplet system ──────────────────────────────────────────────
            // Spawn new droplets every 3 ticks during the duration period
            if (tick < durationTicks && tick % spawnInterval == 0) {

                for (int i = 0; i < 5; i++) {
                    // Spawn a random position in the sphere
                    Vector3f dropletPos = GeometrySource.sphere(playerPos, radius, false);

                    // Find how far this droplet can fall
                    BlockPos posBlock = new BlockPos((int) dropletPos.x, (int) dropletPos.y, (int) dropletPos.z);
                    int maxFall = 10;
                    int fallDistance = maxFall;

                    for (int d = 1; d <= maxFall; d++) {
                        BlockPos below = posBlock.below(d);
                        if (!level.getBlockState(below).isAir()) {
                            fallDistance = d - 1;
                            break;
                        }
                    }

                    droplets.add(new Droplet(dropletPos, tick, fallDistance));
                }
            }

            // Update and render all active droplets
            droplets.removeIf(droplet -> {
                long age = tick - droplet.spawnTick;

                if (age < hoverTicks) {
                    // Hover phase: stay at spawn position
                    serverLevel.sendParticles(ParticleTypes.DRIPPING_WATER,
                            droplet.position.x, droplet.position.y, droplet.position.z,
                            1, 0, 0, 0, 0.0);
                    return false; // Keep droplet
                } else if (age < hoverTicks + fallTicks) {
                    // Fall phase: drop with acceleration
                    int fallTick = (int) (age - hoverTicks);
                    float fallProgress = fallTick / (float) fallTicks;

                    // Accelerating fall (quadratic)
                    float fallAmount = droplet.fallDistance * fallProgress * fallProgress;
                    float currentY = droplet.position.y - fallAmount;

                    serverLevel.sendParticles(ParticleTypes.DRIPPING_WATER,
                            droplet.position.x, currentY, droplet.position.z,
                            1, 0, 0, 0, 0.0);
                    return false; // Keep droplet
                } else {
                    // Droplet has finished its lifecycle
                    return true; // Remove droplet
                }
            });

            // ── NEW: Circular ring layer — sphere-surface paths ──────────────────────
            // Active only during the main duration; each tick emits one point per ring.
            if (tick < durationTicks) {
                for (float frac : RING_FRACS) {
                    float h = frac * radius;
                    // Ring radius traces the sphere surface: r = sqrt(R² - h²)
                    float ringRadius = (float) Math.sqrt((double) radius * radius - (double) h * h);
                    if (ringRadius < 0.2f)
                        continue; // poles — skip degenerate rings

                    Vector3f ringCenter = new Vector3f(sphereCenter.x, sphereCenter.y + h, sphereCenter.z);
                    // Horizontal circle: normal points straight up
                    Vector3f point = GeometrySource.circle(ringCenter, new Vector3f(0, 1, 0), ringRadius, true);

                    // Randomise speed slightly so the ring feels alive
                    double speed = 0.02 + Math.random() * 0.08;
                    serverLevel.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP,
                            point.x, point.y, point.z,
                            1, 0.0, 0.0, 0.0, speed);
                }
            }

            // Stop task when no more droplets will spawn and all existing droplets are done
            if (tick >= durationTicks + hoverTicks + fallTicks) {
                task.cancel();
            }
        }));
    }
}
