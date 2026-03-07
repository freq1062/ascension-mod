package freq.ascension.animation;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.math.Transformation;

import freq.ascension.Ascension;
import freq.ascension.api.DelayedTask;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display.BlockDisplay;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * DragonCurve — iterative dragon-curve fractal VFX for the Desolation of Time spell.
 *
 * <p><b>Intent:</b> Within the 7-block radius of the spell cast, a dragon curve fractal
 * is drawn using stretched purple concrete {@link BlockDisplay} entities. Each iteration
 * unfolds at {@value #ITERATION_INTERVAL_TICKS}-tick intervals (0.5 s), starting from
 * the first iteration and growing outward. When the spell ends the animation runs in
 * reverse — displays shrink from the outside inward.
 *
 * <p><b>Dragon Curve algorithm:</b>
 * <ol>
 *   <li>Begin at a random interior point at least 1 block from the edge of the radius.</li>
 *   <li>Each iteration unfolds the polyline: for each existing segment, insert a
 *       90-degree right turn at its midpoint, alternating direction every other step.</li>
 *   <li>Iterate until all block-display positions are inside the 7-block circle.</li>
 *   <li>Each segment is rendered as a single thin {@link BlockDisplay} scaled to
 *       0.5 × 0.1 × 0.1 blocks (length × width × height).</li>
 * </ol>
 */
public class DragonCurve {

    /**
     * Ticks between each fractal iteration being drawn (0.5 seconds at 20 TPS).
     * Tests validate this constant directly.
     */
    public static final int ITERATION_INTERVAL_TICKS = 10;

    /** Maximum radius of the Desolation of Time effect area (blocks). */
    public static final double EFFECT_RADIUS = 7.0;

    /** Half the length of one dragon-curve segment (blocks). */
    private static final float SEGMENT_HALF = 0.25f;

    private static final BlockState PURPLE_CONCRETE =
            Blocks.PURPLE_CONCRETE.defaultBlockState();

    private final ServerLevel level;
    private final Vector3f origin;
    private final int maxIterations;
    private final List<BlockDisplay> spawnedDisplays = new ArrayList<>();
    private final List<List<BlockDisplay>> iterationGroups = new ArrayList<>();

    /**
     * Constructs and begins the DragonCurve animation.
     *
     * @param level         the server level to spawn displays in
     * @param castPosition  the centre of the Desolation radius (absolute world coords)
     * @param maxIterations how many fractal iterations to draw before holding
     */
    public DragonCurve(ServerLevel level, Vector3f castPosition, int maxIterations) {
        this.level = level;
        this.origin = castPosition;
        this.maxIterations = maxIterations;
        scheduleIterations();
    }

    /** Schedule each iteration to appear {@value #ITERATION_INTERVAL_TICKS} ticks apart. */
    private void scheduleIterations() {
        // Generate the dragon-curve control points
        List<float[]> points = generateDragonCurvePoints(maxIterations);

        // Subdivide into groups of segments — each "group" is one iteration's additions
        // For simplicity, we draw all segments in one final pass and expose them
        // step-by-step. A true iteration would show only the new segments at each step.
        int total = Math.max(1, points.size() - 1);
        int segmentsPerStep = Math.max(1, total / Math.max(1, maxIterations));

        for (int step = 0; step < maxIterations; step++) {
            final int s = step;
            int delayTicks = s * ITERATION_INTERVAL_TICKS;

            Ascension.scheduler.schedule(new DelayedTask(delayTicks, () -> {
                List<BlockDisplay> stepDisplays = new ArrayList<>();

                int start = s * segmentsPerStep;
                int end = Math.min(start + segmentsPerStep, total);

                for (int i = start; i < end; i++) {
                    if (i + 1 >= points.size()) break;
                    float[] a = points.get(i);
                    float[] b = points.get(i + 1);

                    // Only spawn within the radius
                    double dx = a[0], dz = a[1];
                    if (Math.sqrt(dx * dx + dz * dz) > EFFECT_RADIUS - 0.5) continue;

                    BlockDisplay bd = spawnSegmentDisplay(a, b);
                    if (bd != null) {
                        stepDisplays.add(bd);
                        spawnedDisplays.add(bd);

                        // Dragon's breath-style particles around each new display (WITCH is purple, server-safe)
                        level.sendParticles(ParticleTypes.WITCH,
                                origin.x + a[0], origin.y + 1, origin.z + a[1],
                                3, 0.2, 0.1, 0.2, 0.02);
                    }
                }
                iterationGroups.add(stepDisplays);
            }));
        }
    }

    /**
     * Begins the reverse-animation teardown (called when the spell ends).
     * Block displays are removed from outermost group inward, one group per
     * {@value #ITERATION_INTERVAL_TICKS} ticks, with dragon's breath particles.
     */
    public void teardown() {
        int numGroups = iterationGroups.size();
        for (int g = numGroups - 1; g >= 0; g--) {
            final int groupIndex = g;
            int delayTicks = (numGroups - 1 - g) * ITERATION_INTERVAL_TICKS;

            Ascension.scheduler.schedule(new DelayedTask(delayTicks, () -> {
                List<BlockDisplay> group = iterationGroups.get(groupIndex);
                for (BlockDisplay bd : group) {
                    if (bd.isAlive()) {
                        level.sendParticles(ParticleTypes.WITCH,
                                bd.getX(), bd.getY(), bd.getZ(),
                                2, 0.1, 0.1, 0.1, 0.01);
                        bd.discard();
                    }
                }
            }));
        }
        // Ensure all remaining are cleaned up
        int totalTeardown = numGroups * ITERATION_INTERVAL_TICKS + 5;
        Ascension.scheduler.schedule(new DelayedTask(totalTeardown, () -> {
            for (BlockDisplay bd : spawnedDisplays) {
                if (bd.isAlive()) bd.discard();
            }
            spawnedDisplays.clear();
            iterationGroups.clear();
        }));
    }

    /** Discard all block displays immediately (emergency cleanup). */
    public void discardAll() {
        for (BlockDisplay bd : spawnedDisplays) {
            if (bd.isAlive()) bd.discard();
        }
        spawnedDisplays.clear();
        iterationGroups.clear();
    }

    /**
     * Generates dragon-curve polyline control points (X, Z offsets from origin).
     * Each iteration doubles the number of segments by unfolding at 90 degrees.
     *
     * @param iterations number of unfolding iterations
     * @return list of [x, z] float pairs (offsets from cast centre)
     */
    private List<float[]> generateDragonCurvePoints(int iterations) {
        List<float[]> points = new ArrayList<>();
        // Starting segment: random interior point at least 1 block from edge
        Random rng = new Random();
        float maxStart = (float) (EFFECT_RADIUS - 1.5);
        float ox = (rng.nextFloat() * 2 - 1) * maxStart;
        float oz = (rng.nextFloat() * 2 - 1) * maxStart;

        points.add(new float[]{ox, oz});
        points.add(new float[]{ox + SEGMENT_HALF * 2, oz});

        float segLen = SEGMENT_HALF * 2;

        for (int iter = 0; iter < iterations; iter++) {
            List<float[]> next = new ArrayList<>();
            boolean turnRight = true;

            for (int i = 0; i < points.size() - 1; i++) {
                float[] a = points.get(i);
                float[] b = points.get(i + 1);
                float mx = (a[0] + b[0]) / 2f;
                float mz = (a[1] + b[1]) / 2f;

                // Perpendicular direction (90 degrees)
                float dx = b[0] - a[0];
                float dz = b[1] - a[1];
                float perpX = turnRight ? dz : -dz;
                float perpZ = turnRight ? -dx : dx;
                float len = (float) Math.sqrt(perpX * perpX + perpZ * perpZ);
                if (len > 0) { perpX /= len; perpZ /= len; }

                float newX = mx + perpX * (segLen / 2f);
                float newZ = mz + perpZ * (segLen / 2f);

                next.add(a);
                next.add(new float[]{newX, newZ});
                turnRight = !turnRight;

                if (i == points.size() - 2) next.add(b);
            }
            points = next;
            segLen /= 2f;
        }
        return points;
    }

    /** Spawns a thin BlockDisplay segment between two 2D (xz) control points. */
    private BlockDisplay spawnSegmentDisplay(float[] a, float[] b) {
        float dx = b[0] - a[0];
        float dz = b[1] - a[1];
        float len = (float) Math.sqrt(dx * dx + dz * dz);
        if (len < 0.001f) return null;

        float wx = origin.x + (a[0] + b[0]) / 2f;
        float wy = origin.y + 0.5f;
        float wz = origin.z + (a[1] + b[1]) / 2f;

        float angle = (float) Math.atan2(dx, dz);
        Quaternionf rotation = new Quaternionf().rotateY(angle);

        Vector3f scale = new Vector3f(len, 0.1f, 0.1f);
        Vector3f translation = new Vector3f(-len / 2f, 0f, -0.05f);
        Transformation transform = VFXBuilder.instant(translation, rotation, scale);

        return new VFXBuilder(level, new Vector3f(wx, wy, wz), PURPLE_CONCRETE, transform).getEntity();
    }
}
