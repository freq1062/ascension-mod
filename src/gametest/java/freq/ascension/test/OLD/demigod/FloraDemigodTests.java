package freq.ascension.test.demigod;

import freq.ascension.managers.PlantProximityManager;
import freq.ascension.orders.Flora;
import freq.ascension.registry.SpellRegistry;
import freq.ascension.test.TestHelper;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

public class FloraDemigodTests {

    @GameTest(maxTicks = 100)
    public void regenAppliedOnPassiveEquip(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        TestHelper.equip(helper, player, "passive", "flora");

        helper.runAfterDelay(41, () -> {
            var regen = player.getEffect(MobEffects.REGENERATION);
            if (regen != null && regen.getAmplifier() == 0) {
                helper.succeed();
            } else {
                helper.fail("Expected Regeneration I from Flora passive");
            }
        });
    }

    @GameTest(maxTicks = 140)
    public void regenRefreshesAfter40Ticks(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        TestHelper.equip(helper, player, "passive", "flora");

        helper.runAfterDelay(80, () -> {
            var regen = player.getEffect(MobEffects.REGENERATION);
            if (regen != null && regen.getDuration() > 0) {
                helper.succeed();
            } else {
                helper.fail("Expected Regeneration to refresh before expiring");
            }
        });
    }

    @GameTest
    public void negativeEffectsBlockedByFloraPassive(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        TestHelper.equip(helper, player, "passive", "flora");

        MobEffectInstance blocked = Flora.INSTANCE.onPotionEffect(player,
                new MobEffectInstance(MobEffects.POISON, 100, 0, false, true, true));
        if (blocked.getDuration() <= 0) {
            helper.succeed();
        } else {
            helper.fail("Expected Flora passive to block visible negative potion effects");
        }
    }

    @GameTest
    public void beeIgnoresPlayerWithFloraPassive(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        TestHelper.equip(helper, player, "passive", "flora");
        Mob bee = helper.spawnWithNoFreeWill(EntityType.BEE, helper.absolutePos(new BlockPos(1, 2, 1)));

        helper.runAfterDelay(2, () -> {
            if (Flora.INSTANCE.isIgnoredBy(player, bee)) {
                helper.succeed();
            } else {
                helper.fail("Expected bee targeting to be cancelled by Flora passive");
            }
        });
    }

    @GameTest
    public void beeHostileWithoutFloraPassive(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Mob bee = helper.spawnWithNoFreeWill(EntityType.BEE, helper.absolutePos(new BlockPos(1, 2, 1)));

        helper.runAfterDelay(2, () -> {
            bee.setTarget(player);
            if (bee.getTarget() == player) {
                helper.succeed();
            } else {
                helper.fail("Expected bee to target player without Flora passive");
            }
        });
    }

    @GameTest
    public void cropsNotTrampleedWithFloraPassive(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        TestHelper.equip(helper, player, "passive", "flora");
        BlockPos farmlandPos = new BlockPos(1, 2, 1);
        helper.setBlock(farmlandPos, Blocks.FARMLAND);

        helper.runAfterDelay(2, () -> {
            BlockPos abs = helper.absolutePos(farmlandPos);
            Blocks.FARMLAND.fallOn(helper.getLevel(), helper.getLevel().getBlockState(abs), abs, player, 1.0);
            if (helper.getLevel().getBlockState(abs).is(Blocks.FARMLAND)) {
                helper.succeed();
            } else {
                helper.fail("Expected Flora passive to prevent farmland trampling");
            }
        });
    }

    @GameTest
    public void foodSaturationDoubledWithFlora(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        TestHelper.equip(helper, player, "passive", "flora");

        float modified = Flora.INSTANCE.modifySaturation(player, 2.0F);
        if (modified == 3.0F) {
            helper.succeed();
        } else {
            helper.fail("Expected Flora passive to scale saturation from 2.0 to 3.0, got " + modified);
        }
    }

    @GameTest
    public void plantCamouflageSculkIgnoredWhenHoldingPlant(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        TestHelper.equip(helper, player, "utility", "flora");
        player.getInventory().setItem(0, new ItemStack(Items.OAK_LEAVES));
        TestHelper.selectHotbarSlot(player, 0);

        helper.runAfterDelay(1, () -> {
            if (PlantProximityManager.isNearPlantSync(player) && Flora.INSTANCE.hasPlantProximityEffect(player)) {
                helper.succeed();
            } else {
                helper.fail("Expected plant camouflage prerequisites to be active while holding leaves");
            }
        });
    }

    @GameTest
    public void plantCamouflageRequiresPlantHeldOrNearby(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Mob creeper = helper.spawnWithNoFreeWill(EntityType.CREEPER, helper.absolutePos(new BlockPos(1, 2, 1)));
        TestHelper.equip(helper, player, "utility", "flora");

        helper.runAfterDelay(2, () -> {
            if (!PlantProximityManager.isHoldingPlant(player) && !Flora.INSTANCE.isNeutralBy(player, creeper)) {
                helper.succeed();
            } else {
                helper.fail("Expected plant camouflage benefits to require held or nearby plants");
            }
        });
    }

    @GameTest
    public void creepersNeutralWithPlantCamouflageActive(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        TestHelper.equip(helper, player, "utility", "flora");
        player.getInventory().setItem(0, new ItemStack(Items.OAK_LEAVES));
        TestHelper.selectHotbarSlot(player, 0);
        Mob creeper = helper.spawnWithNoFreeWill(EntityType.CREEPER, helper.absolutePos(new BlockPos(1, 2, 1)));

        helper.runAfterDelay(6, () -> {
            if (Flora.INSTANCE.isNeutralBy(player, creeper)) {
                helper.succeed();
            } else {
                helper.fail("Expected active plant camouflage to cancel creeper targeting");
            }
        });
    }

    @GameTest
    public void creepersHostileWithFloraPassiveOnly(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        TestHelper.equip(helper, player, "passive", "flora");
        player.getInventory().setItem(0, new ItemStack(Items.OAK_LEAVES));
        TestHelper.selectHotbarSlot(player, 0);
        Mob creeper = helper.spawnWithNoFreeWill(EntityType.CREEPER, helper.absolutePos(new BlockPos(1, 2, 1)));

        helper.runAfterDelay(2, () -> {
            creeper.setTarget(player);
            if (creeper.getTarget() == player) {
                helper.succeed();
            } else {
                helper.fail("Expected creeper to remain hostile without Flora utility equipped");
            }
        });
    }

    @GameTest
    public void vineWrathRootsNearbyMob(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Mob target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, helper.absolutePos(new BlockPos(1, 2, 1)));
        TestHelper.equip(helper, player, "combat", "flora");
        TestHelper.bind(helper, player, 1, "thorns");
        TestHelper.selectHotbarSlot(player, 0);

        helper.runAfterDelay(2, () -> {
            Flora.INSTANCE.executeActiveSpell("thorns", player);
            SpellRegistry.executeThorns(player, target);
            helper.runAfterDelay(2, () -> {
                var slowness = target.getEffect(MobEffects.SLOWNESS);
                if (target.isNoAi() && slowness != null && slowness.getAmplifier() == 255) {
                    helper.succeed();
                } else {
                    helper.fail("Expected thorns to root the target mob");
                }
            });
        });
    }
}
