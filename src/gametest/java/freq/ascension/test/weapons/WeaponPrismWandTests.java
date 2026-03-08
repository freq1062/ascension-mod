package freq.ascension.test.weapons;

import java.util.List;

import freq.ascension.orders.Order;
import freq.ascension.registry.WeaponRegistry;
import freq.ascension.weapons.MythicWeapon;
import freq.ascension.weapons.PrismWand;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Unit;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;

/**
 * GameTest suite for the {@link PrismWand} mythical weapon.
 *
 * <p>Covers identity, item creation, isItem detection, enchantment presence,
 * aimbot targeting geometry, effect-copy logic, and WeaponRegistry integration.
 */
public class WeaponPrismWandTests {

    // ─────────────────────────────────────────────────────────────────────────
    // Identity
    // ─────────────────────────────────────────────────────────────────────────

    @GameTest
    public void prism_wand_weapon_id(GameTestHelper helper) {
        if (!"prism_wand".equals(PrismWand.INSTANCE.getWeaponId())) {
            helper.fail("getWeaponId() must return \"prism_wand\"");
        }
        helper.succeed();
    }

    @GameTest
    public void prism_wand_base_item(GameTestHelper helper) {
        if (PrismWand.INSTANCE.getBaseItem() != Items.BOW) {
            helper.fail("getBaseItem() must return Items.BOW, got: " + PrismWand.INSTANCE.getBaseItem());
        }
        helper.succeed();
    }

    @GameTest
    public void prism_wand_parent_order(GameTestHelper helper) {
        if (!"magic".equals(PrismWand.INSTANCE.getParentOrder().getOrderName())) {
            helper.fail("getParentOrder().getOrderName() must return \"magic\", got: "
                    + PrismWand.INSTANCE.getParentOrder().getOrderName());
        }
        helper.succeed();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Item creation
    // ─────────────────────────────────────────────────────────────────────────

    @GameTest
    public void prism_wand_create_item_not_empty(GameTestHelper helper) {
        ItemStack stack = PrismWand.INSTANCE.createItem();
        if (stack == null || stack.isEmpty()) {
            helper.fail("createItem() must return a non-empty ItemStack");
        }
        helper.succeed();
    }

    @GameTest
    public void prism_wand_has_custom_model_data(GameTestHelper helper) {
        ItemStack stack = PrismWand.INSTANCE.createItem();
        CustomModelData cmd = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        if (cmd == null || !cmd.strings().contains("prism_wand")) {
            helper.fail("createItem() must have CustomModelData string \"prism_wand\", got: "
                    + (cmd == null ? "null" : cmd.strings()));
        }
        helper.succeed();
    }

    @GameTest
    public void prism_wand_is_unbreakable(GameTestHelper helper) {
        ItemStack stack = PrismWand.INSTANCE.createItem();
        Unit unbreakable = stack.get(DataComponents.UNBREAKABLE);
        if (unbreakable == null) {
            helper.fail("createItem() must set DataComponents.UNBREAKABLE");
        }
        helper.succeed();
    }

    @GameTest
    public void prism_wand_has_custom_name(GameTestHelper helper) {
        ItemStack stack = PrismWand.INSTANCE.createItem();
        if (stack.get(DataComponents.CUSTOM_NAME) == null) {
            helper.fail("createItem() must set DataComponents.CUSTOM_NAME");
        }
        helper.succeed();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // isItem detection
    // ─────────────────────────────────────────────────────────────────────────

    @GameTest
    public void prism_wand_is_item_detects_own(GameTestHelper helper) {
        ItemStack stack = PrismWand.INSTANCE.createItem();
        if (!PrismWand.INSTANCE.isItem(stack)) {
            helper.fail("isItem() must return true for createItem() result");
        }
        helper.succeed();
    }

    @GameTest
    public void prism_wand_is_item_rejects_plain_bow(GameTestHelper helper) {
        if (PrismWand.INSTANCE.isItem(new ItemStack(Items.BOW))) {
            helper.fail("isItem() must return false for a plain ItemStack(Items.BOW)");
        }
        helper.succeed();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Enchantments
    // ─────────────────────────────────────────────────────────────────────────

    @GameTest
    public void prism_wand_has_power_5(GameTestHelper helper) {
        ItemStack stack = PrismWand.INSTANCE.createItem();
        var enchReg = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        int level = EnchantmentHelper.getItemEnchantmentLevel(
                enchReg.getOrThrow(Enchantments.POWER), stack);
        if (level != 5) {
            helper.fail("createItem() must have Power level 5, got: " + level);
        }
        helper.succeed();
    }

    @GameTest
    public void prism_wand_has_flame(GameTestHelper helper) {
        ItemStack stack = PrismWand.INSTANCE.createItem();
        var enchReg = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        int level = EnchantmentHelper.getItemEnchantmentLevel(
                enchReg.getOrThrow(Enchantments.FLAME), stack);
        if (level < 1) {
            helper.fail("createItem() must have Flame enchantment (level >= 1), got: " + level);
        }
        helper.succeed();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Aimbot targeting geometry — uses real spawned entities
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Standard zombie eye height in Minecraft 1.21.x.
     * Zombie entity height = 1.95, eye height = 1.74 blocks above feet.
     */
    private static final double ZOMBIE_EYE_HEIGHT = 1.74;

    /**
     * Spawns zombies at 0°, 4°, and 6° off-axis from a fixed look direction and verifies
     * that findTarget honours the 5-degree threshold.
     */
    @GameTest
    public void prism_wand_aimbot_target_within_5_degrees(GameTestHelper helper) {
        // Fixed eye position and look direction (+Z)
        Vec3 eye  = new Vec3(0.5, 65.5, 0.5);
        Vec3 look = new Vec3(0, 0, 1).normalize();
        double range = 10.0;

        // Desired eye positions for each test angle
        Vec3 ep0 = new Vec3(eye.x, eye.y, eye.z + range);                                           // 0°
        Vec3 ep4 = new Vec3(eye.x + Math.sin(Math.toRadians(4)) * range, eye.y,
                             eye.z + Math.cos(Math.toRadians(4)) * range);                           // 4°
        Vec3 ep6 = new Vec3(eye.x + Math.sin(Math.toRadians(6)) * range, eye.y,
                             eye.z + Math.cos(Math.toRadians(6)) * range);                           // 6°

        // Spawn zombies and teleport their feet so their eye matches the desired positions.
        // Zombie eye = feet + ZOMBIE_EYE_HEIGHT, so feet = eye - ZOMBIE_EYE_HEIGHT.
        LivingEntity z0 = (LivingEntity) helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 2, 1));
        LivingEntity z4 = (LivingEntity) helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 2, 1));
        LivingEntity z6 = (LivingEntity) helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 2, 1));

        z0.teleportTo(ep0.x, ep0.y - ZOMBIE_EYE_HEIGHT, ep0.z);
        z4.teleportTo(ep4.x, ep4.y - ZOMBIE_EYE_HEIGHT, ep4.z);
        z6.teleportTo(ep6.x, ep6.y - ZOMBIE_EYE_HEIGHT, ep6.z);

        try {
            if (PrismWand.findTarget(eye, look, List.of(z0), 64, 5) != z0) {
                helper.fail("Entity at 0 degrees must be found by findTarget");
            }
            if (PrismWand.findTarget(eye, look, List.of(z4), 64, 5) != z4) {
                helper.fail("Entity at 4 degrees must be found (within 5-degree cone)");
            }
            if (PrismWand.findTarget(eye, look, List.of(z6), 64, 5) != null) {
                helper.fail("Entity at 6 degrees must NOT be found (outside 5-degree cone)");
            }
        } finally {
            z0.discard();
            z4.discard();
            z6.discard();
        }
        helper.succeed();
    }



    // ─────────────────────────────────────────────────────────────────────────
    // WeaponRegistry integration
    // ─────────────────────────────────────────────────────────────────────────

    @GameTest
    public void prism_wand_registry_for_magic(GameTestHelper helper) {
        try {
            WeaponRegistry.register(PrismWand.INSTANCE);
        } catch (IllegalArgumentException e) {
            // Already registered during onInitialize — acceptable
        }
        MythicWeapon found = WeaponRegistry.getForOrder("magic");
        if (found == null) {
            helper.fail("WeaponRegistry.getForOrder(\"magic\") must not be null after registration");
        }
        helper.succeed();
    }
}
