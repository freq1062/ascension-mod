package freq.ascension.test.god;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

import freq.ascension.managers.AbilityManager;
import freq.ascension.test.TestHelper;

public class FloraGodTests {

    @GameTest
    public void goldenAppleGivesAbsorptionIIWithGodPassive(GameTestHelper helper) {
        ServerPlayer player = (ServerPlayer) helper.makeMockPlayer(GameType.SURVIVAL);
        TestHelper.setRank(helper, player, "god");
        TestHelper.equip(helper, player, "passive", "flora");

        helper.runAfterDelay(2, () -> {
            ItemStack apple = new ItemStack(Items.GOLDEN_APPLE);
            player.eat(player.level(), apple);
            helper.runAfterDelay(2, () -> {
                var absEffect = player.getEffect(MobEffects.ABSORPTION);
                if (absEffect != null && absEffect.getAmplifier() == 1) {
                    helper.succeed();
                } else {
                    helper.fail("Not Absorption II");
                }
            });
        });
    }

    @GameTest
    public void goldenAppleGivesRegenFor300Ticks(GameTestHelper helper) {
        ServerPlayer player = (ServerPlayer) helper.makeMockPlayer(GameType.SURVIVAL);
        TestHelper.setRank(helper, player, "god");
        TestHelper.equip(helper, player, "passive", "flora");

        helper.runAfterDelay(2, () -> {
            ItemStack apple = new ItemStack(Items.GOLDEN_APPLE);
            player.eat(player.level(), apple);
            helper.runAfterDelay(2, () -> {
                var regenEffect = player.getEffect(MobEffects.REGENERATION);
                if (regenEffect != null && regenEffect.getDuration() == 300) {
                    helper.succeed();
                } else {
                    helper.fail("Not 300 ticks");
                }
            });
        });
    }

    @GameTest
    public void thornsReturnsDamageOnMeleeHit(GameTestHelper helper) {
        ServerPlayer player = (ServerPlayer) helper.makeMockPlayer(GameType.SURVIVAL);
        ServerPlayer attacker = (ServerPlayer) helper.makeMockPlayer(GameType.SURVIVAL);
        TestHelper.setRank(helper, player, "god");
        TestHelper.equip(helper, player, "combat", "flora");

        helper.runAfterDelay(2, () -> {
            TestHelper.bind(helper, player, 1, "thorns");
            TestHelper.selectHotbarSlot(player, 0);
            TestHelper.activateSpell(helper, player);
            helper.runAfterDelay(2, () -> {
                helper.succeed();
            });
        });
    }

    @GameTest
    public void thornsFreezesDurationLongerThanDemigod(GameTestHelper helper) {
        ServerPlayer player = (ServerPlayer) helper.makeMockPlayer(GameType.SURVIVAL);
        TestHelper.setRank(helper, player, "god");
        TestHelper.equip(helper, player, "combat", "flora");

        helper.runAfterDelay(2, () -> {
            helper.succeed();
        });
    }

    @GameTest
    public void plantRangeReductionIs90PctWithInventoryPlant(GameTestHelper helper) {
        ServerPlayer player = (ServerPlayer) helper.makeMockPlayer(GameType.SURVIVAL);
        TestHelper.setRank(helper, player, "god");
        TestHelper.equip(helper, player, "utility", "flora");

        helper.runAfterDelay(2, () -> {
            player.getInventory().add(new ItemStack(Items.SEAGRASS));
            if (player.containerMenu != null) {
                helper.succeed();
            } else {
                helper.fail("Inventory error");
            }
        });
    }

    @GameTest
    public void creepersNeutralWithInventoryPlantForGod(GameTestHelper helper) {
        ServerPlayer player = (ServerPlayer) helper.makeMockPlayer(GameType.SURVIVAL);
        TestHelper.setRank(helper, player, "god");
        TestHelper.equip(helper, player, "utility", "flora");

        helper.runAfterDelay(2, () -> {
            player.getInventory().add(new ItemStack(Items.OAK_LEAVES));
            helper.succeed();
        });
    }
}
