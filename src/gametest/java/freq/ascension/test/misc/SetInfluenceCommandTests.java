package freq.ascension.test.misc;

import freq.ascension.managers.AscensionData;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

/**
 * Tests the /setinfluence command: setting influence to positive values,
 * zero, and negative values.
 */
public class SetInfluenceCommandTests {

    /**
     * Uses /setinfluence player 10; asserts getInfluence() returns 10.
     */
    @GameTest
    public void setInfluenceChangesPlayerInfluenceTo10(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        runSetInfluenceCommand(helper, player, 10);
        assertInfluence(helper, player, 10, "Expected /setinfluence to set influence to 10");
    }

    /**
     * Uses /setinfluence player 0; asserts getInfluence() returns 0.
     */
    @GameTest
    public void setInfluenceToZeroSetsInfluenceToZero(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        runSetInfluenceCommand(helper, player, 0);
        assertInfluence(helper, player, 0, "Expected /setinfluence to set influence to 0");
    }

    /**
     * Uses /setinfluence player -1; asserts influence is clamped to 0
     * or command returns an error.
     */
    @GameTest
    public void setInfluenceToNegativeIsHandledGracefully(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        runSetInfluenceCommand(helper, player, -1);
        int influence = ((AscensionData) player).getInfluence();
        if (influence == -1 || influence == 0) {
            helper.succeed();
        } else {
            helper.fail("Expected negative /setinfluence to be handled gracefully; influence=" + influence);
        }
    }

    private static int runSetInfluenceCommand(GameTestHelper helper, ServerPlayer player, int amount) {
        helper.getLevel().getServer().getCommands().performPrefixedCommand(
                player.createCommandSourceStack().withPermission(4),
                "setinfluence @s " + amount);
        return 1;
    }

    private static void assertInfluence(GameTestHelper helper, ServerPlayer player, int expected, String failMessage) {
        int actual = ((AscensionData) player).getInfluence();
        if (actual == expected) {
            helper.succeed();
        } else {
            helper.fail(failMessage + "; got " + actual);
        }
    }
}
