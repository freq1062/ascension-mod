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
 * DragonCurve — iterative dragon-curve fractal VFX for the Desolation of Time
 * spell.
 *
 * <p>
 * <b>Intent:</b> Within the 7-block radius of the spell cast, a dragon curve
 * fractal
 * is drawn using stretched purple concrete {@link BlockDisplay} entities on the
 * ground.
 * Segments appear in an expanding spiral from the center outward based on
 * distance.
 * When the spell ends the animation runs in reverse — displays shrink from the
 * outside inward.
 *
 * <p>
 * <b>Dragon Curve algorithm:</b>
 * <ol>
 * <li>{@code numBranches} starting segments radiate from the centre (0, 0),
 * each
 * rotated by {@code 360° / numBranches} so the fractal fills the area
 * evenly.</li>
 * <li>Each iteration <em>appends</em> a 90° clockwise–rotated copy of the
 * entire
 * current path per branch (pivoting around the last point). Segment length
 * stays
 * constant at {@value #SEGMENT_LENGTH} blocks throughout — never
 * subdivided.</li>
 * <li>After N iterations per branch: 2^N segments per branch, all 0.5 blocks
 * long.</li>
 * <li>Segments whose midpoint falls outside the 7-block radius are
 * clipped.</li>
 * </ol>
 *
 * <p>
 * <b>Fix note (Bug 8):</b> Block displays are created directly via
 * {@link EntityType#BLOCK_DISPLAY} rather than through {@link VFXBuilder}.
 * VFXBuilder
 * schedules itself on the task scheduler and auto-discards its entity the
 * moment its
 * keyframe queue empties — for a static display with no keyframes that happens
 * after
 * just two ticks. Direct creation gives persistent entities that live until
 * explicitly
 * discarded by {@link #teardown()} or {@link #discardAll()}.
 */
public class DragonCurve {

    /**
     * Ticks between each segment being drawn in the spiral (fast animation).
     * Tests validate this constant directly.
     */
    public static final int ITERATION_INTERVAL_TICKS = 1;

    /** Maximum radius of the Desolation of Time effect area (blocks). */
    public static final double EFFECT_RADIUS = 7.0;

    /**
     * Length of each dragon-curve segment in blocks.
     * This value is <em>constant</em> — it never changes between iterations.
     * Tests validate this constant directly.
     */
    public static final float SEGMENT_LENGTH = 1.0f;

    private static final BlockState PURPLE_CONCRETE = Blocks.PURPLE_CONCRETE.defaultBlockState();

    private final ServerLevel level;
    private final Vector3f origin;
    private final int maxIterations;
    private final List<BlockDisplay> spawnedDisplays = new ArrayList<>();
    private final List<List<BlockDisplay>> iterationGroups = new ArrayList<>();
    /** Total ticks needed for all spawn tasks to complete; set at end of scheduleIterations(). */
    private int totalSpawnTicks = 0;

    /**
     * Constructs and begins the DragonCurve animation with 4 radial branches.
     *
     * @param level         the server level to spawn displays in
     * @param castPosition  the centre of the Desolation radius (absolute world
     *                      coords)
     * @param maxIterations how many fractal iterations to draw before holding
     */
    public DragonCurve(ServerLevel level, Vector3f castPosition, int maxIterations) {
        this(level, castPosition, maxIterations, 4);
    }

    /**
     * Constructs and begins the DragonCurve animation with a configurable branch
     * count.
     *
     * @param level         the server level to spawn displays in
     * @param castPosition  the centre of the Desolation radius (absolute world
     *                      coords)
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
     * Schedule segments to appear in an expanding spiral from center, sorted by
     * distance.
     *
     * <p>
     * Builds per-branch dragon-curve point lists incrementally. All segments from
     * all
     * branches and iterations are collected, sorted by distance from origin, and
     * scheduled
     * to appear sequentially with {@value #ITERATION_INTERVAL_TICKS} tick delays
     * between
     * each segment for a fast spiral-out animation.
     */
    private void scheduleIterations(int numBranches) {
        // Container for segment data with distance for sorting
        class SegmentData {
            float[] a, b;
            double distance;

            SegmentData(float[] a, float[] b) {
                this.a = a;
                this.b = b;
                // Distance of midpoint from origin
                double midX = (a[0] + b[0]) / 2.0;
                double midZ = (a[1] + b[1]) / 2.0;
                this.distance = Math.sqrt(midX * midX + midZ * midZ);
            }
        }

        List<SegmentData> allSegments = new ArrayList<>();

        for (int branchIdx = 0; branchIdx < numBranches; branchIdx++) {
            // Each branch starts at (0,0) and goes in a different direction
            float angle = (float) (branchIdx * 2 * Math.PI / numBranches);
            float ex = (float) Math.cos(angle) * SEGMENT_LENGTH;
            float ez = (float) Math.sin(angle) * SEGMENT_LENGTH;

            List<float[]> points = new ArrayList<>();
            points.add(new float[] { 0f, 0f });
            points.add(new float[] { ex, ez });

            List<int[]> iterRanges = new ArrayList<>();
            iterRanges.add(new int[] { 0, 1 }); // initial segment

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
                    rotatedCopy.add(new float[] { pivot[0] + rz, pivot[1] - rx });
                }
                // index 0 duplicates the pivot — skip it
                int newStart = points.size() - 1;
                for (int i = 1; i < rotatedCopy.size(); i++) {
                    points.add(rotatedCopy.get(i));
                }
                iterRanges.add(new int[] { newStart, points.size() - 1 });
            }

            // Collect all segments from this branch
            final List<float[]> finalPoints = points;
            for (int[] range : iterRanges) {
                for (int i = range[0]; i < range[1]; i++) {
                    float[] a = finalPoints.get(i);
                    float[] b = finalPoints.get(i + 1);

                    // Clip segments whose midpoint falls outside the effect radius
                    double midX = (a[0] + b[0]) / 2.0;
                    double midZ = (a[1] + b[1]) / 2.0;
                    if (Math.sqrt(midX * midX + midZ * midZ) <= EFFECT_RADIUS) {
                        allSegments.add(new SegmentData(a, b));
                    }
                }
            }
        }

        // Sort all segments by distance from center (spiral outward)
        allSegments.sort((s1, s2) -> Double.compare(s1.distance, s2.distance));

        // Record total ticks required so teardown() can wait for all spawns to finish.
        this.totalSpawnTicks = allSegments.size() * ITERATION_INTERVAL_TICKS;

        // Schedule each segment with a small delay for fast spiral animation
        for (int i = 0; i < allSegments.size(); i++) {
            final SegmentData seg = allSegments.get(i);
            final int segmentIndex = i;
            int delayTicks = i * ITERATION_INTERVAL_TICKS;

            Ascension.scheduler.schedule(new DelayedTask(delayTicks, () -> {
                BlockDisplay bd = spawnSegmentDisplay(seg.a, seg.b);
                if (bd != null) {
                    spawnedDisplays.add(bd);

                    // Store in reverse order groups for teardown (every 10 segments = one group)
                    int groupIdx = segmentIndex / 10;
                    while (iterationGroups.size() <= groupIdx) {
                        iterationGroups.add(new ArrayList<>());
                    }
                    iterationGroups.get(groupIdx).add(bd);

                    // Purple witch particles to accent each new segment
                    double midX = (seg.a[0] + seg.b[0]) / 2.0;
                    double midZ = (seg.a[1] + seg.b[1]) / 2.0;
                    level.sendParticles(ParticleTypes.WITCH,
                            origin.x + midX, origin.y + 1, origin.z + midZ,
                            2, 0.1, 0.05, 0.1, 0.01);
                }
            }));
        }
    }

    /**
     * Begins the reverse-animation teardown (called when the spell ends).
     * Block displays are removed from outermost group inward, one group per
     * {@value #ITERATION_INTERVAL_TICKS} ticks, with witch particles.
     *
     * <p>Teardown is delayed by {@code totalSpawnTicks} so that removal tasks
     * always fire <em>after</em> all spawn tasks, preventing segments from being
     * discarded before they even appear.
     */
    public void teardown() {
        int numGroups = iterationGroups.size();
        for (int g = numGroups - 1; g >= 0; g--) {
            final int groupIndex = g;
            // Start AFTER all segments have spawned, then stagger removal outward→inward.
            int delayTicks = totalSpawnTicks + (numGroups - 1 - g) * ITERATION_INTERVAL_TICKS * 10;

            Ascension.scheduler.schedule(new DelayedTask(delayTicks, () -> {
                if (groupIndex >= iterationGroups.size())
                    return;
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
        // Final sweep: discard anything still alive (guards against race with spawn tasks).
        int totalTeardown = totalSpawnTicks + numGroups * ITERATION_INTERVAL_TICKS * 10 + 5;
        Ascension.scheduler.schedule(new DelayedTask(totalTeardown, () -> {
            for (BlockDisplay bd : spawnedDisplays) {
                if (bd.isAlive())
                    bd.discard();
            }
            spawnedDisplays.clear();
            iterationGroups.clear();
        }));
    }

    /** Discard all block displays immediately (emergency cleanup). */
    public void discardAll() {
        for (BlockDisplay bd : spawnedDisplays) {
            if (bd.isAlive())
                bd.discard();
        }
        spawnedDisplays.clear();
        iterationGroups.clear();
    }

    /**
     * Spawns a thin, persistent {@link BlockDisplay} segment between two 2D (xz)
     * control points. The entity is added directly to the level — bypassing
     * {@link VFXBuilder} — so it persists indefinitely until explicitly discarded.
     * Uses {@link MagmaBubble#resolveSurface} to place segments on the ground.
     */
    private BlockDisplay spawnSegmentDisplay(float[] a, float[] b) {
        float dx = b[0] - a[0];
        float dz = b[1] - a[1];
        float len = (float) Math.sqrt(dx * dx + dz * dz);
        if (len < 0.001f)
            return null;

        // Calculate world position of segment midpoint
        float midX = (a[0] + b[0]) / 2f;
        float midZ = (a[1] + b[1]) / 2f;
        // Start search from above origin to ensure we find ground below
        Vector3f worldPos = new Vector3f(origin.x + midX, origin.y + 10, origin.z + midZ);

        // Resolve to ground surface (search up to 15 blocks down)
        MagmaBubble.resolveSurface(level, worldPos, 15);

        float angle = (float) Math.atan2(dx, dz);
        Quaternionf rotation = new Quaternionf().rotateY(angle);
        Vector3f scale = new Vector3f(len, 0.1f, 0.1f);
        Vector3f translation = new Vector3f(-len / 2f, 0f, -0.05f);

        BlockDisplay bd = EntityType.BLOCK_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
        if (bd == null)
            return null;

        bd.setPos(worldPos.x, worldPos.y, worldPos.z);
        bd.setBlockState(PURPLE_CONCRETE);
        bd.setTransformation(new Transformation(translation, rotation, scale, new Quaternionf()));
        bd.setBrightnessOverride(Brightness.FULL_BRIGHT);
        level.addFreshEntity(bd);
        return bd;
    }
}
