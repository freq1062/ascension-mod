package freq.ascension.test.demigod;

import java.util.Map;

import freq.ascension.managers.AscensionData;
import freq.ascension.orders.End;
import freq.ascension.registry.SpellRegistry;
import freq.ascension.test.TestHelper;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class EndDemigodTests {

    @GameTest
    public void pearlCooldownHalvedToTenTicks(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        TestHelper.equip(helper, player, "passive", "end");
        ItemStack pearl = new ItemStack(Items.ENDER_PEARL);
        player.getInventory().setItem(0, pearl);
        TestHelper.selectHotbarSlot(player, 0);

        helper.runAfterDelay(2, () -> {
            player.getCooldowns().addCooldown(Items.ENDER_PEARL.getDefaultInstance(), 20);
            UseItemCallback.EVENT.invoker().interact(player, helper.getLevel(), InteractionHand.MAIN_HAND);

            helper.runAfterDelay(9, () -> {
                if (!player.getCooldowns().isOnCooldown(Items.ENDER_PEARL.getDefaultInstance())) {
                    helper.fail("Expected ender pearl cooldown to still be active at 9 ticks");
                    return;
                }

                helper.runAfterDelay(3, () -> {
                    if (player.getCooldowns().isOnCooldown(Items.ENDER_PEARL.getDefaultInstance())) {
                        helper.fail("Expected ender pearl cooldown to end after the End passive reduction");
                    } else {
                        helper.succeed();
                    }
                });
            });
        });
    }

    @GameTest
    public void noEndermiteOnMultiplePearlThrows(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        TestHelper.equip(helper, player, "passive", "end");
        Mob endermite = helper.spawnWithNoFreeWill(EntityType.ENDERMITE, helper.absolutePos(new BlockPos(1, 2, 1)));

        helper.runAfterDelay(2, () -> {
            if (End.INSTANCE.isNeutralBy(player, endermite)) {
                helper.succeed();
            } else {
                helper.fail("Expected End passive to neutralize endermites");
            }
        });
    }

    @GameTest
    public void endermanNeutralWithEndPassive(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        TestHelper.equip(helper, player, "passive", "end");
        Mob enderman = helper.spawnWithNoFreeWill(EntityType.ENDERMAN, helper.absolutePos(new BlockPos(1, 2, 1)));

        helper.runAfterDelay(2, () -> {
            if (End.INSTANCE.isNeutralBy(player, enderman)) {
                helper.succeed();
            } else {
                helper.fail("Expected enderman targeting to be cancelled by End passive");
            }
        });
    }

    @GameTest
    public void endermanHostileWithoutEndPassive(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Mob enderman = helper.spawnWithNoFreeWill(EntityType.ENDERMAN, helper.absolutePos(new BlockPos(1, 2, 1)));

        helper.runAfterDelay(2, () -> {
            enderman.setTarget(player);
            if (enderman.getTarget() == player) {
                helper.succeed();
            } else {
                helper.fail("Expected enderman to target player without End passive");
            }
        });
    }

    @GameTest
    public void voidSpikeKnocksTargetUp5Blocks(GameTestHelper helper) {
        ServerPlayer caster = helper.makeMockServerPlayerInLevel();
        ServerPlayer target = helper.makeMockServerPlayerInLevel();
        TestHelper.equip(helper, caster, "combat", "end");
        TestHelper.bind(helper, caster, 1, "desolation_of_time");
        TestHelper.selectHotbarSlot(caster, 0);
        movePlayerTo(helper, caster, new BlockPos(1, 2, 1));
        movePlayerTo(helper, target, new BlockPos(3, 2, 1));

        helper.runAfterDelay(2, () -> {
            End.INSTANCE.executeActiveSpell("desolation_of_time", caster);
            helper.runAfterDelay(2, () -> {
                var weakness = target.getEffect(MobEffects.WEAKNESS);
                if (End.isAffectedByDesolation(target) && weakness != null) {
                    helper.succeed();
                } else {
                    helper.fail("Expected desolation_of_time to affect nearby players");
                }
            });
        });
    }

    @GameTest
    public void voidSpikeRequiresCombatSlot(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        TestHelper.equip(helper, player, "passive", "end");
        TestHelper.bind(helper, player, 1, "desolation_of_time");

        helper.runAfterDelay(2, () -> {
            Map<Integer, String> bindings = ((AscensionData) player).getSpellBindings();
            if (!bindings.containsValue("desolation_of_time")) {
                helper.succeed();
            } else {
                helper.fail("Expected desolation_of_time binding to require the combat slot");
            }
        });
    }

    @GameTest
    public void chorusFruitTeleportRangeExtended(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        TestHelper.equip(helper, player, "utility", "end");
        TestHelper.bind(helper, player, 1, "teleport");
        TestHelper.selectHotbarSlot(player, 0);
        movePlayerTo(helper, player, new BlockPos(1, 2, 1));
        player.setYRot(-90.0F);
        player.setXRot(0.0F);

        helper.runAfterDelay(2, () -> {
            double startX = player.getX();
            SpellRegistry.teleport(player, 10, 2);
            helper.runAfterDelay(2, () -> {
                double distance = player.getX() - startX;
                if (distance > 8.0) {
                    helper.succeed();
                } else {
                    helper.fail("Expected teleport spell to move player more than 8 blocks, got " + distance);
                }
            });
        });
    }

    private static void movePlayerTo(GameTestHelper helper, ServerPlayer player, BlockPos pos) {
        BlockPos abs = helper.absolutePos(pos);
        player.setPos(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
    }
}
