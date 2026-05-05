package freq.ascension.test.weapons;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import freq.ascension.Config;
import freq.ascension.orders.Order;
import freq.ascension.registry.WeaponRegistry;
import freq.ascension.weapons.RuinousScythe;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * Tests for the RuinousScythe weapon: combo activation on the third hit,
 * per-target combo counter isolation, counter reset after trigger, shield
 * hit exclusion, enchantments, unbreakable attribute, and End order assignment.
 */
public class WeaponRuinousScytheTests {

    /**
     * Gives RuinousScythe to end god; hits mob 3 times in succession;
     * asserts combo effect triggers on 3rd hit.
     */
    @GameTest
    public void ruinousScytheComboActivatesOnThirdHit(GameTestHelper helper) {
        resetState();
        ServerPlayer attacker = helper.makeMockServerPlayerInLevel();
        attacker.setItemInHand(InteractionHand.MAIN_HAND, RuinousScythe.INSTANCE.createItem());
        RuinousScythe.CAPTURED_ATTACK_STRENGTH.put(attacker.getUUID(), 1.0f);
        Mob victim = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, helper.absolutePos(new BlockPos(1, 2, 1)));
        float healthBefore = victim.getHealth();

        for (int i = 0; i < Config.ruinousScytheHitsNeeded; i++) {
            RuinousScythe.INSTANCE.onAttack(attacker, victim,
                    new Order.DamageContext(helper.getLevel().damageSources().playerAttack(attacker), 1.0f));
        }

        int comboCount = comboCount(attacker.getUUID(), victim.getUUID());
        if (comboCount == 0 && victim.getHealth() < healthBefore) {
            helper.succeed();
        } else {
            helper.fail("Expected Ruinous Scythe combo to trigger on hit " + Config.ruinousScytheHitsNeeded
                    + "; combo=" + comboCount + ", healthBefore=" + healthBefore + ", healthAfter="
                    + victim.getHealth());
        }
    }

    /**
     * Hits mob A twice; hits mob B once; asserts neither combo has triggered
     * (each mob has independent counter).
     */
    @GameTest
    public void ruinousScytheComboCounterSeparatePerTarget(GameTestHelper helper) {
        resetState();
        ServerPlayer attacker = helper.makeMockServerPlayerInLevel();
        attacker.setItemInHand(InteractionHand.MAIN_HAND, RuinousScythe.INSTANCE.createItem());
        RuinousScythe.CAPTURED_ATTACK_STRENGTH.put(attacker.getUUID(), 1.0f);
        Mob victimA = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, helper.absolutePos(new BlockPos(1, 2, 1)));
        Mob victimB = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, helper.absolutePos(new BlockPos(2, 2, 2)));

        RuinousScythe.INSTANCE.onAttack(attacker, victimA,
                new Order.DamageContext(helper.getLevel().damageSources().playerAttack(attacker), 1.0f));
        RuinousScythe.INSTANCE.onAttack(attacker, victimA,
                new Order.DamageContext(helper.getLevel().damageSources().playerAttack(attacker), 1.0f));
        RuinousScythe.INSTANCE.onAttack(attacker, victimB,
                new Order.DamageContext(helper.getLevel().damageSources().playerAttack(attacker), 1.0f));

        if (comboCount(attacker.getUUID(), victimA.getUUID()) == 2
                && comboCount(attacker.getUUID(), victimB.getUUID()) == 1) {
            helper.succeed();
        } else {
            helper.fail("Expected separate combo counters per target");
        }
    }

    /**
     * Triggers combo on 3rd hit; hits same mob again;
     * asserts counter = 1 (not 4).
     */
    @GameTest
    public void ruinousScytheComboResetsToOneAfterTrigger(GameTestHelper helper) {
        resetState();
        ServerPlayer attacker = helper.makeMockServerPlayerInLevel();
        attacker.setItemInHand(InteractionHand.MAIN_HAND, RuinousScythe.INSTANCE.createItem());
        RuinousScythe.CAPTURED_ATTACK_STRENGTH.put(attacker.getUUID(), 1.0f);
        Mob victim = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, helper.absolutePos(new BlockPos(1, 2, 1)));

        for (int i = 0; i < Config.ruinousScytheHitsNeeded + 1; i++) {
            RuinousScythe.INSTANCE.onAttack(attacker, victim,
                    new Order.DamageContext(helper.getLevel().damageSources().playerAttack(attacker), 1.0f));
        }

        int comboCount = comboCount(attacker.getUUID(), victim.getUUID());
        if (comboCount == 1) {
            helper.succeed();
        } else {
            helper.fail("Expected combo counter to reset to 1 after trigger; got " + comboCount);
        }
    }

    /**
     * Hits blocking player's shield; asserts combo counter unchanged.
     */
    @GameTest
    public void ruinousScytheShieldHitDoesNotCountToCombo(GameTestHelper helper) {
        resetState();
        ServerPlayer attacker = helper.makeMockServerPlayerInLevel();
        ServerPlayer victim = helper.makeMockServerPlayerInLevel();
        attacker.setItemInHand(InteractionHand.MAIN_HAND, RuinousScythe.INSTANCE.createItem());
        victim.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
        victim.startUsingItem(InteractionHand.OFF_HAND);
        RuinousScythe.CAPTURED_ATTACK_STRENGTH.put(attacker.getUUID(), 1.0f);

        helper.runAfterDelay(2, () -> {
            if (!victim.isBlocking()) {
                helper.fail("Expected mock victim to be blocking with a shield");
                return;
            }
            RuinousScythe.INSTANCE.onAttack(attacker, victim,
                    new Order.DamageContext(helper.getLevel().damageSources().playerAttack(attacker), 1.0f));
            if (comboCount(attacker.getUUID(), victim.getUUID()) == 0) {
                helper.succeed();
            } else {
                helper.fail("Expected blocked shield hit to leave combo counter unchanged");
            }
        });
    }

    /**
     * Gives scythe; asserts Sharpness 5 present.
     */
    @GameTest
    public void ruinousScytheHasSharpnessEnchant(GameTestHelper helper) {
        assertEnchantmentLevel(helper, RuinousScythe.INSTANCE.createItem(), Enchantments.SHARPNESS, 5,
                "Expected Sharpness V on Ruinous Scythe");
    }

    /**
     * Gives scythe; asserts Fire Aspect 2 present.
     */
    @GameTest
    public void ruinousScytheHasFireAspectEnchant(GameTestHelper helper) {
        assertEnchantmentLevel(helper, RuinousScythe.INSTANCE.createItem(), Enchantments.FIRE_ASPECT, 2,
                "Expected Fire Aspect II on Ruinous Scythe");
    }

    /**
     * Gives scythe; asserts unbreakable.
     */
    @GameTest
    public void ruinousScytheIsUnbreakable(GameTestHelper helper) {
        ItemStack stack = RuinousScythe.INSTANCE.createItem();
        if (stack.get(DataComponents.UNBREAKABLE) != null) {
            helper.succeed();
        } else {
            helper.fail("Expected Ruinous Scythe to be unbreakable");
        }
    }

    /**
     * Checks weapon registry; asserts RuinousScythe registered for End order.
     */
    @GameTest
    public void ruinousScytheAssignedToEndGodOrder(GameTestHelper helper) {
        if (WeaponRegistry.get(RuinousScythe.INSTANCE.getWeaponId()) == RuinousScythe.INSTANCE
                && WeaponRegistry.getForOrder(RuinousScythe.INSTANCE.getParentOrder().getOrderName()) == RuinousScythe.INSTANCE) {
            helper.succeed();
        } else {
            helper.fail("Expected Ruinous Scythe to be registered for the End order");
        }
    }

    private static void resetState() {
        RuinousScythe.CAPTURED_ATTACK_STRENGTH.clear();
        RuinousScythe.COMBO_COUNTERS.clear();
        RuinousScythe.RESET_GENERATIONS.clear();
    }

    private static int comboCount(UUID attackerId, UUID victimId) {
        ConcurrentHashMap<UUID, Integer> combos = RuinousScythe.COMBO_COUNTERS.get(attackerId);
        if (combos == null) {
            return 0;
        }
        return combos.getOrDefault(victimId, 0);
    }

    private static void assertEnchantmentLevel(GameTestHelper helper, ItemStack stack,
            net.minecraft.resources.ResourceKey<net.minecraft.world.item.enchantment.Enchantment> enchantment,
            int expectedLevel, String failMessage) {
        var enchReg = helper.getLevel().getServer().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        int actualLevel = EnchantmentHelper.getItemEnchantmentLevel(enchReg.getOrThrow(enchantment), stack);
        if (actualLevel == expectedLevel) {
            helper.succeed();
        } else {
            helper.fail(failMessage + "; got level " + actualLevel);
        }
    }
}
