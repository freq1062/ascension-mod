package freq.ascension.test.weapons;

import java.util.UUID;

import freq.ascension.orders.Sky;
import freq.ascension.registry.WeaponRegistry;
import freq.ascension.weapons.GravitonGauntlet;
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
import net.minecraft.world.phys.Vec3;

/**
 * GameTest suite for the {@link GravitonGauntlet} mythical weapon.
 *
 * <p>Verifies identity, item creation, component data, enchantment presence,
 * mode-toggle logic, velocity calculation helpers, and WeaponRegistry integration.
 */
public class WeaponGravitonGauntletTests {

    // ─────────────────────────────────────────────────────────────────────────
    // Identity
    // ─────────────────────────────────────────────────────────────────────────

    @GameTest
    public void graviton_gauntlet_weapon_id(GameTestHelper helper) {
        if (!"graviton_gauntlet".equals(GravitonGauntlet.INSTANCE.getWeaponId())) {
            helper.fail("getWeaponId() must return \"graviton_gauntlet\"");
        }
        helper.succeed();
    }

    @GameTest
    public void graviton_gauntlet_base_item(GameTestHelper helper) {
        if (GravitonGauntlet.INSTANCE.getBaseItem() != Items.DIAMOND_AXE) {
            helper.fail("getBaseItem() must return Items.DIAMOND_AXE");
        }
        helper.succeed();
    }

    @GameTest
    public void graviton_gauntlet_parent_order(GameTestHelper helper) {
        if (!"sky".equals(GravitonGauntlet.INSTANCE.getParentOrder().getOrderName())) {
            helper.fail("getParentOrder().getOrderName() must return \"sky\"");
        }
        helper.succeed();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Item creation
    // ─────────────────────────────────────────────────────────────────────────

    @GameTest
    public void graviton_gauntlet_create_item_not_empty(GameTestHelper helper) {
        ItemStack stack = GravitonGauntlet.INSTANCE.createItem();
        if (stack == null || stack.isEmpty()) {
            helper.fail("createItem() must return a non-empty ItemStack");
        }
        helper.succeed();
    }

    @GameTest
    public void graviton_gauntlet_has_custom_model_data(GameTestHelper helper) {
        ItemStack stack = GravitonGauntlet.INSTANCE.createItem();
        CustomModelData cmd = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        if (cmd == null || !cmd.strings().contains("graviton_gauntlet")) {
            helper.fail("createItem() must have CustomModelData string \"graviton_gauntlet\", got: "
                    + (cmd == null ? "null" : cmd.strings()));
        }
        helper.succeed();
    }

    @GameTest
    public void graviton_gauntlet_is_unbreakable(GameTestHelper helper) {
        ItemStack stack = GravitonGauntlet.INSTANCE.createItem();
        Unit unbreakable = stack.get(DataComponents.UNBREAKABLE);
        if (unbreakable == null) {
            helper.fail("createItem() must set DataComponents.UNBREAKABLE");
        }
        helper.succeed();
    }

    @GameTest
    public void graviton_gauntlet_has_custom_name(GameTestHelper helper) {
        ItemStack stack = GravitonGauntlet.INSTANCE.createItem();
        if (stack.get(DataComponents.CUSTOM_NAME) == null) {
            helper.fail("createItem() must set DataComponents.CUSTOM_NAME");
        }
        helper.succeed();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // isItem detection
    // ─────────────────────────────────────────────────────────────────────────

    @GameTest
    public void graviton_gauntlet_is_item_detects_own(GameTestHelper helper) {
        ItemStack stack = GravitonGauntlet.INSTANCE.createItem();
        if (!GravitonGauntlet.INSTANCE.isItem(stack)) {
            helper.fail("isItem() must return true for createItem() result");
        }
        helper.succeed();
    }

    @GameTest
    public void graviton_gauntlet_is_item_rejects_plain_axe(GameTestHelper helper) {
        if (GravitonGauntlet.INSTANCE.isItem(new ItemStack(Items.DIAMOND_AXE))) {
            helper.fail("isItem() must return false for a plain ItemStack(Items.DIAMOND_AXE)");
        }
        helper.succeed();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Enchantments
    // ─────────────────────────────────────────────────────────────────────────

    @GameTest
    public void graviton_gauntlet_has_sharpness_5(GameTestHelper helper) {
        ItemStack stack = GravitonGauntlet.INSTANCE.createItem();
        var enchReg = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        int level = EnchantmentHelper.getItemEnchantmentLevel(
                enchReg.getOrThrow(Enchantments.SHARPNESS), stack);
        if (level != 5) {
            helper.fail("createItem() must have Sharpness level 5, got: " + level);
        }
        helper.succeed();
    }

    @GameTest
    public void graviton_gauntlet_has_efficiency_5(GameTestHelper helper) {
        ItemStack stack = GravitonGauntlet.INSTANCE.createItem();
        var enchReg = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        int level = EnchantmentHelper.getItemEnchantmentLevel(
                enchReg.getOrThrow(Enchantments.EFFICIENCY), stack);
        if (level != 5) {
            helper.fail("createItem() must have Efficiency level 5, got: " + level);
        }
        helper.succeed();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Mode toggle logic
    // ─────────────────────────────────────────────────────────────────────────

    @GameTest
    public void graviton_gauntlet_default_mode_is_pull(GameTestHelper helper) {
        UUID uuid = UUID.randomUUID();
        if (!GravitonGauntlet.getPullMode(uuid)) {
            helper.fail("Default mode for a new UUID must be PULL (getPullMode() should return true)");
        }
        helper.succeed();
    }

    @GameTest
    public void graviton_gauntlet_mode_toggles_to_push(GameTestHelper helper) {
        UUID uuid = UUID.randomUUID();
        GravitonGauntlet.toggleMode(uuid); // PULL → PUSH
        if (GravitonGauntlet.getPullMode(uuid)) {
            helper.fail("After one toggle, mode should be PUSH (getPullMode() should return false)");
        }
        helper.succeed();
    }

    @GameTest
    public void graviton_gauntlet_mode_toggles_back_to_pull(GameTestHelper helper) {
        UUID uuid = UUID.randomUUID();
        GravitonGauntlet.toggleMode(uuid); // PULL → PUSH
        GravitonGauntlet.toggleMode(uuid); // PUSH → PULL
        if (!GravitonGauntlet.getPullMode(uuid)) {
            helper.fail("After two toggles, mode should be PULL (getPullMode() should return true)");
        }
        helper.succeed();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Velocity helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Player at origin, entity at (8,0,0): pull velocity must have a negative X component
     * (entity is launched toward the player).
     */
    @GameTest
    public void graviton_gauntlet_pull_velocity_toward_player(GameTestHelper helper) {
        Vec3 playerPos = new Vec3(0, 0, 0);
        Vec3 entityPos = new Vec3(8, 0, 0);
        Vec3 vel = GravitonGauntlet.calcPullVelocity(playerPos, entityPos);
        if (vel.x >= 0) {
            helper.fail("Pull velocity X should be negative (toward player at origin), got: " + vel.x);
        }
        helper.succeed();
    }

    /**
     * Player at origin, entity at (8,0,0): push velocity must have a positive X component
     * (entity is launched away from the player).
     */
    @GameTest
    public void graviton_gauntlet_push_velocity_away_from_player(GameTestHelper helper) {
        Vec3 playerPos = new Vec3(0, 0, 0);
        Vec3 entityPos = new Vec3(8, 0, 0);
        Vec3 vel = GravitonGauntlet.calcPushVelocity(playerPos, entityPos);
        if (vel.x <= 0) {
            helper.fail("Push velocity X should be positive (away from player at origin), got: " + vel.x);
        }
        helper.succeed();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // WeaponRegistry integration
    // ─────────────────────────────────────────────────────────────────────────

    @GameTest
    public void graviton_gauntlet_registry_for_sky(GameTestHelper helper) {
        try {
            WeaponRegistry.register(new GravitonGauntlet());
        } catch (IllegalArgumentException e) {
            // Already registered during onInitialize — acceptable
        }
        MythicWeapon found = WeaponRegistry.getForOrder("sky");
        if (found == null) {
            helper.fail("WeaponRegistry.getForOrder(\"sky\") must not be null after registration");
        }
        helper.succeed();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cooldown: second activation must be blocked
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Verifies that the 400-tick cooldown blocks a second activation.
     *
     * <p>Simulates the cooldown by manually checking {@link GravitonGauntlet#isOnCooldown}
     * before and after a recorded cooldown entry, without requiring a live ServerPlayer.
     */
    @GameTest
    public void gravitonCooldownBlocksSpellActivation(GameTestHelper helper) {
        UUID uuid = UUID.randomUUID();
        long fakeTick = 1000L;

        // Before any activation — must NOT be on cooldown
        if (GravitonGauntlet.isOnCooldown(uuid, fakeTick)) {
            helper.fail("Player must not be on cooldown before first use");
            return;
        }

        // Simulate an activation: cooldown expires at fakeTick + COOLDOWN_TICKS
        java.lang.reflect.Field field;
        try {
            field = GravitonGauntlet.class.getDeclaredField("COOLDOWN_ENDS");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.concurrent.ConcurrentHashMap<UUID, Long> map =
                    (java.util.concurrent.ConcurrentHashMap<UUID, Long>) field.get(null);
            map.put(uuid, fakeTick + GravitonGauntlet.COOLDOWN_TICKS);
        } catch (Exception e) {
            helper.fail("Could not access COOLDOWN_ENDS field: " + e);
            return;
        }

        // 1 tick after activation: must be on cooldown
        if (!GravitonGauntlet.isOnCooldown(uuid, fakeTick + 1)) {
            helper.fail("Player must be on cooldown immediately after activation");
            return;
        }

        // At cooldown expiry tick: must NOT be on cooldown
        if (GravitonGauntlet.isOnCooldown(uuid, fakeTick + GravitonGauntlet.COOLDOWN_TICKS)) {
            helper.fail("Player must not be on cooldown at expiry tick");
            return;
        }

        helper.succeed();
    }
}
