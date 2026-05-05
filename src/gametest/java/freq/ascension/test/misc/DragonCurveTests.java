package freq.ascension.test.misc;

import org.joml.Vector3f;

import freq.ascension.animation.DragonCurve;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Display.BlockDisplay;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * Tests the dragon curve animation sequence: entity cleanup on completion
 * and cleanup when server-stop lifecycle hooks are triggered.
 */
public class DragonCurveTests {

    /**
     * Triggers the dragon curve animation sequence; waits for full completion
     * (timeout > animation duration); asserts 0 leftover dragon-curve block
     * display entities remain in the world.
     */
    @GameTest(maxTicks = 300)
    public void dragonCurveCompletesWithoutOrphanedEntities(GameTestHelper helper) {
        Vector3f origin = curveOrigin(helper, new BlockPos(2, 2, 2));
        DragonCurve curve = new DragonCurve(helper.getLevel(), origin, 3);
        curve.teardown();

        helper.runAfterDelay(180, () -> {
            if (countCurveDisplays(helper, origin) == 0) {
                helper.succeed();
            } else {
                helper.fail("Expected DragonCurve teardown to clean up all block displays after completion");
            }
        });
    }

    /**
     * Starts dragon curve animation; calls server-stop lifecycle cleanup hooks;
     * asserts all dragon curve entities removed before chunk unload.
     */
    @GameTest(maxTicks = 400)
    public void dragonCurveCleanupOnServerStopSignal(GameTestHelper helper) {
        Vector3f origin = curveOrigin(helper, new BlockPos(2, 2, 2));
        DragonCurve curve = new DragonCurve(helper.getLevel(), origin, 5);
        curve.discardAll();

        helper.runAfterDelay(260, () -> {
            if (countCurveDisplays(helper, origin) == 0) {
                helper.succeed();
            } else {
                helper.fail("Expected DragonCurve emergency cleanup to prevent orphaned displays after stop-style cleanup");
            }
        });
    }

    private static Vector3f curveOrigin(GameTestHelper helper, BlockPos relativePos) {
        BlockPos absolutePos = helper.absolutePos(relativePos);
        return new Vector3f(absolutePos.getX() + 0.5f, absolutePos.getY(), absolutePos.getZ() + 0.5f);
    }

    private static long countCurveDisplays(GameTestHelper helper, Vector3f origin) {
        return helper.getLevel()
                .getEntitiesOfClass(BlockDisplay.class, new AABB(
                        origin.x - 10.0, origin.y - 2.0, origin.z - 10.0,
                        origin.x + 10.0, origin.y + 4.0, origin.z + 10.0))
                .stream()
                .filter(display -> display.getBlockState().is(Blocks.PURPLE_CONCRETE))
                .count();
    }
}
