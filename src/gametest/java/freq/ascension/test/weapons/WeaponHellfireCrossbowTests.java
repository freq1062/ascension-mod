package freq.ascension.test.weapons;

import java.util.UUID;

import freq.ascension.animation.HellfireBeam;
import freq.ascension.registry.WeaponRegistry;
import freq.ascension.weapons.HellfireCrossbow;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * Tests for the HellfireCrossbow weapon: hellfire beam triggering on the third shot,
 * counter reset, damage scaling with range, enchantments, unbreakable attribute,
 * and Nether order assignment.
 */
public class WeaponHellfireCrossbowTests {

    /**
     * Gives HellfireCrossbow to nether god; fires 3 bolts at mob;
     * asserts hellfire beam fires on 3rd shot.
     */
    @GameTest
    public void hellfireBeamTriggersOnThirdShot(GameTestHelper helper) {
        HellfireCrossbow.FIREWORK_COUNTER.clear();
        UUID playerId = UUID.randomUUID();

        boolean first = HellfireCrossbow.incrementAndCheck(playerId);
        boolean second = HellfireCrossbow.incrementAndCheck(playerId);
        boolean third = HellfireCrossbow.incrementAndCheck(playerId);
        int counter = HellfireCrossbow.FIREWORK_COUNTER.getOrDefault(playerId, -1);

        if (!first && !second && third && counter == 3) {
            helper.succeed();
        } else {
            helper.fail("Expected Hellfire beam trigger only on the 3rd shot; got first="
                    + first + ", second=" + second + ", third=" + third + ", counter=" + counter);
        }
    }

    /**
     * Fires beam; fires one more bolt; asserts counter = 1
     * (reset to 0 after beam, then +1).
     */
    @GameTest
    public void hellfireCounterResetsAfterBeam(GameTestHelper helper) {
        HellfireCrossbow.FIREWORK_COUNTER.clear();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        HellfireCrossbow.INSTANCE.onFireworkShot(player);
        HellfireCrossbow.INSTANCE.onFireworkShot(player);
        HellfireCrossbow.INSTANCE.onFireworkShot(player);
        int afterBeam = HellfireCrossbow.FIREWORK_COUNTER.getOrDefault(player.getUUID(), -1);

        HellfireCrossbow.INSTANCE.onFireworkShot(player);
        int afterNextShot = HellfireCrossbow.FIREWORK_COUNTER.getOrDefault(player.getUUID(), -1);

        if (afterBeam == 0 && afterNextShot == 1) {
            helper.succeed();
        } else {
            helper.fail("Expected counter reset to 0 after beam and advance to 1 on next shot; got "
                    + afterBeam + " then " + afterNextShot);
        }
    }

    /**
     * Measures beam damage at 5, 30, and 60 blocks;
     * asserts damage at 5 blocks > damage at 60 blocks.
     */
    @GameTest
    public void hellfireDamageScalesWithRange(GameTestHelper helper) {
        double maxHp = 20.0;
        double near = HellfireBeam.calculateDamage(5.0, maxHp);
        double mid = HellfireBeam.calculateDamage(30.0, maxHp);
        double far = HellfireBeam.calculateDamage(60.0, maxHp);

        if (near > mid && mid > far && far == 0.0) {
            helper.succeed();
        } else {
            helper.fail("Expected Hellfire beam damage falloff; got near=" + near + ", mid=" + mid
                    + ", far=" + far);
        }
    }

    /**
     * Gives crossbow; asserts Piercing 4 present.
     */
    @GameTest
    public void hellfireCrossbowHasPiercingEnchant(GameTestHelper helper) {
        assertEnchantmentLevel(helper, HellfireCrossbow.INSTANCE.createItem(), Enchantments.PIERCING, 4,
                "Expected Piercing IV on Hellfire Crossbow");
    }

    /**
     * Gives crossbow; asserts Quick Charge 2 present.
     */
    @GameTest
    public void hellfireCrossbowHasQuickChargeEnchant(GameTestHelper helper) {
        assertEnchantmentLevel(helper, HellfireCrossbow.INSTANCE.createItem(), Enchantments.QUICK_CHARGE, 2,
                "Expected Quick Charge II on Hellfire Crossbow");
    }

    /**
     * Gives crossbow; asserts unbreakable.
     */
    @GameTest
    public void hellfireCrossbowIsUnbreakable(GameTestHelper helper) {
        ItemStack stack = HellfireCrossbow.INSTANCE.createItem();
        if (stack.get(DataComponents.UNBREAKABLE) != null) {
            helper.succeed();
        } else {
            helper.fail("Expected Hellfire Crossbow to be unbreakable");
        }
    }

    /**
     * Checks weapon registry; asserts HellfireCrossbow is registered for the Nether order.
     */
    @GameTest
    public void hellfireCrossbowAssignedToNetherGodOrder(GameTestHelper helper) {
        if (WeaponRegistry.get(HellfireCrossbow.INSTANCE.getWeaponId()) == HellfireCrossbow.INSTANCE
                && WeaponRegistry.getForOrder(HellfireCrossbow.INSTANCE.getParentOrder().getOrderName()) == HellfireCrossbow.INSTANCE) {
            helper.succeed();
        } else {
            helper.fail("Expected Hellfire Crossbow to be registered for the Nether order");
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
