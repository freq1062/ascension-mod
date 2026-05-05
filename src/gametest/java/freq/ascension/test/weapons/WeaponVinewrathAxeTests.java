package freq.ascension.test.weapons;

import freq.ascension.orders.Order;
import freq.ascension.registry.WeaponRegistry;
import freq.ascension.weapons.VinewrathAxe;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Display.BlockDisplay;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;

/**
 * Tests for the VinewrathAxe weapon: shield disable on player hit,
 * no vine spawn on non-player hit, enchantments, unbreakable attribute,
 * and Flora order assignment.
 */
public class WeaponVinewrathAxeTests {

    /**
     * Gives VinewrathAxe to flora god; attacks shielding player;
     * asserts shield is put on cooldown.
     */
    @GameTest
    public void vinewrathAxeAttackDisablesTargetShield(GameTestHelper helper) {
        ServerPlayer attacker = helper.makeMockServerPlayerInLevel();
        ServerPlayer victim = helper.makeMockServerPlayerInLevel();
        attacker.setItemInHand(InteractionHand.MAIN_HAND, VinewrathAxe.INSTANCE.createItem());
        victim.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
        victim.startUsingItem(InteractionHand.OFF_HAND);

        VinewrathAxe.INSTANCE.onAttack(attacker, victim,
                new Order.DamageContext(helper.getLevel().damageSources().playerAttack(attacker), 1.0f));
        helper.runAfterDelay(2, () -> {
            if (victim.getCooldowns().isOnCooldown(Items.SHIELD.getDefaultInstance())) {
                helper.succeed();
            } else {
                helper.fail("Expected Vinewrath Axe attack to apply a shield cooldown");
            }
        });
    }

    /**
     * Attacks a non-player mob; asserts no vine entities spawned.
     */
    @GameTest
    public void vinewrathAxeNonPlayerHitDoesNotThrowVines(GameTestHelper helper) {
        ServerPlayer attacker = helper.makeMockServerPlayerInLevel();
        attacker.setItemInHand(InteractionHand.MAIN_HAND, VinewrathAxe.INSTANCE.createItem());
        Mob victim = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, helper.absolutePos(new BlockPos(1, 2, 1)));
        AABB box = victim.getBoundingBox().inflate(8.0);
        int baseline = helper.getLevel().getEntitiesOfClass(BlockDisplay.class, box).size();

        VinewrathAxe.INSTANCE.onAttack(attacker, victim,
                new Order.DamageContext(helper.getLevel().damageSources().playerAttack(attacker), 1.0f));
        helper.runAfterDelay(5, () -> {
            int afterHit = helper.getLevel().getEntitiesOfClass(BlockDisplay.class, box).size();
            if (afterHit == baseline) {
                helper.succeed();
            } else {
                helper.fail("Expected non-player Vinewrath Axe hit to spawn no vine displays");
            }
        });
    }

    /**
     * Gives axe; asserts Sharpness enchantment present.
     */
    @GameTest
    public void vinewrathAxeHasSharpnessEnchant(GameTestHelper helper) {
        assertEnchantmentLevel(helper, VinewrathAxe.INSTANCE.createItem(), Enchantments.SHARPNESS, 5,
                "Expected Sharpness V on Vinewrath Axe");
    }

    /**
     * Gives axe; asserts Efficiency enchantment present.
     */
    @GameTest
    public void vinewrathAxeHasEfficiencyEnchant(GameTestHelper helper) {
        assertEnchantmentLevel(helper, VinewrathAxe.INSTANCE.createItem(), Enchantments.EFFICIENCY, 5,
                "Expected Efficiency V on Vinewrath Axe");
    }

    /**
     * Gives axe; asserts unbreakable.
     */
    @GameTest
    public void vinewrathAxeIsUnbreakable(GameTestHelper helper) {
        ItemStack stack = VinewrathAxe.INSTANCE.createItem();
        if (stack.get(DataComponents.UNBREAKABLE) != null) {
            helper.succeed();
        } else {
            helper.fail("Expected Vinewrath Axe to be unbreakable");
        }
    }

    /**
     * Checks weapon registry; asserts VinewrathAxe registered for Flora order.
     */
    @GameTest
    public void vinewrathAxeAssignedToFloraGodOrder(GameTestHelper helper) {
        if (WeaponRegistry.get(VinewrathAxe.INSTANCE.getWeaponId()) == VinewrathAxe.INSTANCE
                && WeaponRegistry.getForOrder(VinewrathAxe.INSTANCE.getParentOrder().getOrderName()) == VinewrathAxe.INSTANCE) {
            helper.succeed();
        } else {
            helper.fail("Expected Vinewrath Axe to be registered for the Flora order");
        }
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
