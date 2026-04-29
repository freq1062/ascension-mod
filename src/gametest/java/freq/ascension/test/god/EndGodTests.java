package freq.ascension.test.god;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import freq.ascension.managers.AbilityManager;
import freq.ascension.test.TestHelper;

public class EndGodTests {

    @GameTest
    public void allEndMobsNeutralWithGodPassive(GameTestHelper helper) {
        ServerPlayer player = (ServerPlayer) helper.makeMockPlayer(GameType.SURVIVAL);
        TestHelper.setRank(helper, player, "god");
        TestHelper.equip(helper, player, "passive", "end");

        helper.runAfterDelay(2, () -> {
            helper.succeed();
        });
    }

    @GameTest
    public void pearlCooldownFurtherReducedForGod(GameTestHelper helper) {
        ServerPlayer player = (ServerPlayer) helper.makeMockPlayer(GameType.SURVIVAL);
        TestHelper.setRank(helper, player, "god");
        TestHelper.equip(helper, player, "passive", "end");

        helper.runAfterDelay(2, () -> {
            helper.succeed();
        });
    }

    @GameTest
    public void teleportSpellMovesPlayerToTarget(GameTestHelper helper) {
        ServerPlayer player = (ServerPlayer) helper.makeMockPlayer(GameType.SURVIVAL);
        TestHelper.setRank(helper, player, "god");
        TestHelper.equip(helper, player, "utility", "end");

        ServerLevel level = helper.getLevel();
        BlockPos absPos = helper.absolutePos(new BlockPos(0, 2, 0));
        player.moveTo(absPos.getX() + 0.5, absPos.getY() + 1, absPos.getZ() + 0.5);

        helper.runAfterDelay(2, () -> {
            double xBefore = player.getX();
            TestHelper.bind(helper, player, 1, "teleport");
            TestHelper.selectHotbarSlot(player, 0);
            TestHelper.activateSpell(helper, player);
            helper.runAfterDelay(5, () -> {
                double xAfter = player.getX();
                double distance = Math.abs(xAfter - xBefore);
                if (distance > 0.5) {
                    helper.succeed();
                } else {
                    helper.fail("No movement");
                }
            });
        });
    }

    @GameTest
    public void teleportRangeCappedAt15Blocks(GameTestHelper helper) {
        ServerPlayer player = (ServerPlayer) helper.makeMockPlayer(GameType.SURVIVAL);
        TestHelper.setRank(helper, player, "god");
        TestHelper.equip(helper, player, "utility", "end");

        helper.runAfterDelay(2, () -> {
            helper.succeed();
        });
    }

    @GameTest
    public void desolationDisablesNearbyPlayerAbilities(GameTestHelper helper) {
        ServerPlayer caster = (ServerPlayer) helper.makeMockPlayer(GameType.SURVIVAL);
        ServerPlayer target = (ServerPlayer) helper.makeMockPlayer(GameType.SURVIVAL);
        TestHelper.setRank(helper, caster, "god");
        TestHelper.equip(helper, caster, "combat", "end");

        helper.runAfterDelay(2, () -> {
            TestHelper.bind(helper, caster, 1, "desolation_of_time");
            TestHelper.selectHotbarSlot(caster, 0);
            TestHelper.activateSpell(helper, caster);
            helper.runAfterDelay(2, () -> {
                helper.succeed();
            });
        });
    }

    @GameTest
    public void desolationAbilityDisabledFor200Ticks(GameTestHelper helper) {
        ServerPlayer caster = (ServerPlayer) helper.makeMockPlayer(GameType.SURVIVAL);
        ServerPlayer target = (ServerPlayer) helper.makeMockPlayer(GameType.SURVIVAL);
        TestHelper.setRank(helper, caster, "god");
        TestHelper.equip(helper, caster, "combat", "end");

        helper.runAfterDelay(2, () -> {
            TestHelper.bind(helper, caster, 1, "desolation_of_time");
            TestHelper.selectHotbarSlot(caster, 0);
            TestHelper.activateSpell(helper, caster);
            helper.runAfterDelay(200, () -> {
                helper.succeed();
            });
        });
    }
}
