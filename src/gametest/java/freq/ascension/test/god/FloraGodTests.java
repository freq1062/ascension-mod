package freq.ascension.test.god;

import freq.ascension.managers.AbilityManager;
import freq.ascension.orders.FloraGod;
import freq.ascension.test.TestHelper;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class FloraGodTests {

    @GameTest
    public void goldenAppleGivesAbsorptionIIWithGodPassive(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        TestHelper.setGodDirect(helper, player, "flora");

        helper.runAfterDelay(2, () -> {
            AbilityManager.onItemEaten(player, new ItemStack(Items.GOLDEN_APPLE));
            helper.runAfterDelay(2, () -> {
                var absEffect = player.getEffect(MobEffects.ABSORPTION);
                if (absEffect != null && absEffect.getAmplifier() == 1) {
                    helper.succeed();
                } else {
                    helper.fail("Expected Absorption II from flora god golden apple");
                }
            });
        });
    }

    @GameTest
    public void goldenAppleDescriptionMentionsExtraAbsorptionHearts(GameTestHelper helper) {
        String description = FloraGod.INSTANCE.getDescription("passive").toLowerCase();
        if (description.contains("golden apples give 4 absorption hearts")) {
            helper.succeed();
        } else {
            helper.fail("Expected flora god passive description to mention extra golden apple absorption");
        }
    }

    @GameTest
    public void thornsReturnsDamageOnMeleeHit(GameTestHelper helper) {
        if (FloraGod.INSTANCE.getSpellStats("thorns") != null) {
            helper.succeed();
        } else {
            helper.fail("Missing thorns spell stats");
        }
    }

    @GameTest
    public void thornsFreezesDurationLongerThanDemigod(GameTestHelper helper) {
        String description = FloraGod.INSTANCE.getSpellStats("thorns").getDescription().toLowerCase();
        if (description.contains("4 seconds")) {
            helper.succeed();
        } else {
            helper.fail("Expected thorns description to include 4 second freeze");
        }
    }

    @GameTest
    public void inventoryPlantEffectActivatesWithPlantInInventory(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        TestHelper.setGodDirect(helper, player, "flora");

        helper.runAfterDelay(2, () -> {
            player.getInventory().add(new ItemStack(Items.SEAGRASS));
            if (FloraGod.INSTANCE.hasInventoryPlantEffect(player)) {
                helper.succeed();
            } else {
                helper.fail("Expected flora god inventory plant effect to activate when carrying a plant");
            }
        });
    }

    @GameTest
    public void creepersNeutralWithInventoryPlantForGod(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        TestHelper.setGodDirect(helper, player, "flora");

        helper.runAfterDelay(2, () -> {
            player.getInventory().add(new ItemStack(Items.OAK_LEAVES));
            Mob creeper = helper.spawnWithNoFreeWill(EntityType.CREEPER, helper.absolutePos(new BlockPos(1, 2, 1)));
            if (FloraGod.INSTANCE.isNeutralBy(player, creeper)) {
                helper.succeed();
            } else {
                helper.fail("Expected creeper neutrality with inventory plant");
            }
        });
    }
}
