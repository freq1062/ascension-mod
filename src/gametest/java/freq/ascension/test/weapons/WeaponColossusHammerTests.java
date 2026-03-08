package freq.ascension.test.weapons;

import freq.ascension.orders.Earth;
import freq.ascension.registry.WeaponRegistry;
import freq.ascension.weapons.ColossusHammer;
import freq.ascension.weapons.MythicWeapon;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Unit;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * GameTest suite for the {@link ColossusHammer} mythical weapon.
 *
 * <p>Verifies identity, item creation, component data, enchantment presence,
 * and WeaponRegistry integration.
 */
public class WeaponColossusHammerTests {

    // ─────────────────────────────────────────────────────────────────────────
    // Identity
    // ─────────────────────────────────────────────────────────────────────────

    @GameTest
    public void colossus_hammer_weapon_id(GameTestHelper helper) {
        if (!"colossus_hammer".equals(ColossusHammer.INSTANCE.getWeaponId())) {
            helper.fail("getWeaponId() must return \"colossus_hammer\"");
        }
        helper.succeed();
    }

    @GameTest
    public void colossus_hammer_base_item(GameTestHelper helper) {
        if (ColossusHammer.INSTANCE.getBaseItem() != Items.MACE) {
            helper.fail("getBaseItem() must return Items.MACE");
        }
        helper.succeed();
    }

    @GameTest
    public void colossus_hammer_parent_order(GameTestHelper helper) {
        if (!"earth".equals(ColossusHammer.INSTANCE.getParentOrder().getOrderName())) {
            helper.fail("getParentOrder().getOrderName() must return \"earth\"");
        }
        helper.succeed();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Item creation
    // ─────────────────────────────────────────────────────────────────────────

    @GameTest
    public void colossus_hammer_create_item_not_empty(GameTestHelper helper) {
        ItemStack stack = ColossusHammer.INSTANCE.createItem();
        if (stack == null || stack.isEmpty()) {
            helper.fail("createItem() must return a non-empty ItemStack");
        }
        helper.succeed();
    }

    @GameTest
    public void colossus_hammer_has_custom_model_data(GameTestHelper helper) {
        ItemStack stack = ColossusHammer.INSTANCE.createItem();
        CustomModelData cmd = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        if (cmd == null || !cmd.strings().contains("colossus_hammer")) {
            helper.fail("createItem() must have CustomModelData string \"colossus_hammer\", got: "
                    + (cmd == null ? "null" : cmd.strings()));
        }
        helper.succeed();
    }

    @GameTest
    public void colossus_hammer_is_unbreakable(GameTestHelper helper) {
        ItemStack stack = ColossusHammer.INSTANCE.createItem();
        Unit unbreakable = stack.get(DataComponents.UNBREAKABLE);
        if (unbreakable == null) {
            helper.fail("createItem() must set DataComponents.UNBREAKABLE");
        }
        helper.succeed();
    }

    @GameTest
    public void colossus_hammer_has_custom_name(GameTestHelper helper) {
        ItemStack stack = ColossusHammer.INSTANCE.createItem();
        if (stack.get(DataComponents.CUSTOM_NAME) == null) {
            helper.fail("createItem() must set DataComponents.CUSTOM_NAME");
        }
        helper.succeed();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // isItem detection
    // ─────────────────────────────────────────────────────────────────────────

    @GameTest
    public void colossus_hammer_is_item_detects_own(GameTestHelper helper) {
        ItemStack stack = ColossusHammer.INSTANCE.createItem();
        if (!ColossusHammer.INSTANCE.isItem(stack)) {
            helper.fail("isItem() must return true for createItem() result");
        }
        helper.succeed();
    }

    @GameTest
    public void colossus_hammer_is_item_rejects_plain_mace(GameTestHelper helper) {
        if (ColossusHammer.INSTANCE.isItem(new ItemStack(Items.MACE))) {
            helper.fail("isItem() must return false for a plain ItemStack(Items.MACE)");
        }
        helper.succeed();
    }

    @GameTest
    public void colossus_hammer_is_item_rejects_empty(GameTestHelper helper) {
        if (ColossusHammer.INSTANCE.isItem(ItemStack.EMPTY)) {
            helper.fail("isItem() must return false for ItemStack.EMPTY");
        }
        helper.succeed();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Enchantments
    // ─────────────────────────────────────────────────────────────────────────

    @GameTest
    public void colossus_hammer_has_breach_enchant(GameTestHelper helper) {
        ItemStack stack = ColossusHammer.INSTANCE.createItem();
        var enchReg = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        int level = EnchantmentHelper.getItemEnchantmentLevel(
                enchReg.getOrThrow(Enchantments.BREACH), stack);
        if (level != 2) {
            helper.fail("createItem() must have Breach level 2, got: " + level);
        }
        helper.succeed();
    }

    @GameTest
    public void colossus_hammer_has_wind_burst_enchant(GameTestHelper helper) {
        ItemStack stack = ColossusHammer.INSTANCE.createItem();
        var enchReg = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        int level = EnchantmentHelper.getItemEnchantmentLevel(
                enchReg.getOrThrow(Enchantments.WIND_BURST), stack);
        if (level != 1) {
            helper.fail("createItem() must have Wind Burst level 1, got: " + level);
        }
        helper.succeed();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // WeaponRegistry integration
    // ─────────────────────────────────────────────────────────────────────────

    @GameTest
    public void colossus_hammer_registry_returns_for_earth(GameTestHelper helper) {
        try {
            WeaponRegistry.register(new ColossusHammer());
        } catch (IllegalArgumentException e) {
            // Already registered during onInitialize — acceptable
        }
        MythicWeapon found = WeaponRegistry.getForOrder("earth");
        if (found == null) {
            helper.fail("WeaponRegistry.getForOrder(\"earth\") must not be null after registration");
        }
        helper.succeed();
    }

    @GameTest
    public void colossus_hammer_is_mythical_weapon(GameTestHelper helper) {
        try {
            WeaponRegistry.register(new ColossusHammer());
        } catch (IllegalArgumentException e) {
            // Already registered — acceptable
        }
        ItemStack stack = ColossusHammer.INSTANCE.createItem();
        if (!WeaponRegistry.isMythicalWeapon(stack)) {
            helper.fail("WeaponRegistry.isMythicalWeapon() must return true for the Colossus Hammer");
        }
        helper.succeed();
    }
}
