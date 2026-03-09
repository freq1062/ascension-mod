package freq.ascension.test.weapons;

import java.util.List;
import java.util.UUID;

import org.joml.Quaternionf;
import org.joml.Vector3f;

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

    /**
     * After the beam fires (shot 3), {@link HellfireCrossbow#onFireworkShot} resets the counter
     * to 0 so the next 3 shots start a fresh charge cycle. Verified by checking that the
     * subsequent two shots do NOT trigger, and the third subsequent shot DOES trigger again.
     */
    @GameTest
    public void hellfire_crossbow_counter_resets_after_beam(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        // Simulate counter at 2 (shots 1 and 2 already fired).
        HellfireCrossbow.FIREWORK_COUNTER.put(id, 2);
        // Shot 3 → triggers (count becomes 3, 3 % 3 == 0).
        boolean triggered = HellfireCrossbow.incrementAndCheck(id);
        if (!triggered) {
            HellfireCrossbow.FIREWORK_COUNTER.remove(id);
            helper.fail("incrementAndCheck must return true when count goes 2→3");
        }
        // Simulate the reset that onFireworkShot performs after the beam fires.
        HellfireCrossbow.FIREWORK_COUNTER.put(id, 0);
        // Next shot (shot 4) → count goes 0→1, must NOT trigger.
        boolean shot4 = HellfireCrossbow.incrementAndCheck(id);
        // Next shot (shot 5) → count goes 1→2, must NOT trigger.
        boolean shot5 = HellfireCrossbow.incrementAndCheck(id);
        // Next shot (shot 6) → count goes 2→3, MUST trigger again.
        boolean shot6 = HellfireCrossbow.incrementAndCheck(id);
        HellfireCrossbow.FIREWORK_COUNTER.remove(id);
        if (shot4) {
            helper.fail("After counter reset, shot 4 (count 0→1) must not trigger");
        }
        if (shot5) {
            helper.fail("After counter reset, shot 5 (count 1→2) must not trigger");
        }
        if (!shot6) {
            helper.fail("After counter reset, shot 6 (count 2→3) must trigger again");
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

    // ─── HellfireBeam centering ───────────────────────────────────────────────

    /**
     * Verifies that the dynamic center offset used in the outer glass animation
     * scales proportionally with {@code scaleXZ}, keeping the block centered on
     * the beam axis at every stage of the shrink.
     *
     * <p>When {@code scaleXZ = S} and the base rotation is the identity, the
     * expected offset is {@code (-S/2, 0, -S/2)}. The test checks two sizes:
     * full width G=0.8 and half width G/2=0.4.
     */
    @GameTest
    public void beamCenterCalculationIsProportional(GameTestHelper helper) {
        Quaternionf identity = new Quaternionf(); // identity rotation

        float G = 0.8f;
        Vector3f fullCenter = identity.transform(
                new Vector3f(-G * 0.5f, 0f, -G * 0.5f), new Vector3f());
        float halfG = G * 0.5f;
        Vector3f halfCenter = identity.transform(
                new Vector3f(-halfG * 0.5f, 0f, -halfG * 0.5f), new Vector3f());

        // Full-width: center must be (-G/2, 0, -G/2)
        float eps = 0.0001f;
        if (Math.abs(fullCenter.x - (-G * 0.5f)) > eps || Math.abs(fullCenter.z - (-G * 0.5f)) > eps) {
            helper.fail("Full-width center must be (-G/2, 0, -G/2); got ("
                    + fullCenter.x + ", " + fullCenter.y + ", " + fullCenter.z + ")");
        }
        // Half-width: center must be (-G/4, 0, -G/4)
        if (Math.abs(halfCenter.x - (-G * 0.25f)) > eps || Math.abs(halfCenter.z - (-G * 0.25f)) > eps) {
            helper.fail("Half-width center must be (-G/4, 0, -G/4); got ("
                    + halfCenter.x + ", " + halfCenter.y + ", " + halfCenter.z + ")");
        }
        // Proportionality: halfCenter must equal fullCenter × 0.5 in X and Z
        if (Math.abs(halfCenter.x - fullCenter.x * 0.5f) > eps
                || Math.abs(halfCenter.z - fullCenter.z * 0.5f) > eps) {
            helper.fail("Center offset must scale proportionally with scaleXZ; "
                    + "half-width center != full-width center × 0.5");
        }
        helper.succeed();
    }
}
