package freq.ascension.test.weapons;

import java.util.List;

import freq.ascension.Config;
import freq.ascension.registry.WeaponRegistry;
import freq.ascension.weapons.PrismWand;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display.BlockDisplay;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Tests for the PrismWand weapon: arrow homing within 5 degrees,
 * shrinking visual effect on impact, enchantments, unbreakable attribute,
 * and Magic order assignment.
 */
public class WeaponPrismWandTests {

    /**
     * Gives PrismWand to magic god; mob is within 5 degrees of aim;
     * fires wand; asserts arrow homes to mob.
     */
    @GameTest
    public void prismWandArrowCurvesToMobWithin5Degrees(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Mob aligned = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, helper.absolutePos(new BlockPos(1, 2, 1)));
        Mob offAxis = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, helper.absolutePos(new BlockPos(2, 2, 2)));

        Vec3 eyePos = player.getEyePosition();
        Vec3 lookDir = player.getLookAngle().normalize();
        Vec3 perpendicular = new Vec3(-lookDir.z, 0.0, lookDir.x).normalize();

        Vec3 alignedPos = eyePos.add(lookDir.scale(8.0));
        Vec3 offAxisPos = eyePos.add(lookDir.scale(8.0)).add(perpendicular.scale(2.0));
        aligned.setPos(alignedPos.x, player.getY(), alignedPos.z);
        offAxis.setPos(offAxisPos.x, player.getY(), offAxisPos.z);

        LivingEntity target = PrismWand.findTarget(eyePos, lookDir, List.of(aligned, offAxis),
                Config.prismWandRange, 5.0);
        if (target == aligned) {
            helper.succeed();
        } else {
            helper.fail("Expected Prism Wand targeting to prefer the mob within 5 degrees");
        }
    }

    /**
     * Fires wand at mob; asserts bolt/arrow has shrinking visual effect on impact.
     */
    @GameTest
    public void prismWandArrowShrinksOnImpact(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, PrismWand.INSTANCE.createItem());
        Mob target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, helper.absolutePos(new BlockPos(1, 2, 1)));

        Vec3 eyePos = player.getEyePosition();
        Vec3 targetPos = eyePos.add(player.getLookAngle().normalize().scale(2.0));
        target.setPos(targetPos.x, player.getY(), targetPos.z);

        AABB box = new AABB(player.position(), player.position()).inflate(8.0);
        int baseline = helper.getLevel().getEntitiesOfClass(BlockDisplay.class, box).size();

        PrismWand.INSTANCE.handleAimbotShot(player, helper.getLevel(), player.getMainHandItem());
        helper.runAfterDelay(2, () -> {
            int duringFlight = helper.getLevel().getEntitiesOfClass(BlockDisplay.class, box).size();
            if (duringFlight <= baseline) {
                helper.fail("Expected Prism Wand shot to spawn display entities before impact cleanup");
                return;
            }
            helper.runAfterDelay(30, () -> {
                int afterImpact = helper.getLevel().getEntitiesOfClass(BlockDisplay.class, box).size();
                if (afterImpact <= baseline) {
                    helper.succeed();
                } else {
                    helper.fail("Expected Prism Wand impact visuals to shrink away and clean up");
                }
            });
        });
    }

    /**
     * Gives wand; asserts Power 5 present.
     */
    @GameTest
    public void prismWandHasPowerEnchant(GameTestHelper helper) {
        assertEnchantmentLevel(helper, PrismWand.INSTANCE.createItem(), Enchantments.POWER, 5,
                "Expected Power V on Prism Wand");
    }

    /**
     * Gives wand; asserts Flame enchantment present.
     */
    @GameTest
    public void prismWandHasFlameEnchant(GameTestHelper helper) {
        assertEnchantmentLevel(helper, PrismWand.INSTANCE.createItem(), Enchantments.FLAME, 1,
                "Expected Flame on Prism Wand");
    }

    /**
     * Gives wand; asserts unbreakable.
     */
    @GameTest
    public void prismWandIsUnbreakable(GameTestHelper helper) {
        ItemStack stack = PrismWand.INSTANCE.createItem();
        if (stack.get(DataComponents.UNBREAKABLE) != null) {
            helper.succeed();
        } else {
            helper.fail("Expected Prism Wand to be unbreakable");
        }
    }

    /**
     * Checks weapon registry; asserts PrismWand registered for Magic order.
     */
    @GameTest
    public void prismWandAssignedToMagicGodOrder(GameTestHelper helper) {
        if (WeaponRegistry.get(PrismWand.INSTANCE.getWeaponId()) == PrismWand.INSTANCE
                && WeaponRegistry.getForOrder(PrismWand.INSTANCE.getParentOrder().getOrderName()) == PrismWand.INSTANCE) {
            helper.succeed();
        } else {
            helper.fail("Expected Prism Wand to be registered for the Magic order");
        }
    }

    private static void assertEnchantmentLevel(GameTestHelper helper, ItemStack stack,
            net.minecraft.resources.ResourceKey<net.minecraft.world.item.enchantment.Enchantment> enchantment,
            int expectedLevel, String failMessage) {
        var enchReg = helper.getLevel().getServer().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        int actualLevel = EnchantmentHelper.getItemEnchantmentLevel(enchReg.getOrThrow(enchantment), stack);
        if (actualLevel == expectedLevel) {
            helper.succeed();
        } else {
            helper.fail(failMessage + "; got level " + actualLevel);
        }
    }
}
