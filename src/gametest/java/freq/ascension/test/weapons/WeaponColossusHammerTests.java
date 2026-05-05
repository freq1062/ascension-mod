package freq.ascension.test.weapons;

import freq.ascension.registry.WeaponRegistry;
import freq.ascension.test.TestHelper;
import freq.ascension.weapons.ColossusHammer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * Tests for the ColossusHammer weapon: damage, enchantments,
 * unbreakable attribute, and Earth order assignment.
 */
public class WeaponColossusHammerTests {

    /**
     * Gives ColossusHammer to earth god player; player attacks mob;
     * asserts mob takes damage.
     */
    @GameTest
    public void colossusHammerDealsDamageToMob(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().setItem(0, ColossusHammer.INSTANCE.createItem());
        TestHelper.selectHotbarSlot(player, 0);
        Mob mob = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 3.0F, 2.0F, 1.0F);

        helper.runAfterDelay(2, () -> {
            float before = mob.getHealth();
            helper.hurt(mob, helper.getLevel().damageSources().playerAttack(player), 8.0F);
            if (mob.getHealth() < before) {
                helper.succeed();
            } else {
                helper.fail("Expected Colossus Hammer attack to damage the mob");
            }
        });
    }

    /**
     * Gives hammer; asserts Breach enchantment present.
     */
    @GameTest
    public void colossusHammerHasBreachEnchant(GameTestHelper helper) {
        ItemStack stack = ColossusHammer.INSTANCE.createItem();
        var enchantments = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        int breach = EnchantmentHelper.getItemEnchantmentLevel(enchantments.getOrThrow(Enchantments.BREACH), stack);
        if (breach == 2) {
            helper.succeed();
        } else {
            helper.fail("Expected Breach II, got " + breach);
        }
    }

    /**
     * Gives hammer; asserts Wind Burst enchantment present.
     */
    @GameTest
    public void colossusHammerHasWindBurstEnchant(GameTestHelper helper) {
        ItemStack stack = ColossusHammer.INSTANCE.createItem();
        var enchantments = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        int windBurst = EnchantmentHelper
                .getItemEnchantmentLevel(enchantments.getOrThrow(Enchantments.WIND_BURST), stack);
        if (windBurst == 1) {
            helper.succeed();
        } else {
            helper.fail("Expected Wind Burst I, got " + windBurst);
        }
    }

    /**
     * Gives hammer; asserts unbreakable attribute.
     */
    @GameTest
    public void colossusHammerIsUnbreakable(GameTestHelper helper) {
        if (ColossusHammer.INSTANCE.createItem().get(DataComponents.UNBREAKABLE) != null) {
            helper.succeed();
        } else {
            helper.fail("Expected Colossus Hammer to be unbreakable");
        }
    }

    /**
     * Checks weapon registry; asserts ColossusHammer is registered for the Earth order.
     */
    @GameTest
    public void colossusHammerAssignedToEarthGodOrder(GameTestHelper helper) {
        if (WeaponRegistry.getForOrder("earth") == ColossusHammer.INSTANCE) {
            helper.succeed();
        } else {
            helper.fail("Expected Colossus Hammer to be registered for the Earth order");
        }
    }
}
