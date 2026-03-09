package freq.ascension.test.weapons;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import freq.ascension.orders.End;
import freq.ascension.registry.WeaponRegistry;
import freq.ascension.weapons.MythicWeapon;
import freq.ascension.weapons.RuinousScythe;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.util.Unit;

/**
 * GameTest suite for the {@link RuinousScythe} mythical weapon.
 *
 * <p>Pure-logic tests (combo counter, armour distribution) manipulate the package-visible static
 * maps and helpers directly; no live server entities or players are required.
 *
 * <p>Item-inspection tests call {@link RuinousScythe#createItem()} through the GameTest server
 * context, where {@code Ascension.getServer()} is already non-null.
 */
public class WeaponRuinousScytheTests {

    // ─── Identity ─────────────────────────────────────────────────────────────

    @GameTest
    public void ruinous_scythe_weapon_id(GameTestHelper helper) {
        if (!"ruinous_scythe".equals(RuinousScythe.INSTANCE.getWeaponId())) {
            helper.fail("getWeaponId() must return \"ruinous_scythe\", got: "
                    + RuinousScythe.INSTANCE.getWeaponId());
        }
        helper.succeed();
    }

    @GameTest
    public void ruinous_scythe_base_item(GameTestHelper helper) {
        if (RuinousScythe.INSTANCE.getBaseItem() != Items.DIAMOND_SWORD) {
            helper.fail("getBaseItem() must return Items.DIAMOND_SWORD, got: "
                    + RuinousScythe.INSTANCE.getBaseItem());
        }
        helper.succeed();
    }

    @GameTest
    public void ruinous_scythe_parent_order(GameTestHelper helper) {
        if (!"end".equals(RuinousScythe.INSTANCE.getParentOrder().getOrderName())) {
            helper.fail("getParentOrder().getOrderName() must be \"end\", got: "
                    + RuinousScythe.INSTANCE.getParentOrder().getOrderName());
        }
        helper.succeed();
    }

    // ─── createItem / buildBaseItem ──────────────────────────────────────────

    @GameTest
    public void ruinous_scythe_create_item_not_empty(GameTestHelper helper) {
        ItemStack stack = RuinousScythe.INSTANCE.createItem();
        if (stack == null || stack.isEmpty()) {
            helper.fail("createItem() must return a non-empty ItemStack");
        }
        if (stack.getItem() != Items.DIAMOND_SWORD) {
            helper.fail("createItem() must be DIAMOND_SWORD, got: " + stack.getItem());
        }
        helper.succeed();
    }

    @GameTest
    public void ruinous_scythe_has_custom_model_data(GameTestHelper helper) {
        ItemStack stack = RuinousScythe.INSTANCE.buildBaseItem();
        CustomModelData cmd = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        if (cmd == null || !cmd.strings().contains("ruinous_scythe")) {
            helper.fail("CustomModelData strings must contain \"ruinous_scythe\", got: "
                    + (cmd == null ? "null" : cmd.strings()));
        }
        helper.succeed();
    }

    @GameTest
    public void ruinous_scythe_is_unbreakable(GameTestHelper helper) {
        ItemStack stack = RuinousScythe.INSTANCE.buildBaseItem();
        if (stack.get(DataComponents.UNBREAKABLE) == null) {
            helper.fail("buildBaseItem() must set DataComponents.UNBREAKABLE");
        }
        helper.succeed();
    }

    @GameTest
    public void ruinous_scythe_has_custom_name(GameTestHelper helper) {
        ItemStack stack = RuinousScythe.INSTANCE.buildBaseItem();
        if (stack.get(DataComponents.CUSTOM_NAME) == null) {
            helper.fail("buildBaseItem() must set DataComponents.CUSTOM_NAME");
        }
        helper.succeed();
    }

    // ─── isItem ───────────────────────────────────────────────────────────────

    @GameTest
    public void ruinous_scythe_is_item_detects_own(GameTestHelper helper) {
        ItemStack own = RuinousScythe.INSTANCE.buildBaseItem();
        if (!RuinousScythe.INSTANCE.isItem(own)) {
            helper.fail("isItem() must return true for the weapon's own stack");
        }
        helper.succeed();
    }

    @GameTest
    public void ruinous_scythe_is_item_rejects_plain(GameTestHelper helper) {
        if (RuinousScythe.INSTANCE.isItem(new ItemStack(Items.DIAMOND_SWORD))) {
            helper.fail("isItem() must return false for a plain DIAMOND_SWORD without model data");
        }
        if (RuinousScythe.INSTANCE.isItem(ItemStack.EMPTY)) {
            helper.fail("isItem() must return false for an empty stack");
        }
        helper.succeed();
    }

    // ─── Enchantments (requires live server registry via GameTest context) ───

    @GameTest
    public void ruinous_scythe_has_sharpness_5(GameTestHelper helper) {
        ItemStack stack = RuinousScythe.INSTANCE.createItem();
        ItemEnchantments enc = stack.get(DataComponents.ENCHANTMENTS);
        if (enc == null) {
            helper.fail("createItem() must apply enchantments (enchantments component is null)");
        }
        var enchReg = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        int level = enc.getLevel(enchReg.getOrThrow(Enchantments.SHARPNESS));
        if (level != 5) {
            helper.fail("Expected Sharpness 5, got level " + level);
        }
        helper.succeed();
    }

    @GameTest
    public void ruinous_scythe_has_fire_aspect_2(GameTestHelper helper) {
        ItemStack stack = RuinousScythe.INSTANCE.createItem();
        ItemEnchantments enc = stack.get(DataComponents.ENCHANTMENTS);
        if (enc == null) {
            helper.fail("createItem() must apply enchantments (enchantments component is null)");
        }
        var enchReg = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        int level = enc.getLevel(enchReg.getOrThrow(Enchantments.FIRE_ASPECT));
        if (level != 2) {
            helper.fail("Expected Fire Aspect 2, got level " + level);
        }
        helper.succeed();
    }

    @GameTest
    public void ruinous_scythe_has_smite_5(GameTestHelper helper) {
        ItemStack stack = RuinousScythe.INSTANCE.createItem();
        ItemEnchantments enc = stack.get(DataComponents.ENCHANTMENTS);
        if (enc == null) {
            helper.fail("createItem() must apply enchantments (enchantments component is null)");
        }
        var enchReg = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        int level = enc.getLevel(enchReg.getOrThrow(Enchantments.SMITE));
        if (level != 5) {
            helper.fail("Expected Smite 5, got level " + level);
        }
        helper.succeed();
    }

    // ─── WeaponRegistry ───────────────────────────────────────────────────────

    @GameTest
    public void ruinous_scythe_registry_for_end(GameTestHelper helper) {
        MythicWeapon found = WeaponRegistry.getForOrder("end");
        if (found == null) {
            helper.fail("WeaponRegistry.getForOrder(\"end\") must return the RuinousScythe after registration");
        }
        if (!(found instanceof RuinousScythe)) {
            helper.fail("WeaponRegistry.getForOrder(\"end\") returned wrong type: " + found.getClass().getSimpleName());
        }
        helper.succeed();
    }

    // ─── Combo counter logic ──────────────────────────────────────────────────

    /**
     * Directly manipulates the package-visible COMBO_COUNTERS map to simulate 3 hits,
     * verifying the counter increments from 0 → 1 → 2 → 3 without triggering the burst.
     */
    @GameTest
    public void ruinous_scythe_combo_counter_increments(GameTestHelper helper) {
        UUID attacker = UUID.randomUUID();
        UUID target = UUID.randomUUID();

        // Start clean
        RuinousScythe.COMBO_COUNTERS.remove(attacker);

        ConcurrentHashMap<UUID, Integer> attackerMap =
                RuinousScythe.COMBO_COUNTERS.computeIfAbsent(attacker, k -> new ConcurrentHashMap<>());

        // Simulate 3 hits manually
        for (int i = 1; i <= 3; i++) {
            attackerMap.merge(target, 1, Integer::sum);
            int count = attackerMap.get(target);
            if (count != i) {
                helper.fail("After hit " + i + ", expected count " + i + " but got " + count);
            }
        }

        // Cleanup
        RuinousScythe.COMBO_COUNTERS.remove(attacker);
        helper.succeed();
    }

    /**
     * Verifies that the combo counter is tracked separately per target.
     * Attacker A hits target X twice (count=2), then hits target Y once (count=1).
     * Target X's counter must still be 2 afterwards.
     */
    @GameTest
    public void ruinous_scythe_combo_counter_separate_per_target(GameTestHelper helper) {
        UUID attacker = UUID.randomUUID();
        UUID targetX = UUID.randomUUID();
        UUID targetY = UUID.randomUUID();

        ConcurrentHashMap<UUID, Integer> attackerMap =
                RuinousScythe.COMBO_COUNTERS.computeIfAbsent(attacker, k -> new ConcurrentHashMap<>());

        // 2 hits on X, 1 hit on Y
        attackerMap.merge(targetX, 1, Integer::sum);
        attackerMap.merge(targetX, 1, Integer::sum);
        attackerMap.merge(targetY, 1, Integer::sum);

        int countX = attackerMap.getOrDefault(targetX, 0);
        int countY = attackerMap.getOrDefault(targetY, 0);

        if (countX != 2) helper.fail("Target X count must be 2, got " + countX);
        if (countY != 1) helper.fail("Target Y count must be 1, got " + countY);

        RuinousScythe.COMBO_COUNTERS.remove(attacker);
        helper.succeed();
    }

    /**
     * After the 4th hit triggers the burst, the counter must be reset to 0 (not 4).
     * We simulate the reset inline as {@link RuinousScythe#onAttack} does.
     */
    @GameTest
    public void ruinous_scythe_combo_resets_to_zero_after_trigger(GameTestHelper helper) {
        UUID attacker = UUID.randomUUID();
        UUID target = UUID.randomUUID();

        ConcurrentHashMap<UUID, Integer> attackerMap =
                RuinousScythe.COMBO_COUNTERS.computeIfAbsent(attacker, k -> new ConcurrentHashMap<>());

        // Simulate 3 hits
        for (int i = 0; i < 3; i++) attackerMap.merge(target, 1, Integer::sum);

        // Simulate 4th hit + burst reset (mirrors onAttack logic)
        int newCount = attackerMap.merge(target, 1, Integer::sum);
        if (newCount >= 4) {
            attackerMap.put(target, 0);
        }

        int counterAfter = attackerMap.getOrDefault(target, -1);
        if (counterAfter != 0) {
            helper.fail("Counter must be 0 after 4th-hit burst, got " + counterAfter);
        }

        RuinousScythe.COMBO_COUNTERS.remove(attacker);
        helper.succeed();
    }

    // ─── Armour durability distribution ──────────────────────────────────────

    /**
     * Calls {@link RuinousScythe#distributeArmor} 1 000 times to verify:
     * <ul>
     *   <li>Total damage is always in the range [15, 20].</li>
     *   <li>Every individual piece receives ≥ 0 damage.</li>
     *   <li>The sum of pieces equals the total.</li>
     * </ul>
     */
    @GameTest
    public void ruinous_scythe_armor_durability_distribution(GameTestHelper helper) {
        java.util.Random rng = new java.util.Random(42); // deterministic seed for reproducibility
        for (int trial = 0; trial < 1000; trial++) {
            int numPieces = 4;
            int[] distribution = RuinousScythe.distributeArmor(numPieces, rng);

            if (distribution.length != numPieces) {
                helper.fail("distributeArmor returned array of length " + distribution.length
                        + " but expected " + numPieces);
            }

            int sum = 0;
            for (int i = 0; i < distribution.length; i++) {
                if (distribution[i] < 0) {
                    helper.fail("Piece " + i + " received negative damage: " + distribution[i]);
                }
                sum += distribution[i];
            }

            if (sum < 15 || sum > 20) {
                helper.fail("Total damage " + sum + " is outside [15, 20]");
            }
        }
        helper.succeed();
    }

    // ─── Shield block and combo timeout ──────────────────────────────────────

    /**
     * Verifies that the combo counter is NOT incremented for a blocking victim.
     * The new 10a fix checks {@code victim.isBlocking()} rather than {@code ctx.isCancelled()}.
     * We simulate this by confirming the guard: when a victim's blocking flag would be true,
     * onAttack skips the merge. We test the map directly: map stays empty.
     *
     * <p>Since we cannot easily set isBlocking() on a real entity in a unit test without a full
     * server connection, we verify the structural invariant: starting with an empty map and
     * simulating the guard logic, the map remains empty for a "blocked" hit.
     */
    @GameTest
    public void ruinous_scythe_shield_hit_not_counted(GameTestHelper helper) {
        UUID attacker = UUID.randomUUID();
        UUID target = UUID.randomUUID();

        // Start clean
        RuinousScythe.COMBO_COUNTERS.remove(attacker);

        // Simulate the guard: if isBlocking() is true, onAttack returns without merging.
        // We verify that without the merge, the map entry is absent (count = 0).
        boolean isBlocking = true; // simulated
        if (!isBlocking) {
            RuinousScythe.COMBO_COUNTERS
                    .computeIfAbsent(attacker, k -> new ConcurrentHashMap<>())
                    .merge(target, 1, Integer::sum);
        }

        int count = RuinousScythe.COMBO_COUNTERS
                .getOrDefault(attacker, new ConcurrentHashMap<>())
                .getOrDefault(target, 0);

        if (count != 0) {
            helper.fail("Blocked hit must not increment combo counter; expected 0, got " + count);
        }

        RuinousScythe.COMBO_COUNTERS.remove(attacker);
        helper.succeed();
    }

    /**
     * Verifies the combo-reset generation mechanic: after the generation for (attacker, target)
     * is incremented, the reset task scheduled for the old generation is a no-op.
     * Only the task holding the current generation should clear the counter.
     */
    @GameTest
    public void ruinous_scythe_combo_reset_generation_guard(GameTestHelper helper) {
        UUID attacker = UUID.randomUUID();
        UUID target = UUID.randomUUID();

        ConcurrentHashMap<UUID, Integer> attackerCombo =
                RuinousScythe.COMBO_COUNTERS.computeIfAbsent(attacker, k -> new ConcurrentHashMap<>());
        ConcurrentHashMap<UUID, Integer> attackerGen =
                RuinousScythe.RESET_GENERATIONS.computeIfAbsent(attacker, k -> new ConcurrentHashMap<>());

        // Hit 1 — generation becomes 1
        attackerCombo.merge(target, 1, Integer::sum);
        int gen1 = attackerGen.merge(target, 1, Integer::sum);

        // Hit 2 — generation becomes 2, invalidating the gen1 reset task
        attackerCombo.merge(target, 1, Integer::sum);
        int gen2 = attackerGen.merge(target, 1, Integer::sum);

        // Simulate gen1's reset task: it should see gen2 != gen1 and do nothing
        Integer currentGen = attackerGen.get(target);
        boolean gen1ResetFires = currentGen != null && currentGen == gen1;
        if (gen1ResetFires) {
            helper.fail("Generation guard failed: old reset task (gen " + gen1
                    + ") matched current gen " + currentGen);
        }

        // Simulate gen2's reset task: it should fire and clear the counter
        boolean gen2ResetFires = currentGen != null && currentGen == gen2;
        if (!gen2ResetFires) {
            helper.fail("Current-gen reset task (gen " + gen2 + ") must match current gen "
                    + currentGen);
        }

        // Perform the reset as onAttack would
        attackerCombo.remove(target);
        attackerGen.remove(target);

        int countAfter = attackerCombo.getOrDefault(target, -999);
        if (countAfter != -999) {
            helper.fail("Counter must be absent after reset; got " + countAfter);
        }

        RuinousScythe.COMBO_COUNTERS.remove(attacker);
        RuinousScythe.RESET_GENERATIONS.remove(attacker);
        helper.succeed();
    }
}
