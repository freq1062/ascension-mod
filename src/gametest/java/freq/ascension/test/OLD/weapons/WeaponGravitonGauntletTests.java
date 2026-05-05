package freq.ascension.test.weapons;

import freq.ascension.registry.WeaponRegistry;
import freq.ascension.test.TestHelper;
import freq.ascension.weapons.GravitonGauntlet;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;

/**
 * Tests for the GravitonGauntlet weapon: pull/push modes, mode toggling,
 * cooldown enforcement, enchantments, unbreakable attribute, and order assignment.
 */
public class WeaponGravitonGauntletTests {

    /**
     * Gives GravitonGauntlet to sky god player in pull mode; right-click mob within range;
     * asserts mob moves toward player.
     */
    @GameTest
    public void gravitonGauntletPullModeAttractsMob(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        equipGauntlet(helper, player);
        Mob mob = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 5.0F, 2.0F, 1.0F);
        player.snapTo(helper.absoluteVec(new Vec3(1.0, 2.0, 1.0)).x, helper.absoluteVec(new Vec3(1.0, 2.0, 1.0)).y,
                helper.absoluteVec(new Vec3(1.0, 2.0, 1.0)).z, 0.0F, 0.0F);

        helper.runAfterDelay(2, () -> {
            GravitonGauntlet.INSTANCE.onShiftUse(player);
            if (mob.getDeltaMovement().x < 0.0) {
                helper.succeed();
            } else {
                helper.fail("Expected pull mode to move the mob toward the player");
            }
        });
    }

    /**
     * Switches to push mode; right-click mob; asserts mob moves away from player.
     */
    @GameTest
    public void gravitonGauntletPushModeRepelsMob(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        equipGauntlet(helper, player);
        Mob mob = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 5.0F, 2.0F, 1.0F);
        player.snapTo(helper.absoluteVec(new Vec3(1.0, 2.0, 1.0)).x, helper.absoluteVec(new Vec3(1.0, 2.0, 1.0)).y,
                helper.absoluteVec(new Vec3(1.0, 2.0, 1.0)).z, 0.0F, 0.0F);

        helper.runAfterDelay(2, () -> {
            GravitonGauntlet.INSTANCE.onShiftLeftClick(player);
            GravitonGauntlet.INSTANCE.onShiftUse(player);
            if (mob.getDeltaMovement().x > 0.0) {
                helper.succeed();
            } else {
                helper.fail("Expected push mode to move the mob away from the player");
            }
        });
    }

    /**
     * Player sneaks + right-clicks gauntlet; asserts mode changes from PULL to PUSH.
     */
    @GameTest
    public void gravitonGauntletModeTogglesSneakRightClick(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        equipGauntlet(helper, player);

        helper.runAfterDelay(2, () -> {
            if (!GravitonGauntlet.getPullMode(player.getUUID())) {
                helper.fail("Expected Graviton Gauntlet to start in pull mode");
                return;
            }

            GravitonGauntlet.INSTANCE.onShiftLeftClick(player);
            if (!GravitonGauntlet.getPullMode(player.getUUID())) {
                helper.succeed();
            } else {
                helper.fail("Expected Graviton Gauntlet mode to toggle to push");
            }
        });
    }

    /**
     * Toggle PULL→PUSH→PULL; asserts mode returns to PULL.
     */
    @GameTest
    public void gravitonGauntletModeTogglesCycleBack(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        equipGauntlet(helper, player);

        helper.runAfterDelay(2, () -> {
            GravitonGauntlet.INSTANCE.onShiftLeftClick(player);
            helper.runAfterDelay(1, () -> {
                GravitonGauntlet.INSTANCE.onShiftLeftClick(player);
                if (GravitonGauntlet.getPullMode(player.getUUID())) {
                    helper.succeed();
                } else {
                    helper.fail("Expected Graviton Gauntlet mode to cycle back to pull");
                }
            });
        });
    }

    /**
     * Activates gauntlet; immediately activates again before cooldown expires;
     * asserts second activation blocked.
     */
    @GameTest
    public void gravitonGauntletCooldownPreventsSpam(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        equipGauntlet(helper, player);
        Mob mob = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 5.0F, 2.0F, 1.0F);
        player.snapTo(helper.absoluteVec(new Vec3(1.0, 2.0, 1.0)).x, helper.absoluteVec(new Vec3(1.0, 2.0, 1.0)).y,
                helper.absoluteVec(new Vec3(1.0, 2.0, 1.0)).z, 0.0F, 0.0F);

        helper.runAfterDelay(2, () -> {
            GravitonGauntlet.INSTANCE.onShiftUse(player);
            if (!GravitonGauntlet.isOnCooldown(player.getUUID(), helper.getLevel().getServer().getTickCount())) {
                helper.fail("Expected Graviton Gauntlet to enter cooldown after use");
                return;
            }

            mob.setDeltaMovement(Vec3.ZERO);
            GravitonGauntlet.INSTANCE.onShiftUse(player);
            if (mob.getDeltaMovement().lengthSqr() == 0.0) {
                helper.succeed();
            } else {
                helper.fail("Expected cooldown to block the second activation");
            }
        });
    }

    /**
     * Gives gauntlet; asserts Efficiency V and Sharpness V are present on the item.
     */
    @GameTest
    public void gravitonGauntletHasCorrectEnchantments(GameTestHelper helper) {
        ItemStack stack = GravitonGauntlet.INSTANCE.createItem();
        var enchantments = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        int sharpness = EnchantmentHelper.getItemEnchantmentLevel(enchantments.getOrThrow(Enchantments.SHARPNESS), stack);
        int efficiency = EnchantmentHelper
                .getItemEnchantmentLevel(enchantments.getOrThrow(Enchantments.EFFICIENCY), stack);

        if (sharpness == 5 && efficiency == 5) {
            helper.succeed();
        } else {
            helper.fail("Expected Sharpness V and Efficiency V, got Sharpness " + sharpness + ", Efficiency "
                    + efficiency);
        }
    }

    /**
     * Gives gauntlet; asserts item has unbreakable attribute.
     */
    @GameTest
    public void gravitonGauntletIsUnbreakable(GameTestHelper helper) {
        ItemStack stack = GravitonGauntlet.INSTANCE.createItem();
        if (stack.get(DataComponents.UNBREAKABLE) != null) {
            helper.succeed();
        } else {
            helper.fail("Expected Graviton Gauntlet to be unbreakable");
        }
    }

    /**
     * Checks weapon registry; asserts GravitonGauntlet is registered for the Sky order.
     */
    @GameTest
    public void gravitonGauntletAssignedToSkyGodOrder(GameTestHelper helper) {
        if (WeaponRegistry.getForOrder("sky") == GravitonGauntlet.INSTANCE) {
            helper.succeed();
        } else {
            helper.fail("Expected Graviton Gauntlet to be registered for the Sky order");
        }
    }

    private static void equipGauntlet(GameTestHelper helper, ServerPlayer player) {
        player.getInventory().setItem(0, GravitonGauntlet.INSTANCE.createItem());
        TestHelper.selectHotbarSlot(player, 0);
        player.snapTo(helper.absoluteVec(new Vec3(1.0, 2.0, 1.0)).x, helper.absoluteVec(new Vec3(1.0, 2.0, 1.0)).y,
                helper.absoluteVec(new Vec3(1.0, 2.0, 1.0)).z, 0.0F, 0.0F);
    }
}
