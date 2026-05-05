package freq.ascension.test.weapons;

import freq.ascension.registry.WeaponRegistry;
import freq.ascension.weapons.GravitonGauntlet;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Tests that mythical weapons cannot be dropped by players and that
 * admin force-drop overrides the protection.
 */
public class WeaponInventoryPreventionTests {

    /**
     * Gives player a GravitonGauntlet; player uses the drop-item action (Q key equivalent);
     * asserts weapon is still in player inventory (not dropped to ground).
     */
    @GameTest
    public void playerCannotDropMythicalWeaponViaQKey(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().setItem(0, GravitonGauntlet.INSTANCE.createItem());

        helper.runAfterDelay(2, () -> {
            ItemStack removed = player.getInventory().removeItemNoUpdate(0);
            ItemEntity dropped = player.drop(removed, false);
            helper.runAfterDelay(1, () -> {
                if (dropped != null) {
                    helper.fail("Expected mythical weapon drop to be blocked");
                } else if (!WeaponRegistry.isMythicalWeapon(player.getInventory().getItem(0))) {
                    helper.fail("Expected mythical weapon to remain in the player's inventory");
                } else {
                    helper.succeed();
                }
            });
        });
    }

    /**
     * Admin force-drops mythical weapon; asserts weapon entity appears on ground.
     */
    @GameTest
    public void adminForceDropOverridesProtection(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        helper.getLevel().getServer().getPlayerList().op(player.nameAndId());
        player.getInventory().setItem(0, GravitonGauntlet.INSTANCE.createItem());

        helper.runAfterDelay(2, () -> {
            ItemStack removed = player.getInventory().removeItemNoUpdate(0);
            ItemEntity dropped = player.drop(removed, false);
            helper.runAfterDelay(1, () -> {
                if (dropped == null) {
                    helper.fail("Expected admin drop to create an item entity");
                } else if (!WeaponRegistry.isMythicalWeapon(dropped.getItem())) {
                    helper.fail("Expected dropped item entity to contain the mythical weapon");
                } else if (!player.getInventory().getItem(0).isEmpty()) {
                    helper.fail("Expected mythical weapon to leave the admin inventory when dropped");
                } else {
                    helper.succeed();
                }
            });
        });
    }
}
