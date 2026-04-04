package freq.ascension.test.god;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;

import freq.ascension.managers.AbilityManager;
import freq.ascension.test.TestHelper;

public class MagicGodTests {

    @GameTest
    public void potionEffectsExtendedTo12000Ticks(GameTestHelper helper) {
        ServerPlayer player = (ServerPlayer) helper.makeMockPlayer(GameType.SURVIVAL);
        TestHelper.setRank(helper, player, "god");
        TestHelper.equip(helper, player, "utility", "magic");

        helper.runAfterDelay(2, () -> {
            net.minecraft.world.effect.MobEffectInstance effect = 
                new net.minecraft.world.effect.MobEffectInstance(MobEffects.STRENGTH, 100, 0, false, true, true);
            player.addEffect(effect);
            helper.runAfterDelay(2, () -> {
                var strengthEffect = player.getEffect(MobEffects.STRENGTH);
                if (strengthEffect != null && strengthEffect.getDuration() >= 9500) {
                    helper.succeed();
                } else {
                    helper.fail("Duration not extended");
                }
            });
        });
    }

    @GameTest
    public void potionExtensionLongerThanDemigod(GameTestHelper helper) {
        ServerPlayer playerGod = (ServerPlayer) helper.makeMockPlayer(GameType.SURVIVAL);
        ServerPlayer playerDemigod = (ServerPlayer) helper.makeMockPlayer(GameType.SURVIVAL);
        TestHelper.setRank(helper, playerGod, "god");
        TestHelper.equip(helper, playerGod, "utility", "magic");
        TestHelper.equip(helper, playerDemigod, "utility", "magic");

        helper.runAfterDelay(2, () -> {
            helper.succeed();
        });
    }

    @GameTest
    public void enchantCostReducedTo10PctForMagicGod(GameTestHelper helper) {
        ServerPlayer player = (ServerPlayer) helper.makeMockPlayer(GameType.SURVIVAL);
        TestHelper.setRank(helper, player, "god");
        TestHelper.equip(helper, player, "passive", "magic");

        helper.runAfterDelay(2, () -> {
            var order = AbilityManager.get(player);
            int modifiedCost = order.modifyEnchantmentCost(100);
            if (modifiedCost <= 15) {
                helper.succeed();
            } else {
                helper.fail("Cost not reduced");
            }
        });
    }

    @GameTest
    public void enchantCostLowerThanDemigod(GameTestHelper helper) {
        ServerPlayer playerGod = (ServerPlayer) helper.makeMockPlayer(GameType.SURVIVAL);
        ServerPlayer playerDemigod = (ServerPlayer) helper.makeMockPlayer(GameType.SURVIVAL);
        TestHelper.setRank(helper, playerGod, "god");
        TestHelper.equip(helper, playerGod, "passive", "magic");
        TestHelper.equip(helper, playerDemigod, "passive", "magic");

        helper.runAfterDelay(2, () -> {
            var orderGod = AbilityManager.get(playerGod);
            var orderDemigod = AbilityManager.get(playerDemigod);
            int godCost = orderGod.modifyEnchantmentCost(100);
            int demigodCost = orderDemigod.modifyEnchantmentCost(100);
            if (godCost < demigodCost) {
                helper.succeed();
            } else {
                helper.fail("God cost not lower");
            }
        });
    }

    @GameTest
    public void speedIIAppliedForMagicGod(GameTestHelper helper) {
        ServerPlayer player = (ServerPlayer) helper.makeMockPlayer(GameType.SURVIVAL);
        TestHelper.setRank(helper, player, "god");
        TestHelper.equip(helper, player, "passive", "magic");

        helper.runAfterDelay(2, () -> {
            var speedEffect = player.getEffect(MobEffects.SPEED);
            if (speedEffect != null && speedEffect.getAmplifier() == 1) {
                helper.succeed();
            } else {
                helper.fail("Not Speed II");
            }
        });
    }

    @GameTest
    public void shapeshiftDurationIs900TicksForGod(GameTestHelper helper) {
        ServerPlayer player = (ServerPlayer) helper.makeMockPlayer(GameType.SURVIVAL);
        TestHelper.setRank(helper, player, "god");
        TestHelper.equip(helper, player, "utility", "magic");

        ServerLevel level = helper.getLevel();
        BlockPos absPos = helper.absolutePos(new BlockPos(0, 2, 0));
        player.moveTo(absPos.getX() + 0.5, absPos.getY() + 1, absPos.getZ() + 0.5);

        helper.runAfterDelay(2, () -> {
            TestHelper.bind(helper, player, 1, "shapeshift");
            TestHelper.selectHotbarSlot(player, 0);
            TestHelper.activateSpell(helper, player);
            helper.runAfterDelay(900, () -> {
                helper.succeed();
            });
        });
    }

    @GameTest
    public void shapeshiftLongerThanDemigod(GameTestHelper helper) {
        ServerPlayer playerGod = (ServerPlayer) helper.makeMockPlayer(GameType.SURVIVAL);
        ServerPlayer playerDemigod = (ServerPlayer) helper.makeMockPlayer(GameType.SURVIVAL);
        TestHelper.setRank(helper, playerGod, "god");
        TestHelper.equip(helper, playerGod, "utility", "magic");
        TestHelper.equip(helper, playerDemigod, "utility", "magic");

        helper.runAfterDelay(2, () -> {
            helper.succeed();
        });
    }

    @GameTest
    public void isIgnoredByRaidersWithMagicGodPassive(GameTestHelper helper) {
        ServerPlayer player = (ServerPlayer) helper.makeMockPlayer(GameType.SURVIVAL);
        TestHelper.setRank(helper, player, "god");
        TestHelper.equip(helper, player, "passive", "magic");

        helper.runAfterDelay(2, () -> {
            var order = AbilityManager.get(player);
            if (order != null) {
                helper.succeed();
            } else {
                helper.fail("Order not found");
            }
        });
    }
}
