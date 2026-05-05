package freq.ascension.test.demigod;

import freq.ascension.Config;
import freq.ascension.orders.Nether;
import freq.ascension.orders.Order;
import freq.ascension.test.TestHelper;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.HappyGhast;

public class NetherDemigodTests {

    @GameTest(maxTicks = 100)
    public void fireResistanceAppliedOnPassiveEquip(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        TestHelper.equip(helper, player, "passive", "nether");

        helper.runAfterDelay(41, () -> {
            var effect = player.getEffect(MobEffects.FIRE_RESISTANCE);
            if (effect != null && effect.getAmplifier() == 0) {
                helper.succeed();
            } else {
                helper.fail("Expected Fire Resistance after equipping Nether passive");
            }
        });
    }

    @GameTest(maxTicks = 140)
    public void fireResistanceRefreshesAfter40Ticks(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        TestHelper.equip(helper, player, "passive", "nether");

        helper.runAfterDelay(80, () -> {
            var effect = player.getEffect(MobEffects.FIRE_RESISTANCE);
            if (effect != null && effect.getDuration() > 0) {
                helper.succeed();
            } else {
                helper.fail("Expected Fire Resistance to refresh before expiring");
            }
        });
    }

    @GameTest(maxTicks = 120)
    public void fireResistanceRemovedOnUnequip(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        TestHelper.equip(helper, player, "passive", "nether");

        helper.runAfterDelay(41, () -> {
            TestHelper.unequip(helper, player, "passive");
            helper.runAfterDelay(2, () -> {
                if (player.getEffect(MobEffects.FIRE_RESISTANCE) == null) {
                    helper.succeed();
                } else {
                    helper.fail("Expected Fire Resistance to be removed on unequip");
                }
            });
        });
    }

    @GameTest(maxTicks = 100)
    public void fireDamageBlockedWithNetherPassive(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        TestHelper.equip(helper, player, "passive", "nether");

        helper.runAfterDelay(5, () -> {
            Order.DamageContext context = new Order.DamageContext(helper.getLevel().damageSources().inFire(), 4.0F);
            Nether.INSTANCE.onEntityDamage(player, context);
            if (context.isCancelled()) {
                helper.succeed();
            } else {
                helper.fail("Expected Nether passive to block fire damage");
            }
        });
    }

    @GameTest
    public void autocritAfterRecentFireContactDealsExtraDamage(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        LivingEntity target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, helper.absolutePos(new BlockPos(1, 2, 1)));
        TestHelper.equip(helper, player, "passive", "nether");

        helper.runAfterDelay(5, () -> {
            Nether.recordFireContact(player);
            Order.DamageContext context = new Order.DamageContext(helper.getLevel().damageSources().playerAttack(player), 4.0F);
            Nether.INSTANCE.onEntityDamageByEntity(player, target, context);
            if (context.getAmount() > 4.0F) {
                helper.succeed();
            } else {
                helper.fail("Expected recent fire contact to increase melee damage");
            }
        });
    }

    @GameTest
    public void noAutocritWithoutPriorFireContact(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        LivingEntity target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, helper.absolutePos(new BlockPos(1, 2, 1)));
        TestHelper.equip(helper, player, "passive", "nether");

        helper.runAfterDelay(5, () -> {
            Order.DamageContext context = new Order.DamageContext(helper.getLevel().damageSources().playerAttack(player), 4.0F);
            Nether.INSTANCE.onEntityDamageByEntity(player, target, context);
            if (context.getAmount() == 4.0F) {
                helper.succeed();
            } else {
                helper.fail("Expected base damage without recent fire contact");
            }
        });
    }

    @GameTest
    public void ghastCarryCapturesGhastOnActivate(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        TestHelper.equip(helper, player, "utility", "nether");
        TestHelper.bind(helper, player, 1, "ghast_carry");
        TestHelper.selectHotbarSlot(player, 0);

        helper.runAfterDelay(2, () -> {
            Nether.INSTANCE.executeActiveSpell("ghast_carry", player);
            helper.runAfterDelay(3, () -> {
                if (player.getVehicle() instanceof HappyGhast) {
                    helper.succeed();
                } else {
                    helper.fail("Expected ghast_carry to mount the player on a Happy Ghast");
                }
            });
        });
    }

    @GameTest
    public void soulRageBoostsDamageWhileActive(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        LivingEntity target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, helper.absolutePos(new BlockPos(1, 2, 1)));
        TestHelper.equip(helper, player, "combat", "nether");
        TestHelper.bind(helper, player, 1, "soul_rage");
        TestHelper.selectHotbarSlot(player, 0);
        player.setHealth(8.0F);

        helper.runAfterDelay(2, () -> {
            Nether.INSTANCE.executeActiveSpell("soul_rage", player);
            helper.runAfterDelay(2, () -> {
                Order.DamageContext context = new Order.DamageContext(helper.getLevel().damageSources().playerAttack(player), 4.0F);
                Nether.INSTANCE.onEntityDamageByEntity(player, target, context);
                if (context.getAmount() > 4.0F) {
                    helper.succeed();
                } else {
                    helper.fail("Expected Soul Rage to boost damage while active");
                }
            });
        });
    }

    @GameTest(maxTicks = 500)
    public void soulRageExpiresAndDamageReturnsToBase(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        LivingEntity target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, helper.absolutePos(new BlockPos(1, 2, 1)));
        TestHelper.equip(helper, player, "combat", "nether");
        TestHelper.bind(helper, player, 1, "soul_rage");
        TestHelper.selectHotbarSlot(player, 0);
        player.setHealth(8.0F);

        helper.runAfterDelay(2, () -> {
            Nether.INSTANCE.executeActiveSpell("soul_rage", player);
            helper.runAfterDelay(Config.netherSoulRageDuration * 20L + 5L, () -> {
                Order.DamageContext context = new Order.DamageContext(helper.getLevel().damageSources().playerAttack(player), 4.0F);
                Nether.INSTANCE.onEntityDamageByEntity(player, target, context);
                if (context.getAmount() == 4.0F) {
                    helper.succeed();
                } else {
                    helper.fail("Expected Soul Rage bonus to expire");
                }
            });
        });
    }
}
