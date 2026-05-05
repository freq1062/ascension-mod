package freq.ascension.test.demigod;

import freq.ascension.Config;
import freq.ascension.managers.AscensionData;
import freq.ascension.orders.Magic;
import freq.ascension.test.TestHelper;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import xyz.nucleoid.disguiselib.api.EntityDisguise;

/**
 * Integration tests for the Magic Order (Demigod) abilities.
 */
public class MagicDemigodTests {

    @GameTest(maxTicks = 100)
    public void speedAppliedOnPassiveEquip(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        TestHelper.equip(helper, player, "passive", "magic");

        helper.runAfterDelay(41, () -> {
            TestHelper.assertEffect(helper, player, MobEffects.SPEED,
                    "Magic passive did not apply speed");
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 140)
    public void speedRefreshesAfter40Ticks(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        TestHelper.equip(helper, player, "passive", "magic");

        helper.runAfterDelay(80, () -> {
            TestHelper.assertEffect(helper, player, MobEffects.SPEED,
                    "Magic passive did not refresh speed");
            helper.succeed();
        });
    }

    @GameTest
    public void potionEffectDurationExtendedByPassive(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        TestHelper.equip(helper, player, "utility", "magic");

        MobEffectInstance extended = Magic.INSTANCE.onPotionEffect(player,
                new MobEffectInstance(MobEffects.STRENGTH, 600, 0, false, true, true));
        if (extended.getDuration() > 600) {
            helper.succeed();
        } else {
            helper.fail("Magic utility should extend short beneficial potion effects");
        }
    }

    @GameTest
    public void shapeshiftActivatesDisguise(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        TestHelper.equip(helper, player, "combat", "magic");

        helper.runAfterDelay(2, () -> {
            seedShapeshiftHistory(player, EntityType.COW);
            Magic.INSTANCE.executeActiveSpell("shapeshift", player);
            helper.runAfterDelay(2, () -> {
                if (((EntityDisguise) player).isDisguised()) {
                    helper.succeed();
                } else {
                    helper.fail("shapeshift should disguise the player");
                }
            });
        });
    }

    @GameTest(maxTicks = 650)
    public void shapeshiftExpiresAfter600Ticks(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        TestHelper.equip(helper, player, "combat", "magic");

        helper.runAfterDelay(2, () -> {
            seedShapeshiftHistory(player, EntityType.COW);
            Magic.INSTANCE.executeActiveSpell("shapeshift", player);
            helper.runAfterDelay(Config.magicShapeshiftDuration + 5, () -> {
                if (!((EntityDisguise) player).isDisguised()) {
                    helper.succeed();
                } else {
                    helper.fail("shapeshift should expire after its configured duration");
                }
            });
        });
    }

    @GameTest
    public void enchantCostReducedWithMagicPassive(GameTestHelper helper) {
        if (Magic.INSTANCE.modifyEnchantmentCost(10) == 5) {
            helper.succeed();
        } else {
            helper.fail("Magic passive should reduce a 10-level enchantment cost to 5");
        }
    }

    @GameTest
    public void magicCombatSpellDamagesMob(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        TestHelper.equip(helper, player, "combat", "magic");

        Magic.INSTANCE.onPlayerKill(player,
                helper.spawnWithNoFreeWill(EntityType.SHEEP, player.blockPosition().offset(1, 0, 0)));
        helper.runAfterDelay(1, () -> {
            if (((AscensionData) player).getShapeshiftHistory().contains("minecraft:sheep")) {
                helper.succeed();
            } else {
                helper.fail("Magic combat slot should record killed mobs for shapeshift");
            }
        });
    }

    private static void seedShapeshiftHistory(ServerPlayer player, EntityType<?> form) {
        ((AscensionData) player).pushShapeshiftKill(form);
    }
}
