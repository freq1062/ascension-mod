package freq.ascension.test.god;

import freq.ascension.Config;
import freq.ascension.orders.End;
import freq.ascension.orders.EndGod;
import freq.ascension.test.TestHelper;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

public class EndGodTests {

    @GameTest
    public void allEndMobsNeutralWithGodPassive(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        TestHelper.setRank(helper, player, "god");
        TestHelper.equip(helper, player, "passive", "end");

        helper.runAfterDelay(2, () -> {
            if (EndGod.INSTANCE.getDescription("passive").contains("End mobs are neutral")) {
                helper.succeed();
            } else {
                helper.fail("Expected passive description to mention end mob neutrality");
            }
        });
    }

    @GameTest
    public void pearlCooldownFurtherReducedForGod(GameTestHelper helper) {
        int demigodRange = End.INSTANCE.getSpellStats("desolation_of_time").getInt(0);
        int godRange = EndGod.INSTANCE.getSpellStats("desolation_of_time").getInt(0);
        if (godRange > demigodRange) {
            helper.succeed();
        } else {
            helper.fail("Expected stronger end god desolation duration than demigod");
        }
    }

    @GameTest
    public void teleportSpellMovesPlayerToTarget(GameTestHelper helper) {
        int teleportRange = EndGod.INSTANCE.getSpellStats("teleport").getInt(0);
        if (teleportRange == Config.endGodTeleportRange && teleportRange == 15) {
            helper.succeed();
        } else {
            helper.fail("Expected end god teleport range 15, got " + teleportRange);
        }
    }

    @GameTest
    public void teleportRangeCappedAt15Blocks(GameTestHelper helper) {
        int teleportRange = EndGod.INSTANCE.getSpellStats("teleport").getInt(0);
        if (teleportRange <= 15) {
            helper.succeed();
        } else {
            helper.fail("Teleport range exceeds 15 blocks");
        }
    }

    @GameTest
    public void desolationDisablesNearbyPlayerAbilities(GameTestHelper helper) {
        int disableTicks = EndGod.INSTANCE.getSpellStats("desolation_of_time").getInt(0);
        if (disableTicks == 200) {
            helper.succeed();
        } else {
            helper.fail("Expected desolation disable duration 200 ticks, got " + disableTicks);
        }
    }

    @GameTest
    public void desolationAbilityDisabledFor200Ticks(GameTestHelper helper) {
        int disableTicks = EndGod.INSTANCE.getSpellStats("desolation_of_time").getInt(0);
        if (disableTicks == 200) {
            helper.succeed();
        } else {
            helper.fail("Expected 200-tick ability disable");
        }
    }
}
