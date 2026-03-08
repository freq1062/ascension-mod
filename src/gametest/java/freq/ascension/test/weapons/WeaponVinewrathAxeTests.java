package freq.ascension.test.weapons;

import freq.ascension.orders.Flora;
import freq.ascension.orders.Order;
import freq.ascension.registry.WeaponRegistry;
import freq.ascension.weapons.MythicWeapon;
import freq.ascension.weapons.VinewrathAxe;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * GameTest suite for the {@link VinewrathAxe} mythical weapon.
 *
 * <p>Verifies identity, item creation, component data, enchantment presence, shield-disable
 * ability smoke test, and WeaponRegistry integration.
 */
public class WeaponVinewrathAxeTests {

    // ─────────────────────────────────────────────────────────────────────────
    // Identity
    // ─────────────────────────────────────────────────────────────────────────

    @GameTest
    public void vinewrath_axe_weapon_id(GameTestHelper helper) {
        if (!"vinewrath_axe".equals(VinewrathAxe.INSTANCE.getWeaponId())) {
            helper.fail("getWeaponId() must return \"vinewrath_axe\"");
        }
        helper.succeed();
    }

    @GameTest
    public void vinewrath_axe_base_item(GameTestHelper helper) {
        if (VinewrathAxe.INSTANCE.getBaseItem() != Items.DIAMOND_AXE) {
            helper.fail("getBaseItem() must return Items.DIAMOND_AXE");
        }
        helper.succeed();
    }

    @GameTest
    public void vinewrath_axe_parent_order(GameTestHelper helper) {
        if (!"flora".equals(VinewrathAxe.INSTANCE.getParentOrder().getOrderName())) {
            helper.fail("getParentOrder().getOrderName() must return \"flora\"");
        }
        helper.succeed();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Item creation
    // ─────────────────────────────────────────────────────────────────────────

    @GameTest
    public void vinewrath_axe_create_item_not_empty(GameTestHelper helper) {
        ItemStack stack = VinewrathAxe.INSTANCE.createItem();
        if (stack == null || stack.isEmpty()) {
            helper.fail("createItem() must return a non-empty ItemStack");
        }
        if (stack.getItem() != Items.DIAMOND_AXE) {
            helper.fail("createItem() must use DIAMOND_AXE as base, got: " + stack.getItem());
        }
        helper.succeed();
    }

    @GameTest
    public void vinewrath_axe_has_custom_model_data(GameTestHelper helper) {
        ItemStack stack = VinewrathAxe.INSTANCE.createItem();
        CustomModelData cmd = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        if (cmd == null || !cmd.strings().contains("vinewrath_axe")) {
            helper.fail("createItem() must have CustomModelData string \"vinewrath_axe\", got: "
                    + (cmd == null ? "null" : cmd.strings()));
        }
        helper.succeed();
    }

    @GameTest
    public void vinewrath_axe_is_unbreakable(GameTestHelper helper) {
        ItemStack stack = VinewrathAxe.INSTANCE.createItem();
        Unit unbreakable = stack.get(DataComponents.UNBREAKABLE);
        if (unbreakable == null) {
            helper.fail("createItem() must set DataComponents.UNBREAKABLE");
        }
        helper.succeed();
    }

    @GameTest
    public void vinewrath_axe_has_custom_name(GameTestHelper helper) {
        ItemStack stack = VinewrathAxe.INSTANCE.createItem();
        if (stack.get(DataComponents.CUSTOM_NAME) == null) {
            helper.fail("createItem() must set DataComponents.CUSTOM_NAME");
        }
        helper.succeed();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // isItem detection
    // ─────────────────────────────────────────────────────────────────────────

    @GameTest
    public void vinewrath_axe_is_item_detects_own(GameTestHelper helper) {
        ItemStack stack = VinewrathAxe.INSTANCE.createItem();
        if (!VinewrathAxe.INSTANCE.isItem(stack)) {
            helper.fail("isItem() must return true for createItem() result");
        }
        helper.succeed();
    }

    @GameTest
    public void vinewrath_axe_is_item_rejects_plain(GameTestHelper helper) {
        if (VinewrathAxe.INSTANCE.isItem(new ItemStack(Items.DIAMOND_AXE))) {
            helper.fail("isItem() must return false for a plain ItemStack(Items.DIAMOND_AXE)");
        }
        helper.succeed();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Enchantments
    // ─────────────────────────────────────────────────────────────────────────

    @GameTest
    public void vinewrath_axe_has_sharpness(GameTestHelper helper) {
        ItemStack stack = VinewrathAxe.INSTANCE.createItem();
        var enchReg = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        int level = EnchantmentHelper.getItemEnchantmentLevel(
                enchReg.getOrThrow(Enchantments.SHARPNESS), stack);
        if (level != 5) {
            helper.fail("createItem() must have Sharpness level 5, got: " + level);
        }
        helper.succeed();
    }

    @GameTest
    public void vinewrath_axe_has_efficiency(GameTestHelper helper) {
        ItemStack stack = VinewrathAxe.INSTANCE.createItem();
        var enchReg = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        int level = EnchantmentHelper.getItemEnchantmentLevel(
                enchReg.getOrThrow(Enchantments.EFFICIENCY), stack);
        if (level != 5) {
            helper.fail("createItem() must have Efficiency level 5, got: " + level);
        }
        helper.succeed();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Shield-disable ability — smoke test
    //
    // Full end-to-end testing of the shield-disable trigger requires two real
    // ServerPlayer instances in an active game world, which is beyond the scope
    // of a GameTest unit suite. Instead we verify that:
    //   (a) onAttack does not throw when the victim is not blocking, and
    //   (b) onAttack does not throw when victim is null-guarded (non-ServerPlayer).
    //
    // Manual integration testing is required for the live shield-disable path.
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * onAttack with a non-ServerPlayer victim must return immediately without error.
     * This covers the fast-return path and confirms no NPE is thrown.
     */
    @GameTest
    public void vinewrath_axe_on_attack_non_player_victim_no_throw(GameTestHelper helper) {
        // Use a Zombie as a non-ServerPlayer LivingEntity victim.
        LivingEntity zombie = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.ZOMBIE,
                new net.minecraft.core.BlockPos(1, 1, 1));

        // Calling onAttack with a non-player victim must not throw.
        try {
            // We cannot construct a real ServerPlayer in a GameTest without a full connection.
            // Verify only the non-player guard by passing null for attacker —
            // the guard `!(victim instanceof ServerPlayer)` returns before using attacker.
            VinewrathAxe.INSTANCE.onAttack(null, zombie, new Order.DamageContext(
                    helper.getLevel().damageSources().generic(), 10.0f));
        } catch (Exception e) {
            helper.fail("onAttack() must not throw for a non-ServerPlayer victim, got: " + e);
        }

        helper.succeed();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // WeaponRegistry integration
    // ─────────────────────────────────────────────────────────────────────────

    @GameTest
    public void vinewrath_axe_registry_for_flora(GameTestHelper helper) {
        try {
            WeaponRegistry.register(new VinewrathAxe());
        } catch (IllegalArgumentException e) {
            // Already registered during onInitialize — acceptable.
        }
        MythicWeapon found = WeaponRegistry.getForOrder("flora");
        if (found == null) {
            helper.fail("WeaponRegistry.getForOrder(\"flora\") must not be null after registration");
        }
        helper.succeed();
    }

    @GameTest
    public void vinewrath_axe_is_mythical_weapon(GameTestHelper helper) {
        try {
            WeaponRegistry.register(new VinewrathAxe());
        } catch (IllegalArgumentException e) {
            // Already registered — acceptable.
        }
        ItemStack stack = VinewrathAxe.INSTANCE.createItem();
        if (!WeaponRegistry.isMythicalWeapon(stack)) {
            helper.fail("WeaponRegistry.isMythicalWeapon() must return true for the Vinewrath Axe");
        }
        helper.succeed();
    }
}
