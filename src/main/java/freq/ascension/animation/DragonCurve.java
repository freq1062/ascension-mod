package freq.ascension.animation;

import java.util.ArrayList;
import java.util.List;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.math.Transformation;

import freq.ascension.Ascension;
import freq.ascension.api.DelayedTask;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display.BlockDisplay;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
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
 *   <li>{@code numBranches} starting segments radiate from the centre (0, 0), each
 *       rotated by {@code 360° / numBranches} so the fractal fills the area evenly.</li>
 *   <li>Each iteration <em>appends</em> a 90° clockwise–rotated copy of the entire
 *       current path per branch (pivoting around the last point). Segment length stays
 *       constant at {@value #SEGMENT_LENGTH} blocks throughout — never subdivided.</li>
 *   <li>After N iterations per branch: 2^N segments per branch, all 0.5 blocks long.</li>
 *   <li>Segments whose midpoint falls outside the 7-block radius are clipped.</li>
 * </ol>
 *
 * <p><b>Fix note (Bug 8):</b> Block displays are created directly via
 * {@link EntityType#BLOCK_DISPLAY} rather than through {@link VFXBuilder}. VFXBuilder
 * schedules itself on the task scheduler and auto-discards its entity the moment its
 * keyframe queue empties — for a static display with no keyframes that happens after
 * just two ticks. Direct creation gives persistent entities that live until explicitly
 * discarded by {@link #teardown()} or {@link #discardAll()}.
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
     * Constructs and begins the DragonCurve animation with 4 radial branches.
     *
     * @param level         the server level to spawn displays in
     * @param castPosition  the centre of the Desolation radius (absolute world coords)
     * @param maxIterations how many fractal iterations to draw before holding
     */
    public DragonCurve(ServerLevel level, Vector3f castPosition, int maxIterations) {
        this(level, castPosition, maxIterations, 4);
    }

    /**
     * Constructs and begins the DragonCurve animation with a configurable branch count.
     *
     * @param level         the server level to spawn displays in
     * @param castPosition  the centre of the Desolation radius (absolute world coords)
     * @param maxIterations how many fractal iterations to draw before holding
     * @param numBranches   number of independent curves radiating from centre (≥ 1)
     */
    public DragonCurve(ServerLevel level, Vector3f castPosition, int maxIterations, int numBranches) {
        this.level = level;
        this.origin = castPosition;
        this.maxIterations = maxIterations;
        scheduleIterations(numBranches);
    }

    /**
     * Schedule each iteration to appear {@value #ITERATION_INTERVAL_TICKS} ticks apart.
     *
     * <p>Builds per-branch dragon-curve point lists incrementally. All branches'
     * segments for a given step are merged and scheduled together so every branch
     * unfolds in lock-step. The initial segments (step 0) are shown immediately;
     * each appended rotation group appears with an additional
     * {@value #ITERATION_INTERVAL_TICKS}-tick delay.
     */
    private void scheduleIterations(int numBranches) {
        int totalSteps = maxIterations + 1;

        // Merge all branches' segments by step index
        List<List<float[][]>> mergedSteps = new ArrayList<>();
        for (int i = 0; i < totalSteps; i++) {
            mergedSteps.add(new ArrayList<>());
        }

        for (int b = 0; b < numBranches; b++) {
            // Each branch starts at (0,0) and goes in a different direction
            float angle = (float) (b * 2 * Math.PI / numBranches);
            float ex = (float) Math.cos(angle) * SEGMENT_LENGTH;
            float ez = (float) Math.sin(angle) * SEGMENT_LENGTH;

            List<float[]> points = new ArrayList<>();
            points.add(new float[]{0f, 0f});
            points.add(new float[]{ex, ez});

            List<int[]> iterRanges = new ArrayList<>();
            iterRanges.add(new int[]{0, 1}); // initial segment

            for (int iter = 0; iter < maxIterations; iter++) {
                int pivotIdx = points.size() - 1;
                float[] pivot = points.get(pivotIdx);

                // 90° clockwise rotation of all current points around the pivot
                List<float[]> rotatedCopy = new ArrayList<>();
                for (int i = pivotIdx; i >= 0; i--) {
                    float[] p = points.get(i);
                    float rx = p[0] - pivot[0];
                    float rz = p[1] - pivot[1];
                    // 90° clockwise: (rx, rz) → (rz, -rx)
                    rotatedCopy.add(new float[]{pivot[0] + rz, pivot[1] - rx});
                }
                // index 0 duplicates the pivot — skip it
                int newStart = points.size() - 1;
                for (int i = 1; i < rotatedCopy.size(); i++) {
                    points.add(rotatedCopy.get(i));
                }
                iterRanges.add(new int[]{newStart, points.size() - 1});
            }

            // Collect segment pairs per step for this branch
            final List<float[]> finalPoints = points;
            for (int step = 0; step < iterRanges.size(); step++) {
                int[] range = iterRanges.get(step);
                List<float[][]> stepSegs = mergedSteps.get(step);
                for (int i = range[0]; i < range[1]; i++) {
                    stepSegs.add(new float[][]{finalPoints.get(i), finalPoints.get(i + 1)});
                }
            }
        }

        for (int step = 0; step < totalSteps; step++) {
            final List<float[][]> segments = mergedSteps.get(step);
            int delayTicks = step * ITERATION_INTERVAL_TICKS;

            Ascension.scheduler.schedule(new DelayedTask(delayTicks, () -> {
                List<BlockDisplay> stepDisplays = new ArrayList<>();

                for (float[][] seg : segments) {
                    float[] a = seg[0];
                    float[] b = seg[1];

                    // Clip segments whose midpoint falls outside the effect radius
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
     * {@value #ITERATION_INTERVAL_TICKS} ticks, with witch particles.
     */
    public void teardown() {
        int numGroups = iterationGroups.size();
        for (int g = numGroups - 1; g >= 0; g--) {
            final int groupIndex = g;
            int delayTicks = (numGroups - 1 - g) * ITERATION_INTERVAL_TICKS;

            Ascension.scheduler.schedule(new DelayedTask(delayTicks, () -> {
                if (groupIndex >= iterationGroups.size()) return;
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
        // Final sweep: discard anything still alive (guards against race with spawn tasks)
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
     * Spawns a thin, persistent {@link BlockDisplay} segment between two 2D (xz)
     * control points. The entity is added directly to the level — bypassing
     * {@link VFXBuilder} — so it persists indefinitely until explicitly discarded.
     */
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

        BlockDisplay bd = EntityType.BLOCK_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
        if (bd == null) return null;

        bd.setPos(wx, wy, wz);
        bd.setBlockState(PURPLE_CONCRETE);
        bd.setTransformation(new Transformation(translation, rotation, scale, new Quaternionf()));
        bd.setBrightnessOverride(Brightness.FULL_BRIGHT);
        level.addFreshEntity(bd);
        return bd;
    }
}
