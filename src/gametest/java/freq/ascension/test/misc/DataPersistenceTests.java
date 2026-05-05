package freq.ascension.test.misc;

import freq.ascension.items.InfluenceItem;
import freq.ascension.managers.AscensionData;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

/**
 * Tests player data persistence: influence item recognition, passive order
 * persistence across reloads, previous passive tracking, and unlocked order tracking.
 */
public class DataPersistenceTests {

    /**
     * Creates the production influence item and asserts the crafting/result code can
     * still recognize it as a valid influence restoration item.
     */
    @GameTest
    public void influenceItemRecognizedByCraftingSystem(GameTestHelper helper) {
        if (InfluenceItem.isInfluenceItem(InfluenceItem.createItem())) {
            helper.succeed();
        } else {
            helper.fail("Expected the production Influence item to be recognized by the crafting system.");
        }
    }

    /**
     * Uses /set passive ocean; saves player data; reloads player data;
     * asserts ocean still equipped as passive.
     */
    @GameTest
    public void oceanPassivePersistedAfterPlayerDataReload(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        AscensionData data = (AscensionData) player;
        data.setPassive("ocean");

        CompoundTag saved = savePlayer(helper, player);
        data.setPassive(null);
        player.load(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), saved));

        if (data.getPassive() != null && "ocean".equalsIgnoreCase(data.getPassive().getOrderName())) {
            helper.succeed();
        } else {
            helper.fail("Expected ocean passive to survive player data serialization reload");
        }
    }

    /**
     * Uses /set passive ocean; then /set passive earth;
     * asserts AscensionData.previousPassive returns "ocean".
     */
    @GameTest
    public void previousPassiveTrackedWhenPassiveChanges(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        AscensionData data = (AscensionData) player;
        data.setPassive("ocean");
        data.setPassive("earth");

        if ("ocean".equalsIgnoreCase(data.getPreviousPassive())) {
            helper.succeed();
        } else {
            helper.fail("Expected previous passive to track the replaced passive order; got "
                    + data.getPreviousPassive());
        }
    }

    /**
     * Unlocks ocean order for player; asserts ocean appears in player's unlocked orders list.
     */
    @GameTest
    public void unlockedOrdersTrackedWhenOrderUnlocked(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        AscensionData data = (AscensionData) player;
        data.unlock("ocean", "passive");

        if (data.getUnlocked().containsKey("ocean") && data.getUnlockedOrder("ocean").hasPassive()) {
            helper.succeed();
        } else {
            helper.fail("Expected unlocked orders to record the ocean passive unlock");
        }
    }

    private static CompoundTag savePlayer(GameTestHelper helper, ServerPlayer player) {
        TagValueOutput output = TagValueOutput.createWithContext(
                ProblemReporter.DISCARDING,
                helper.getLevel().registryAccess());
        player.saveWithoutId(output);
        return output.buildResult();
    }
}
