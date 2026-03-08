package freq.ascension.test.weapons;

import java.util.List;
import java.util.UUID;

import freq.ascension.animation.HellfireBeam;
import freq.ascension.orders.Nether;
import freq.ascension.registry.WeaponRegistry;
import freq.ascension.weapons.HellfireCrossbow;
import freq.ascension.weapons.MythicWeapon;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Unit;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * GameTest suite for the {@link HellfireCrossbow} mythical weapon.
 *
 * <p>Pure-logic tests (firework counter, damage formula) operate directly on static helpers and
 * the package-visible counter map — no live {@link net.minecraft.server.level.ServerPlayer} is
 * required.
 *
 * <p>Item-inspection and enchantment tests call {@link HellfireCrossbow#createItem()} through the
 * GameTest server context, where {@code Ascension.getServer()} is already non-null.
 */
public class WeaponHellfireCrossbowTests {

    // ─── Identity ─────────────────────────────────────────────────────────────

    @GameTest
    public void hellfire_crossbow_weapon_id(GameTestHelper helper) {
        if (!"hellfire_crossbow".equals(HellfireCrossbow.INSTANCE.getWeaponId())) {
            helper.fail("getWeaponId() must return \"hellfire_crossbow\", got: "
                    + HellfireCrossbow.INSTANCE.getWeaponId());
        }
        helper.succeed();
    }

    @GameTest
    public void hellfire_crossbow_base_item(GameTestHelper helper) {
        if (HellfireCrossbow.INSTANCE.getBaseItem() != Items.CROSSBOW) {
            helper.fail("getBaseItem() must return Items.CROSSBOW, got: "
                    + HellfireCrossbow.INSTANCE.getBaseItem());
        }
        helper.succeed();
    }

    @GameTest
    public void hellfire_crossbow_parent_order(GameTestHelper helper) {
        if (!"nether".equals(HellfireCrossbow.INSTANCE.getParentOrder().getOrderName())) {
            helper.fail("getParentOrder().getOrderName() must return \"nether\", got: "
                    + HellfireCrossbow.INSTANCE.getParentOrder().getOrderName());
        }
        helper.succeed();
    }

    // ─── Item creation ────────────────────────────────────────────────────────

    @GameTest
    public void hellfire_crossbow_create_item_not_empty(GameTestHelper helper) {
        ItemStack stack = HellfireCrossbow.INSTANCE.createItem();
        if (stack == null || stack.isEmpty()) {
            helper.fail("createItem() must return a non-empty ItemStack");
        }
        helper.succeed();
    }

    @GameTest
    public void hellfire_crossbow_has_custom_model_data(GameTestHelper helper) {
        ItemStack stack = HellfireCrossbow.INSTANCE.buildBaseItem();
        CustomModelData cmd = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        if (cmd == null) {
            helper.fail("buildBaseItem() must set DataComponents.CUSTOM_MODEL_DATA");
        }
        List<String> strings = cmd.strings();
        if (!strings.contains("hellfire_crossbow")) {
            helper.fail("CustomModelData strings must contain \"hellfire_crossbow\" but got: " + strings);
        }
        if (strings.size() != 1) {
            helper.fail("CustomModelData strings must have exactly 1 entry but had: " + strings.size());
        }
        helper.succeed();
    }

    @GameTest
    public void hellfire_crossbow_is_unbreakable(GameTestHelper helper) {
        ItemStack stack = HellfireCrossbow.INSTANCE.buildBaseItem();
        Unit unbreakable = stack.get(DataComponents.UNBREAKABLE);
        if (unbreakable == null) {
            helper.fail("buildBaseItem() must set DataComponents.UNBREAKABLE");
        }
        helper.succeed();
    }

    @GameTest
    public void hellfire_crossbow_has_custom_name(GameTestHelper helper) {
        ItemStack stack = HellfireCrossbow.INSTANCE.buildBaseItem();
        if (stack.get(DataComponents.CUSTOM_NAME) == null) {
            helper.fail("buildBaseItem() must set DataComponents.CUSTOM_NAME");
        }
        helper.succeed();
    }

    // ─── isItem contract ──────────────────────────────────────────────────────

    @GameTest
    public void hellfire_crossbow_is_item_detects_own(GameTestHelper helper) {
        ItemStack own = HellfireCrossbow.INSTANCE.buildBaseItem();
        if (!HellfireCrossbow.INSTANCE.isItem(own)) {
            helper.fail("isItem() must return true for the weapon's own buildBaseItem() stack");
        }
        helper.succeed();
    }

    @GameTest
    public void hellfire_crossbow_is_item_rejects_plain_crossbow(GameTestHelper helper) {
        ItemStack plain = new ItemStack(Items.CROSSBOW);
        if (HellfireCrossbow.INSTANCE.isItem(plain)) {
            helper.fail("isItem() must return false for a plain crossbow with no CustomModelData");
        }
        helper.succeed();
    }

    // ─── Enchantments ─────────────────────────────────────────────────────────

    @GameTest
    public void hellfire_crossbow_has_quick_charge_2(GameTestHelper helper) {
        ItemStack stack = HellfireCrossbow.INSTANCE.createItem();
        ItemEnchantments enc = stack.get(DataComponents.ENCHANTMENTS);
        if (enc == null) {
            helper.fail("createItem() must apply enchantments (enchantments component is null)");
        }
        var enchReg = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        int level = enc.getLevel(enchReg.getOrThrow(Enchantments.QUICK_CHARGE));
        if (level != 2) {
            helper.fail("Expected Quick Charge 2, got level " + level);
        }
        helper.succeed();
    }

    @GameTest
    public void hellfire_crossbow_has_piercing_4(GameTestHelper helper) {
        ItemStack stack = HellfireCrossbow.INSTANCE.createItem();
        ItemEnchantments enc = stack.get(DataComponents.ENCHANTMENTS);
        if (enc == null) {
            helper.fail("createItem() must apply enchantments (enchantments component is null)");
        }
        var enchReg = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        int level = enc.getLevel(enchReg.getOrThrow(Enchantments.PIERCING));
        if (level != 4) {
            helper.fail("Expected Piercing 4, got level " + level);
        }
        helper.succeed();
    }

    // ─── Firework counter logic ───────────────────────────────────────────────

    /**
     * With count=2 already in the map, the next increment produces count=3.
     * Since 3 % 3 == 0, the beam trigger must fire.
     */
    @GameTest
    public void hellfire_crossbow_counter_triggers_on_third(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        HellfireCrossbow.FIREWORK_COUNTER.put(id, 2);
        boolean triggered = HellfireCrossbow.incrementAndCheck(id);
        HellfireCrossbow.FIREWORK_COUNTER.remove(id);
        if (!triggered) {
            helper.fail("incrementAndCheck must return true when count goes 2→3 (3 % 3 == 0)");
        }
        helper.succeed();
    }

    /**
     * First shot: counter goes 0→1; 1 % 3 ≠ 0, so no trigger.
     */
    @GameTest
    public void hellfire_crossbow_counter_no_trigger_on_first(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        HellfireCrossbow.FIREWORK_COUNTER.remove(id);
        boolean triggered = HellfireCrossbow.incrementAndCheck(id);
        HellfireCrossbow.FIREWORK_COUNTER.remove(id);
        if (triggered) {
            helper.fail("incrementAndCheck must return false when count goes 0→1");
        }
        helper.succeed();
    }

    /**
     * Second shot: counter goes 1→2; 2 % 3 ≠ 0, so no trigger.
     */
    @GameTest
    public void hellfire_crossbow_counter_no_trigger_on_second(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        HellfireCrossbow.FIREWORK_COUNTER.put(id, 1);
        boolean triggered = HellfireCrossbow.incrementAndCheck(id);
        HellfireCrossbow.FIREWORK_COUNTER.remove(id);
        if (triggered) {
            helper.fail("incrementAndCheck must return false when count goes 1→2");
        }
        helper.succeed();
    }

    // ─── Damage formula ───────────────────────────────────────────────────────

    /** At dist=5 (≤ 10), damage = 40 % of maxHp. */
    @GameTest
    public void hellfire_crossbow_damage_at_five_blocks(GameTestHelper helper) {
        double result = HellfireBeam.calculateDamage(5.0, 100.0);
        if (Math.abs(result - 40.0) > 0.001) {
            helper.fail("calculateDamage(5, 100) must be 40.0, got " + result);
        }
        helper.succeed();
    }

    /** At dist=10 (= threshold), damage = 40 % of maxHp. */
    @GameTest
    public void hellfire_crossbow_damage_at_ten_blocks(GameTestHelper helper) {
        double result = HellfireBeam.calculateDamage(10.0, 100.0);
        if (Math.abs(result - 40.0) > 0.001) {
            helper.fail("calculateDamage(10, 100) must be 40.0, got " + result);
        }
        helper.succeed();
    }

    /**
     * At dist=30: {@code 40% × (60 − 30) / 50 = 40% × 0.6 = 24%} of 100 HP = 24.
     */
    @GameTest
    public void hellfire_crossbow_damage_at_thirty_blocks(GameTestHelper helper) {
        double result = HellfireBeam.calculateDamage(30.0, 100.0);
        if (Math.abs(result - 24.0) > 0.001) {
            helper.fail("calculateDamage(30, 100) must be 24.0 (40% × 0.6), got " + result);
        }
        helper.succeed();
    }

    /** At dist=60 (= max range), damage = 0. */
    @GameTest
    public void hellfire_crossbow_damage_at_sixty_blocks(GameTestHelper helper) {
        double result = HellfireBeam.calculateDamage(60.0, 100.0);
        if (Math.abs(result - 0.0) > 0.001) {
            helper.fail("calculateDamage(60, 100) must be 0.0, got " + result);
        }
        helper.succeed();
    }

    /** Beyond max range the damage must be clamped to 0. */
    @GameTest
    public void hellfire_crossbow_damage_beyond_sixty_blocks(GameTestHelper helper) {
        double result = HellfireBeam.calculateDamage(65.0, 100.0);
        if (Math.abs(result - 0.0) > 0.001) {
            helper.fail("calculateDamage(65, 100) must be 0.0 (beyond max range), got " + result);
        }
        helper.succeed();
    }

    // ─── WeaponRegistry integration ───────────────────────────────────────────

    @GameTest
    public void hellfire_crossbow_registry_for_nether(GameTestHelper helper) {
        try {
            WeaponRegistry.register(HellfireCrossbow.INSTANCE);
        } catch (IllegalArgumentException e) {
            // Already registered from Ascension.onInitialize() or a prior test run — acceptable.
        }

        MythicWeapon found = WeaponRegistry.getForOrder("nether");
        if (found == null) {
            helper.fail("WeaponRegistry.getForOrder(\"nether\") must return the HellfireCrossbow after registration");
        }
        if (!(found instanceof HellfireCrossbow)) {
            helper.fail("WeaponRegistry.getForOrder(\"nether\") returned " + found.getClass().getSimpleName()
                    + " but expected HellfireCrossbow");
        }
        helper.succeed();
    }
}
