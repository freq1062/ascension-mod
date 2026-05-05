package freq.ascension.test.demigod;

import freq.ascension.orders.Ocean;
import freq.ascension.orders.Order.DamageContext;
import freq.ascension.managers.AttackSnapshotManager;
import freq.ascension.test.TestHelper;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;

/**
 * Integration tests for the Ocean Order (Demigod) abilities.
 */
public class OceanDemigodTests {

    @GameTest(maxTicks = 100)
    public void waterBreathingAppliedOnPassiveEquip(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        TestHelper.equip(helper, player, "passive", "ocean");

        helper.runAfterDelay(41, () -> {
            TestHelper.assertEffect(helper, player, MobEffects.WATER_BREATHING,
                    "Ocean passive did not apply water breathing");
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 140)
    public void waterBreathingRefreshesAfter40Ticks(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        TestHelper.equip(helper, player, "passive", "ocean");

        helper.runAfterDelay(80, () -> {
            TestHelper.assertEffect(helper, player, MobEffects.WATER_BREATHING,
                    "Ocean passive did not refresh water breathing");
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 120)
    public void waterBreathingRemovedOnUnequip(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        TestHelper.equip(helper, player, "passive", "ocean");

        helper.runAfterDelay(41, () -> {
            TestHelper.assertEffect(helper, player, MobEffects.WATER_BREATHING,
                    "Ocean passive never applied water breathing before unequip");
            TestHelper.unequip(helper, player, "passive");
            helper.runAfterDelay(2, () -> {
                TestHelper.assertNoEffect(helper, player, MobEffects.WATER_BREATHING,
                        "Ocean passive left water breathing behind after unequip");
                helper.succeed();
            });
        });
    }

    @GameTest
    public void canWalkOnPowderSnowWithOceanPassive(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        TestHelper.equip(helper, player, "passive", "ocean");

        helper.runAfterDelay(5, () -> {
            if (Ocean.INSTANCE.canWalkOnPowderSnow(player)) {
                helper.succeed();
            } else {
                helper.fail("Ocean passive should allow walking on powder snow");
            }
        });
    }

    @GameTest
    public void autocritInWaterDealsExtraDamage(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        TestHelper.equip(helper, player, "passive", "ocean");

        helper.runAfterDelay(5, () -> {
            helper.getLevel().setBlockAndUpdate(player.blockPosition(), Blocks.WATER.defaultBlockState());
            Mob victim = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, player.blockPosition().offset(0, 0, 1));
            DamageContext context = new DamageContext(player.damageSources().playerAttack(player), 10.0f);
            AttackSnapshotManager.captureAttack(player, victim, 1.0f);
            Ocean.INSTANCE.onEntityDamageByEntity(player, victim, context);

            if (context.getAmount() > 10.0f) {
                helper.succeed();
            } else {
                helper.fail("Ocean passive should increase melee damage while the player is in water");
            }
        });
    }

    @GameTest
    public void noAutocritOnLandWithoutDrown(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        TestHelper.equip(helper, player, "passive", "ocean");

        helper.runAfterDelay(5, () -> {
            Mob victim = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, player.blockPosition().offset(0, 0, 1));
            DamageContext context = new DamageContext(player.damageSources().playerAttack(player), 10.0f);
            AttackSnapshotManager.captureAttack(player, victim, 1.0f);
            Ocean.INSTANCE.onEntityDamageByEntity(player, victim, context);

            if (Math.abs(context.getAmount() - 10.0f) < 0.001f) {
                helper.succeed();
            } else {
                helper.fail("Ocean passive should not autocrit on land without drown active");
            }
        });
    }

    @GameTest
    public void dolphinsGraceTogglesOnWithPassive(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        TestHelper.equip(helper, player, "passive", "ocean");

        helper.runAfterDelay(5, () -> {
            Ocean.INSTANCE.executeActiveSpell("dolphins_grace", player);
            helper.runAfterDelay(2, () -> {
                TestHelper.assertEffect(helper, player, MobEffects.DOLPHINS_GRACE,
                        "dolphins_grace should toggle on when Ocean passive is equipped");
                helper.succeed();
            });
        });
    }

    @GameTest
    public void dolphinsGraceTogglesOff(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        TestHelper.equip(helper, player, "passive", "ocean");

        helper.runAfterDelay(5, () -> {
            Ocean.INSTANCE.executeActiveSpell("dolphins_grace", player);
            helper.runAfterDelay(2, () -> {
                Ocean.INSTANCE.executeActiveSpell("dolphins_grace", player);
                helper.runAfterDelay(2, () -> {
                    TestHelper.assertNoEffect(helper, player, MobEffects.DOLPHINS_GRACE,
                            "dolphins_grace should toggle off on its second activation");
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(maxTicks = 120)
    public void dolphinsGraceRefreshesAfter40Ticks(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        TestHelper.equip(helper, player, "passive", "ocean");

        helper.runAfterDelay(5, () -> {
            Ocean.INSTANCE.executeActiveSpell("dolphins_grace", player);
            helper.runAfterDelay(80, () -> {
                TestHelper.assertEffect(helper, player, MobEffects.DOLPHINS_GRACE,
                        "Ocean passive should refresh dolphins_grace while it remains toggled on");
                helper.succeed();
            });
        });
    }

    @GameTest
    public void molecularFluxConvertsWaterToIce(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        TestHelper.equip(helper, player, "utility", "ocean");

        helper.runAfterDelay(5, () -> {
            BlockPos targetPos = faceSouthAtUtilityTarget(player);
            helper.getLevel().setBlockAndUpdate(targetPos, Blocks.WATER.defaultBlockState());
            Ocean.INSTANCE.executeActiveSpell("molecular_flux", player);
            helper.runAfterDelay(5, () -> {
                if (helper.getLevel().getBlockState(targetPos).is(Blocks.FROSTED_ICE)) {
                    helper.succeed();
                } else {
                    helper.fail("molecular_flux should convert water to frosted ice");
                }
            });
        });
    }

    @GameTest
    public void molecularFluxConvertsIceToWater(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        TestHelper.equip(helper, player, "utility", "ocean");

        helper.runAfterDelay(5, () -> {
            BlockPos targetPos = faceSouthAtUtilityTarget(player);
            helper.getLevel().setBlockAndUpdate(targetPos, Blocks.ICE.defaultBlockState());
            Ocean.INSTANCE.executeActiveSpell("molecular_flux", player);
            helper.runAfterDelay(5, () -> {
                if (helper.getLevel().getBlockState(targetPos).is(Blocks.WATER)) {
                    helper.succeed();
                } else {
                    helper.fail("molecular_flux should convert ice to water");
                }
            });
        });
    }

    @GameTest
    public void molecularFluxRemovesCobweb(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        TestHelper.equip(helper, player, "utility", "ocean");

        helper.runAfterDelay(5, () -> {
            BlockPos targetPos = faceSouthAtUtilityTarget(player);
            helper.getLevel().setBlockAndUpdate(targetPos, Blocks.COBWEB.defaultBlockState());
            Ocean.INSTANCE.executeActiveSpell("molecular_flux", player);
            helper.runAfterDelay(5, () -> {
                if (helper.getLevel().getBlockState(targetPos).isAir()) {
                    helper.succeed();
                } else {
                    helper.fail("molecular_flux should remove cobwebs");
                }
            });
        });
    }

    @GameTest
    public void drownSpellReducesNearbyEntityOxygen(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ServerPlayer victim = helper.makeMockServerPlayerInLevel();
        TestHelper.equip(helper, player, "combat", "ocean");

        helper.runAfterDelay(5, () -> {
            victim.setPos(player.getX() + 1.0, player.getY(), player.getZ());
            victim.setAirSupply(victim.getMaxAirSupply());
            Ocean.INSTANCE.executeActiveSpell("drown", player);
            helper.runAfterDelay(3, () -> {
                if (victim.getAirSupply() < victim.getMaxAirSupply()) {
                    helper.succeed();
                } else {
                    helper.fail("drown should reduce the nearby victim's air supply");
                }
            });
        });
    }

    @GameTest(maxTicks = 100)
    public void drownSpellEnablesLandAutocritWhileActive(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        TestHelper.equip(helper, player, "passive", "ocean");
        TestHelper.equip(helper, player, "combat", "ocean");

        helper.runAfterDelay(5, () -> {
            Ocean.INSTANCE.executeActiveSpell("drown", player);
            helper.runAfterDelay(3, () -> {
                Mob victim = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, player.blockPosition().offset(0, 0, 1));
                DamageContext context = new DamageContext(player.damageSources().playerAttack(player), 10.0f);
                AttackSnapshotManager.captureAttack(player, victim, 1.0f);
                Ocean.INSTANCE.onEntityDamageByEntity(player, victim, context);

                if (context.getAmount() > 10.0f) {
                    helper.succeed();
                } else {
                    helper.fail("Active drown should enable Ocean autocrits on land");
                }
            });
        });
    }

    private static BlockPos faceSouthAtUtilityTarget(ServerPlayer player) {
        return player.blockPosition().offset(0, 1, 3);
    }
}
