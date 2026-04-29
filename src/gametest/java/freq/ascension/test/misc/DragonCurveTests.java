package freq.ascension.test.misc;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

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
    @GameTest
    public void dragonCurveCompletesWithoutOrphanedEntities(GameTestHelper helper) {
        helper.fail("NOT IMPLEMENTED");
    }

    /**
     * Starts dragon curve animation; calls server-stop lifecycle cleanup hooks;
     * asserts all dragon curve entities removed before chunk unload.
     */
    @GameTest
    public void dragonCurveCleanupOnServerStopSignal(GameTestHelper helper) {
        helper.fail("NOT IMPLEMENTED");
    }
}
