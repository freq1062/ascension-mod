package freq.ascension.test.weapons;

import freq.ascension.orders.Order;
import freq.ascension.registry.WeaponRegistry;
import freq.ascension.test.TestHelper;
import freq.ascension.weapons.TempestTrident;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;

/**
 * Tests for the TempestTrident weapon: lightning strike on third melee hit,
 * mode toggling between LOYALTY and RIPTIDE, invisible thrown entity,
 * enchantments, unbreakable attribute, and Ocean order assignment.
 */
public class WeaponTempestTridentTests {

    /**
     * Gives TempestTrident to ocean god; hits mob 3 times in melee;
     * asserts lightning bolt strikes mob on 3rd hit.
     */
    @GameTest
    public void tempestTridentLightningStrikesOnThirdMeleeHit(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack stack = equipTempestTrident(player);
        Mob mob = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 3.0F, 2.0F, 1.0F);

        helper.runAfterDelay(2, () -> {
            float before = mob.getHealth();
            Order.DamageContext context = new Order.DamageContext(helper.getLevel().damageSources().magic(), 0.0F);
            TempestTrident.INSTANCE.onProjectileHit(player, mob, context);
            TempestTrident.INSTANCE.onProjectileHit(player, mob, context);
            TempestTrident.INSTANCE.onProjectileHit(player, mob, context);

            int lightningCount = helper.getLevel().getEntitiesOfClass(LightningBolt.class, mob.getBoundingBox().inflate(2.0))
                    .size();
            if (!TempestTrident.isLoyaltyModeStack(stack)) {
                helper.fail("Expected Tempest Trident test stack to stay in loyalty mode");
            } else if (lightningCount < 1) {
                helper.fail("Expected a lightning bolt on the third Tempest Trident hit");
            } else if (TempestTrident.hitCounters.getOrDefault(mob.getUUID(), -1) != 0) {
                helper.fail("Expected hit counter to reset after the third hit");
            } else if (mob.getHealth() >= before) {
                helper.fail("Expected the third hit to deal damage");
            } else {
                helper.succeed();
            }
        });
    }

    /**
     * Sneak+right-click trident; asserts mode = RIPTIDE.
     */
    @GameTest
    public void tempestTridentModeTogglesToRiptide(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack stack = equipTempestTrident(player);

        helper.runAfterDelay(2, () -> {
            TempestTrident.INSTANCE.onShiftLeftClick(player);
            var enchantments = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            int riptide = EnchantmentHelper
                    .getItemEnchantmentLevel(enchantments.getOrThrow(Enchantments.RIPTIDE), stack);
            int loyalty = EnchantmentHelper
                    .getItemEnchantmentLevel(enchantments.getOrThrow(Enchantments.LOYALTY), stack);

            if (stack.get(DataComponents.CUSTOM_MODEL_DATA) == null) {
                helper.fail("Expected Tempest Trident to retain custom model data");
            } else if (!stack.get(DataComponents.CUSTOM_MODEL_DATA).strings().contains(TempestTrident.MODEL_RIPTIDE)) {
                helper.fail("Expected Tempest Trident to switch to riptide mode");
            } else if (riptide != 3 || loyalty != 0) {
                helper.fail("Expected Riptide III and no Loyalty after toggling");
            } else {
                helper.succeed();
            }
        });
    }

    /**
     * Toggle LOYALTY→RIPTIDE→LOYALTY; asserts mode returns to LOYALTY.
     */
    @GameTest
    public void tempestTridentModeTogglesCycleBackToLoyalty(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack stack = equipTempestTrident(player);

        helper.runAfterDelay(2, () -> {
            TempestTrident.INSTANCE.onShiftLeftClick(player);
            helper.runAfterDelay(11, () -> {
                TempestTrident.INSTANCE.onShiftLeftClick(player);
                var enchantments = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
                int loyalty = EnchantmentHelper
                        .getItemEnchantmentLevel(enchantments.getOrThrow(Enchantments.LOYALTY), stack);
                int riptide = EnchantmentHelper
                        .getItemEnchantmentLevel(enchantments.getOrThrow(Enchantments.RIPTIDE), stack);

                if (!stack.get(DataComponents.CUSTOM_MODEL_DATA).strings().contains(TempestTrident.MODEL_LOYALTY)) {
                    helper.fail("Expected Tempest Trident to cycle back to loyalty mode");
                } else if (loyalty != 3 || riptide != 0) {
                    helper.fail("Expected Loyalty III and no Riptide after cycling back");
                } else {
                    helper.succeed();
                }
            });
        });
    }

    /**
     * Throws trident; asserts thrown trident entity is invisible to observers.
     */
    @GameTest
    public void tempestTridentThrownEntityIsInvisible(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack stack = equipTempestTrident(player);
        ThrownTrident trident = new ThrownTrident(helper.getLevel(), player, stack.copy());
        trident.setPos(player.getX(), player.getY() + 1.0, player.getZ());

        helper.runAfterDelay(2, () -> {
            TempestTrident.onTridentThrown(trident, player);
            helper.runAfterDelay(2, () -> {
                Entity display = helper.getLevel().getEntity(TempestTrident.tridentToDisplay.get(trident.getUUID()));
                if (!trident.isInvisible()) {
                    helper.fail("Expected thrown Tempest Trident entity to be invisible");
                } else if (display == null) {
                    helper.fail("Expected Tempest Trident throw to spawn a tracking display");
                } else {
                    helper.succeed();
                }
            });
        });
    }

    /**
     * Gives trident; asserts Impaling 5 present.
     */
    @GameTest
    public void tempestTridentHasImpalingEnchant(GameTestHelper helper) {
        ItemStack stack = TempestTrident.INSTANCE.createItem();
        var enchantments = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        int impaling = EnchantmentHelper.getItemEnchantmentLevel(enchantments.getOrThrow(Enchantments.IMPALING), stack);
        if (impaling == 5) {
            helper.succeed();
        } else {
            helper.fail("Expected Impaling V, got " + impaling);
        }
    }

    /**
     * Gives trident; asserts Sharpness 5 present.
     */
    @GameTest
    public void tempestTridentHasSharpnessEnchant(GameTestHelper helper) {
        ItemStack stack = TempestTrident.INSTANCE.createItem();
        var enchantments = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        int sharpness = EnchantmentHelper
                .getItemEnchantmentLevel(enchantments.getOrThrow(Enchantments.SHARPNESS), stack);
        if (sharpness == 5) {
            helper.succeed();
        } else {
            helper.fail("Expected Sharpness V, got " + sharpness);
        }
    }

    /**
     * Gives trident; asserts unbreakable.
     */
    @GameTest
    public void tempestTridentIsUnbreakable(GameTestHelper helper) {
        if (TempestTrident.INSTANCE.createItem().get(DataComponents.UNBREAKABLE) != null) {
            helper.succeed();
        } else {
            helper.fail("Expected Tempest Trident to be unbreakable");
        }
    }

    /**
     * Checks weapon registry; asserts TempestTrident registered for Ocean order.
     */
    @GameTest
    public void tempestTridentAssignedToOceanGodOrder(GameTestHelper helper) {
        if (WeaponRegistry.getForOrder("ocean") == TempestTrident.INSTANCE) {
            helper.succeed();
        } else {
            helper.fail("Expected Tempest Trident to be registered for the Ocean order");
        }
    }

    private static ItemStack equipTempestTrident(ServerPlayer player) {
        ItemStack stack = TempestTrident.INSTANCE.createItem();
        player.getInventory().setItem(0, stack);
        TestHelper.selectHotbarSlot(player, 0);
        return stack;
    }
}
