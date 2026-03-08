package freq.ascension.test.misc;

import java.util.List;

import org.joml.Vector3f;

import freq.ascension.animation.DragonCurve;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Tests for Bug 8 — DragonCurve block-display persistence, teardown, centre-spawn,
 * multi-branch coverage, and iteration-interval constant.
 */
public class DragonCurveTests {

    // ─────────────────────────────────────────────────────────────────────────
    // Bug 8 — Constant check
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * {@link DragonCurve#ITERATION_INTERVAL_TICKS} must equal 10 (0.5 s at 20 TPS).
     */
    @GameTest
    public void dragonCurveIterationIntervalIsCorrect(GameTestHelper helper) {
        if (DragonCurve.ITERATION_INTERVAL_TICKS != 10) {
            helper.fail("ITERATION_INTERVAL_TICKS must be 10, was " + DragonCurve.ITERATION_INTERVAL_TICKS);
        }
        helper.succeed();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Bug 8 Part A — Segments must persist
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Block-display segments must persist after DragonCurve spawns them.
     *
     * <p>Previously, DragonCurve used VFXBuilder which auto-discards its entity
     * when the keyframe queue is empty — for a static display with no keyframes
     * that happens after ~2 ticks. The fix bypasses VFXBuilder and creates
     * BlockDisplay entities directly so they persist until explicitly discarded.
     */
    @GameTest(maxTicks = 30)
    public void dragonCurveSegmentsPersistAfterSpawn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 center = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
        Vector3f origin = new Vector3f((float) center.x, (float) center.y, (float) center.z);

        // 0 extra iterations → exactly 1 segment from step 0 (fires on first scheduler tick)
        new DragonCurve(level, origin, 0, 1);

        helper.runAfterDelay(5, () -> {
            AABB box = new AABB(center.x - 2, center.y - 2, center.z - 2,
                    center.x + 2, center.y + 2, center.z + 2);
            List<Display.BlockDisplay> displays = level.getEntitiesOfClass(Display.BlockDisplay.class, box);
            // Clean up so leaked displays don't affect other tests
            displays.forEach(d -> d.discard());

            if (displays.isEmpty()) {
                helper.fail("DragonCurve block-display segments must persist after spawn; "
                        + "found 0 displays — VFXBuilder was likely auto-discarding them");
            }
            helper.succeed();
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Bug 8 Part A — discardAll must remove everything
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * {@link DragonCurve#discardAll()} must immediately remove all block display
     * segments from the level. Calling it after the first scheduled step has fired
     * must leave zero alive displays in the area.
     */
    @GameTest(maxTicks = 30)
    public void dragonCurveTeardownRemovesAllDisplays(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 center = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
        Vector3f origin = new Vector3f((float) center.x, (float) center.y, (float) center.z);

        DragonCurve curve = new DragonCurve(level, origin, 0, 1);

        helper.runAfterDelay(5, () -> {
            // Segments are now in the level; discard them all
            curve.discardAll();

            AABB box = new AABB(center.x - 2, center.y - 2, center.z - 2,
                    center.x + 2, center.y + 2, center.z + 2);
            long alive = level.getEntitiesOfClass(Display.BlockDisplay.class, box)
                    .stream().filter(d -> d.isAlive()).count();

            if (alive > 0) {
                helper.fail("discardAll() must remove all block displays; " + alive + " remain alive");
            }
            helper.succeed();
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Bug 8 Part B — Curve must start from centre
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * DragonCurve must start its segments from the cast origin (0, 0 relative),
     * not a random interior point. The midpoint of the first-step segment must
     * be within {@link DragonCurve#SEGMENT_LENGTH} of the origin.
     */
    @GameTest(maxTicks = 30)
    public void dragonCurveSpawnsFromCenter(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 center = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
        Vector3f origin = new Vector3f((float) center.x, (float) center.y, (float) center.z);

        new DragonCurve(level, origin, 0, 1);

        helper.runAfterDelay(5, () -> {
            // First segment midpoint is SEGMENT_LENGTH/2 from origin in XZ; wy = origin.y + 0.5
            double r = DragonCurve.SEGMENT_LENGTH + 0.1;
            AABB nearBox = new AABB(center.x - r, center.y - 1, center.z - r,
                    center.x + r, center.y + 2, center.z + r);
            List<Display.BlockDisplay> near = level.getEntitiesOfClass(Display.BlockDisplay.class, nearBox);
            near.forEach(d -> d.discard());

            if (near.isEmpty()) {
                helper.fail("DragonCurve must start from the cast origin; no display found within "
                        + r + " blocks — check that starting points are (0, 0)");
            }
            helper.succeed();
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Bug 8 Part B — Multi-branch must produce more coverage
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * A 4-branch DragonCurve must spawn more block-display segments than a
     * 1-branch curve at the same step, verifying radial coverage.
     *
     * <p>With maxIterations=0 each branch contributes exactly 1 segment at step 0,
     * so 4 branches → 4 displays, 1 branch → 1 display.
     */
    @GameTest(maxTicks = 30)
    public void dragonCurveMultipleBranchesExist(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        Vec3 center4 = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
        Vector3f origin4 = new Vector3f((float) center4.x, (float) center4.y, (float) center4.z);

        Vec3 center1 = Vec3.atCenterOf(helper.absolutePos(new BlockPos(20, 2, 2)));
        Vector3f origin1 = new Vector3f((float) center1.x, (float) center1.y, (float) center1.z);

        DragonCurve curve4 = new DragonCurve(level, origin4, 0, 4);
        DragonCurve curve1 = new DragonCurve(level, origin1, 0, 1);

        helper.runAfterDelay(5, () -> {
            AABB box4 = new AABB(center4.x - 3, center4.y - 2, center4.z - 3,
                    center4.x + 3, center4.y + 2, center4.z + 3);
            AABB box1 = new AABB(center1.x - 3, center1.y - 2, center1.z - 3,
                    center1.x + 3, center1.y + 2, center1.z + 3);

            int count4 = level.getEntitiesOfClass(Display.BlockDisplay.class, box4).size();
            int count1 = level.getEntitiesOfClass(Display.BlockDisplay.class, box1).size();

            curve4.discardAll();
            curve1.discardAll();

            if (count4 <= count1) {
                helper.fail("4-branch DragonCurve (" + count4 + " displays) must produce more segments "
                        + "than 1-branch (" + count1 + ") at the same iteration");
            }
            helper.succeed();
        });
    }
}
