package freq.ascension.test.god;

import freq.ascension.Config;
import freq.ascension.orders.Magic;
import freq.ascension.orders.MagicGod;
import freq.ascension.test.TestHelper;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

public class MagicGodTests {

    @GameTest
    public void potionEffectsExtendedTo12000Ticks(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        TestHelper.setGodDirect(helper, player, "magic");

        helper.runAfterDelay(2, () -> {
            MobEffectInstance result = MagicGod.INSTANCE.onPotionEffect(
                    player,
                    new MobEffectInstance(MobEffects.STRENGTH, 100, 0, false, true, true));
            if (result.getDuration() == 12_000) {
                helper.succeed();
            } else {
                helper.fail("Expected potion duration 12000, got " + result.getDuration());
            }
        });
    }

    @GameTest
    public void potionExtensionLongerThanDemigod(GameTestHelper helper) {
        ServerPlayer playerGod = helper.makeMockServerPlayerInLevel();
        ServerPlayer playerDemigod = helper.makeMockServerPlayerInLevel();
        TestHelper.setGodDirect(helper, playerGod, "magic");
        TestHelper.equip(helper, playerDemigod, "utility", "magic");

        helper.runAfterDelay(2, () -> {
            MobEffectInstance base = new MobEffectInstance(MobEffects.STRENGTH, 100, 0, false, true, true);
            int godDuration = MagicGod.INSTANCE.onPotionEffect(playerGod, base).getDuration();
            int demigodDuration = Magic.INSTANCE.onPotionEffect(playerDemigod, base).getDuration();
            if (godDuration > demigodDuration) {
                helper.succeed();
            } else {
                helper.fail("Expected god duration > demigod duration, got " + godDuration + " vs " + demigodDuration);
            }
        });
    }

    @GameTest
    public void enchantCostReducedTo10PctForMagicGod(GameTestHelper helper) {
        int modifiedCost = MagicGod.INSTANCE.modifyEnchantmentCost(100);
        if (modifiedCost == 10) {
            helper.succeed();
        } else {
            helper.fail("Expected enchant cost 10, got " + modifiedCost);
        }
    }

    @GameTest
    public void enchantCostLowerThanDemigod(GameTestHelper helper) {
        int godCost = MagicGod.INSTANCE.modifyEnchantmentCost(100);
        int demigodCost = Magic.INSTANCE.modifyEnchantmentCost(100);
        if (godCost < demigodCost) {
            helper.succeed();
        } else {
            helper.fail("Expected god cost < demigod cost, got " + godCost + " vs " + demigodCost);
        }
    }

    @GameTest(maxTicks = 60)
    public void speedIIAppliedForMagicGod(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        TestHelper.setGodDirect(helper, player, "magic");

        helper.runAfterDelay(41, () -> {
            var speedEffect = player.getEffect(MobEffects.SPEED);
            if (speedEffect != null && speedEffect.getAmplifier() == 1) {
                helper.succeed();
            } else {
                helper.fail("Expected Speed II from magic god passive");
            }
        });
    }

    @GameTest
    public void shapeshiftDurationIs900TicksForGod(GameTestHelper helper) {
        var stats = MagicGod.INSTANCE.getSpellStats("shapeshift");
        if (stats != null && stats.getInt(0) == Config.magicGodShapeshiftDuration) {
            helper.succeed();
        } else {
            helper.fail("Expected shapeshift duration " + Config.magicGodShapeshiftDuration);
        }
    }

    @GameTest
    public void shapeshiftLongerThanDemigod(GameTestHelper helper) {
        int godDuration = MagicGod.INSTANCE.getSpellStats("shapeshift").getInt(0);
        int demigodDuration = Magic.INSTANCE.getSpellStats("shapeshift").getInt(0);
        if (godDuration > demigodDuration) {
            helper.succeed();
        } else {
            helper.fail("Expected god shapeshift duration > demigod duration");
        }
    }

    @GameTest
    public void isIgnoredByRaidersWithMagicGodPassive(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        TestHelper.setGodDirect(helper, player, "magic");

        helper.runAfterDelay(2, () -> {
            Mob raider = helper.spawnWithNoFreeWill(EntityType.PILLAGER, helper.absolutePos(new BlockPos(1, 2, 1)));
            if (MagicGod.INSTANCE.isIgnoredBy(player, raider)) {
                helper.succeed();
            } else {
                helper.fail("Expected raider to ignore magic god");
            }
        });
    }
}
