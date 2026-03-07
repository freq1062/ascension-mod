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
 * <p><b>Dragon Curve algorithm (correct):</b>
 * <ol>
 *   <li>Start with one 0.5-block segment at a random interior point.</li>
 *   <li>Each iteration <em>appends</em> a 90° clockwise–rotated copy of the entire
 *       current path (pivoting around the last point). The segment length stays constant
 *       at {@value #SEGMENT_LENGTH} blocks throughout — never subdivided.</li>
 *   <li>After N iterations: 2^N segments, all 0.5 blocks long.</li>
 *   <li>Segments whose midpoint falls outside the 7-block radius are clipped.</li>
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

    /**
     * Length of each dragon-curve segment in blocks.
     * This value is <em>constant</em> — it never changes between iterations.
     * Tests validate this constant directly.
     */
    public static final float SEGMENT_LENGTH = 0.5f;

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

    /**
     * Schedule each iteration to appear {@value #ITERATION_INTERVAL_TICKS} ticks apart.
     *
     * <p>Builds the full dragon-curve point list incrementally, recording which
     * point-range was added at each step, then schedules display spawns for each group.
     * The initial segment is shown immediately (step 0); each appended rotation group
     * is shown with an additional {@value #ITERATION_INTERVAL_TICKS}-tick delay.
     */
    private void scheduleIterations() {
        // Seed the point list with a single 0.5-block segment at a random interior point
        Random rng = new Random();
        float maxStart = (float) (EFFECT_RADIUS - 1.0);
        float ox = (rng.nextFloat() * 2 - 1) * maxStart;
        float oz = (rng.nextFloat() * 2 - 1) * maxStart;

        List<float[]> points = new ArrayList<>();
        points.add(new float[]{ox, oz});
        points.add(new float[]{ox + SEGMENT_LENGTH, oz});

        // iterRanges[i] = [startPointIndex, endPointIndex] for segments added at step i.
        // A "segment" is the line from points[j] to points[j+1].
        List<int[]> iterRanges = new ArrayList<>();
        iterRanges.add(new int[]{0, 1}); // initial segment (points 0→1)

        for (int iter = 0; iter < maxIterations; iter++) {
            int pivotIdx = points.size() - 1; // index of the last (pivot) point
            float[] pivot = points.get(pivotIdx);

            // Build rotated copy of all current points (90° clockwise around pivot).
            // Iteration is in reverse order; the first entry equals the pivot and is skipped.
            List<float[]> rotatedCopy = new ArrayList<>();
            for (int i = pivotIdx; i >= 0; i--) {
                float[] p = points.get(i);
                float rx = p[0] - pivot[0];
                float rz = p[1] - pivot[1];
                // 90° clockwise: (rx, rz) → (rz, -rx)
                rotatedCopy.add(new float[]{pivot[0] + rz, pivot[1] - rx});
            }
            // Skip index 0 (it duplicates the pivot) and append the rest
            int newStart = points.size() - 1; // the segment starts at the current last point
            for (int i = 1; i < rotatedCopy.size(); i++) {
                points.add(rotatedCopy.get(i));
            }
            iterRanges.add(new int[]{newStart, points.size() - 1});
        }

        final List<float[]> finalPoints = points;

        for (int step = 0; step < iterRanges.size(); step++) {
            final int[] range = iterRanges.get(step);
            int delayTicks = step * ITERATION_INTERVAL_TICKS;

            Ascension.scheduler.schedule(new DelayedTask(delayTicks, () -> {
                List<BlockDisplay> stepDisplays = new ArrayList<>();

                for (int i = range[0]; i < range[1]; i++) {
                    float[] a = finalPoints.get(i);
                    float[] b = finalPoints.get(i + 1);

                    // Clip: skip segments whose midpoint is outside the effect radius
                    double midX = (a[0] + b[0]) / 2.0;
                    double midZ = (a[1] + b[1]) / 2.0;
                    if (Math.sqrt(midX * midX + midZ * midZ) > EFFECT_RADIUS) continue;

                    BlockDisplay bd = spawnSegmentDisplay(a, b);
                    if (bd != null) {
                        stepDisplays.add(bd);
                        spawnedDisplays.add(bd);

                        // Purple witch particles to accent each new segment
                        level.sendParticles(ParticleTypes.WITCH,
                                origin.x + midX, origin.y + 1, origin.z + midZ,
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
