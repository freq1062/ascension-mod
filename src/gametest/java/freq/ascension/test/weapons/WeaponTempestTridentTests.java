package freq.ascension.test.weapons;

import java.util.List;
import java.util.UUID;

import freq.ascension.orders.Ocean;
import freq.ascension.registry.WeaponRegistry;
import freq.ascension.weapons.MythicWeapon;
import freq.ascension.weapons.TempestTrident;
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
 * GameTest suite for the {@link TempestTrident} mythical weapon.
 *
 * <p>Verifies identity, item creation, component data, enchantment presence, custom attribute
 * modifiers, mode detection, isItem prefix-matching, hit counter logic, and WeaponRegistry
 * integration.
 */
public class WeaponTempestTridentTests {

    // ─────────────────────────────────────────────────────────────────────────
    // Identity
    // ─────────────────────────────────────────────────────────────────────────

    @GameTest
    public void tempest_trident_weapon_id(GameTestHelper helper) {
        if (!"tempest_trident".equals(TempestTrident.INSTANCE.getWeaponId())) {
            helper.fail("getWeaponId() must return \"tempest_trident\"");
        }
        helper.succeed();
    }

    @GameTest
    public void tempest_trident_base_item(GameTestHelper helper) {
        if (TempestTrident.INSTANCE.getBaseItem() != Items.TRIDENT) {
            helper.fail("getBaseItem() must return Items.TRIDENT");
        }
        helper.succeed();
    }

    @GameTest
    public void tempest_trident_parent_order(GameTestHelper helper) {
        if (!"ocean".equals(TempestTrident.INSTANCE.getParentOrder().getOrderName())) {
            helper.fail("getParentOrder().getOrderName() must return \"ocean\"");
        }
        helper.succeed();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Item creation
    // ─────────────────────────────────────────────────────────────────────────

    @GameTest
    public void tempest_trident_create_item_not_empty(GameTestHelper helper) {
        ItemStack stack = TempestTrident.INSTANCE.createItem();
        if (stack == null || stack.isEmpty()) {
            helper.fail("createItem() must return a non-empty ItemStack");
        }
        helper.succeed();
    }

    @GameTest
    public void tempest_trident_default_mode_is_loyalty(GameTestHelper helper) {
        ItemStack stack = TempestTrident.INSTANCE.createItem();
        var enchReg = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        int loyaltyLevel = EnchantmentHelper.getItemEnchantmentLevel(
                enchReg.getOrThrow(Enchantments.LOYALTY), stack);
        int riptideLevel = EnchantmentHelper.getItemEnchantmentLevel(
                enchReg.getOrThrow(Enchantments.RIPTIDE), stack);
        if (loyaltyLevel != 3) {
            helper.fail("Default mode must have Loyalty 3, got " + loyaltyLevel);
        }
        if (riptideLevel != 0) {
            helper.fail("Default mode must NOT have Riptide, got level " + riptideLevel);
        }
        helper.succeed();
    }

    @GameTest
    public void tempest_trident_has_custom_model_data_loyalty(GameTestHelper helper) {
        ItemStack stack = TempestTrident.INSTANCE.createItem();
        CustomModelData cmd = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        if (cmd == null || !cmd.strings().contains(TempestTrident.MODEL_LOYALTY)) {
            helper.fail("createItem() must have CustomModelData string \""
                    + TempestTrident.MODEL_LOYALTY + "\", got: "
                    + (cmd == null ? "null" : cmd.strings()));
        }
        helper.succeed();
    }

    @GameTest
    public void tempest_trident_is_unbreakable(GameTestHelper helper) {
        ItemStack stack = TempestTrident.INSTANCE.createItem();
        Unit unbreakable = stack.get(DataComponents.UNBREAKABLE);
        if (unbreakable == null) {
            helper.fail("createItem() must set DataComponents.UNBREAKABLE");
        }
        helper.succeed();
    }

    @GameTest
    public void tempest_trident_has_custom_name(GameTestHelper helper) {
        ItemStack stack = TempestTrident.INSTANCE.createItem();
        if (stack.get(DataComponents.CUSTOM_NAME) == null) {
            helper.fail("createItem() must set DataComponents.CUSTOM_NAME");
        }
        helper.succeed();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Enchantments
    // ─────────────────────────────────────────────────────────────────────────

    @GameTest
    public void tempest_trident_has_sharpness_5(GameTestHelper helper) {
        ItemStack stack = TempestTrident.INSTANCE.createItem();
        var enchReg = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        int level = EnchantmentHelper.getItemEnchantmentLevel(
                enchReg.getOrThrow(Enchantments.SHARPNESS), stack);
        if (level != 5) {
            helper.fail("createItem() must have Sharpness 5, got: " + level);
        }
        helper.succeed();
    }

    @GameTest
    public void tempest_trident_has_impaling_5(GameTestHelper helper) {
        ItemStack stack = TempestTrident.INSTANCE.createItem();
        var enchReg = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        int level = EnchantmentHelper.getItemEnchantmentLevel(
                enchReg.getOrThrow(Enchantments.IMPALING), stack);
        if (level != 5) {
            helper.fail("createItem() must have Impaling 5, got: " + level);
        }
        helper.succeed();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // isItem — prefix-based detection
    // ─────────────────────────────────────────────────────────────────────────

    @GameTest
    public void tempest_trident_is_item_detects_loyalty_mode(GameTestHelper helper) {
        ItemStack stack = new ItemStack(Items.TRIDENT);
        stack.set(DataComponents.CUSTOM_MODEL_DATA,
                new CustomModelData(List.of(), List.of(), List.of(TempestTrident.MODEL_LOYALTY), List.of()));
        if (!TempestTrident.INSTANCE.isItem(stack)) {
            helper.fail("isItem() must return true for a stack with model data \""
                    + TempestTrident.MODEL_LOYALTY + "\"");
        }
        helper.succeed();
    }

    @GameTest
    public void tempest_trident_is_item_detects_riptide_mode(GameTestHelper helper) {
        ItemStack stack = new ItemStack(Items.TRIDENT);
        stack.set(DataComponents.CUSTOM_MODEL_DATA,
                new CustomModelData(List.of(), List.of(), List.of(TempestTrident.MODEL_RIPTIDE), List.of()));
        if (!TempestTrident.INSTANCE.isItem(stack)) {
            helper.fail("isItem() must return true for a stack with model data \""
                    + TempestTrident.MODEL_RIPTIDE + "\"");
        }
        helper.succeed();
    }

    @GameTest
    public void tempest_trident_is_item_rejects_plain_trident(GameTestHelper helper) {
        if (TempestTrident.INSTANCE.isItem(new ItemStack(Items.TRIDENT))) {
            helper.fail("isItem() must return false for a plain ItemStack(Items.TRIDENT)");
        }
        helper.succeed();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Hit counter logic
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Sets the counter to 2 for a test UUID, processes the 3rd hit, and verifies the counter
     * resets to 0 (trigger fired, counter is not cumulative).
     */
    @GameTest
    public void tempest_trident_hit_counter_triggers_on_third_hit(GameTestHelper helper) {
        UUID testId = UUID.randomUUID();
        TempestTrident.hitCounters.put(testId, 2); // simulate 2 previous hits

        boolean triggered = TempestTrident.processHitCounter(testId);
        int counterAfter = TempestTrident.hitCounters.getOrDefault(testId, -1);

        TempestTrident.hitCounters.remove(testId); // cleanup

        if (!triggered) {
            helper.fail("processHitCounter() must return true on the 3rd hit (count was 2, increment → 3)");
        }
        if (counterAfter != 0) {
            helper.fail("Counter must reset to 0 after trigger, got: " + counterAfter);
        }
        helper.succeed();
    }

    /**
     * Processes the 2nd hit (counter was 1), verifies no trigger and counter is 2.
     */
    @GameTest
    public void tempest_trident_hit_counter_no_trigger_on_second_hit(GameTestHelper helper) {
        UUID testId = UUID.randomUUID();
        TempestTrident.hitCounters.put(testId, 1); // simulate 1 previous hit

        boolean triggered = TempestTrident.processHitCounter(testId);
        int counterAfter = TempestTrident.hitCounters.getOrDefault(testId, -1);

        TempestTrident.hitCounters.remove(testId); // cleanup

        if (triggered) {
            helper.fail("processHitCounter() must return false on the 2nd hit (count was 1, increment → 2)");
        }
        if (counterAfter != 2) {
            helper.fail("Counter must be 2 after 2nd hit, got: " + counterAfter);
        }
        helper.succeed();
    }

    /**
     * Verifies that a TempestTrident in riptide mode is NOT in loyalty mode, meaning
     * the hit counter logic in onProjectileHit would return early without incrementing.
     *
     * <p>Tests the mode guard ({@code isLoyaltyModeStack}) rather than the full
     * onProjectileHit path (which would require a live ServerPlayer).
     */
    @GameTest
    public void tempest_trident_hit_counter_not_in_riptide_mode(GameTestHelper helper) {
        // Build a TempestTrident stack in riptide mode
        ItemStack riptideStack = new ItemStack(Items.TRIDENT);
        riptideStack.set(DataComponents.CUSTOM_MODEL_DATA,
                new CustomModelData(List.of(), List.of(), List.of(TempestTrident.MODEL_RIPTIDE), List.of()));

        // isLoyaltyModeStack must return false for riptide mode — onProjectileHit guards on this
        if (TempestTrident.isLoyaltyModeStack(riptideStack)) {
            helper.fail("isLoyaltyModeStack() must return false for a stack in riptide mode; "
                    + "onProjectileHit would otherwise incorrectly increment the hit counter");
        }

        // Also verify loyalty mode stack returns true
        ItemStack loyaltyStack = new ItemStack(Items.TRIDENT);
        loyaltyStack.set(DataComponents.CUSTOM_MODEL_DATA,
                new CustomModelData(List.of(), List.of(), List.of(TempestTrident.MODEL_LOYALTY), List.of()));
        if (!TempestTrident.isLoyaltyModeStack(loyaltyStack)) {
            helper.fail("isLoyaltyModeStack() must return true for a stack in loyalty mode");
        }

        helper.succeed();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // WeaponRegistry integration
    // ─────────────────────────────────────────────────────────────────────────

    @GameTest
    public void tempest_trident_registry_for_ocean(GameTestHelper helper) {
        try {
            WeaponRegistry.register(TempestTrident.INSTANCE);
        } catch (IllegalArgumentException e) {
            // Already registered during Ascension.onInitialize() — acceptable
        }
        MythicWeapon found = WeaponRegistry.getForOrder("ocean");
        if (found == null) {
            helper.fail("WeaponRegistry.getForOrder(\"ocean\") must not be null after registration");
        }
        helper.succeed();
    }
}
