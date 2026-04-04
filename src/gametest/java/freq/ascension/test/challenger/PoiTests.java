package freq.ascension.test.challenger;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Tests for the POI entity: display name, interaction event firing,
 * and persistence across chunk unload/reload.
 */
public class PoiTests {

    /**
     * Spawns ocean POI entity; asserts entity custom name is "Ocean"
     * (not "ascension_poi_ocean").
     */
    @GameTest
    public void poiEntityDisplaysOrderNameNotInternalId(GameTestHelper helper) {
        helper.fail("NOT IMPLEMENTED");
    }

    /**
     * Spawns POI; mock player right-clicks it;
     * asserts POI interaction handler is invoked (trial attempt registered).
     */
    @GameTest
    public void poiRightClickFiresInteractionEvent(GameTestHelper helper) {
        helper.fail("NOT IMPLEMENTED");
    }

    /**
     * Spawns POI; forces chunk unload then reload;
     * asserts POI entity still present in world.
     */
    @GameTest
    public void poiPersistsAfterChunkUnloadAndReload(GameTestHelper helper) {
        helper.fail("NOT IMPLEMENTED");
    }
}
