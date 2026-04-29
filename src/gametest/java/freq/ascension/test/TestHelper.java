package freq.ascension.test;

import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;

/**
 * Shared helper utilities for Ascension integration tests.
 *
 * <p>All equip/bind/activate helpers dispatch real in-game commands so tests
 * replicate exact player behaviour rather than calling internal Java APIs.
 * Admin-level commands (/set, /setrank, /setinfluence) are dispatched via the
 * server command source (permission level 4).  Player-level commands (/bind,
 * /activatespell) are dispatched via the mock player's command source.
 *
 * <p><b>NOTE:</b> Helper method bodies are stubs — they will be filled in when
 * test bodies are implemented.
 */
public class TestHelper {

    // -------------------------------------------------------------------------
    // Order / rank setup
    // -------------------------------------------------------------------------

    /**
     * Equips {@code order} into {@code slot} ("passive", "utility", or "combat")
     * for {@code player} by running {@code /set <name> <slot> <order>} as the
     * server (OP level 4).
     */
    public static void equip(GameTestHelper helper, ServerPlayer player, String slot, String order) {
        String cmd = "set " + player.getGameProfile().getName() + " " + slot + " " + order;
        helper.getLevel().getServer().getCommands()
                .performPrefixedCommand(helper.getLevel().getServer().createCommandSourceStack(), cmd);
    }

    /**
     * Clears {@code slot} for {@code player} by equipping the "none" order.
     */
    public static void unequip(GameTestHelper helper, ServerPlayer player, String slot) {
        equip(helper, player, slot, "none");
    }

    /**
     * Sets {@code player}'s rank ("demigod" or "god") via {@code /setrank}.
     */
    public static void setRank(GameTestHelper helper, ServerPlayer player, String rank) {
        String cmd = "setrank " + player.getGameProfile().getName() + " " + rank;
        helper.getLevel().getServer().getCommands()
                .performPrefixedCommand(helper.getLevel().getServer().createCommandSourceStack(), cmd);
    }

    /**
     * Sets {@code player}'s influence to {@code amount} via {@code /setinfluence}.
     */
    public static void setInfluence(GameTestHelper helper, ServerPlayer player, int amount) {
        String cmd = "setinfluence " + player.getGameProfile().getName() + " " + amount;
        helper.getLevel().getServer().getCommands()
                .performPrefixedCommand(helper.getLevel().getServer().createCommandSourceStack(), cmd);
    }

    // -------------------------------------------------------------------------
    // Spell binding / activation
    // -------------------------------------------------------------------------

    /**
     * Binds {@code spellId} to hotbar slot {@code hotbarSlot} (1-9) for
     * {@code player} via {@code /bind <slot> <spellId>}.
     */
    public static void bind(GameTestHelper helper, ServerPlayer player, int hotbarSlot, String spellId) {
        String cmd = "bind " + hotbarSlot + " " + spellId;
        helper.getLevel().getServer().getCommands()
                .performPrefixedCommand(player.createCommandSourceStack(), cmd);
    }

    /**
     * Activates the spell in the player's current hotbar slot via
     * {@code /activatespell}.
     */
    public static void activateSpell(GameTestHelper helper, ServerPlayer player) {
        helper.getLevel().getServer().getCommands()
                .performPrefixedCommand(player.createCommandSourceStack(), "activatespell");
    }

    /**
     * Selects hotbar {@code slot} (0-8) for {@code player}.
     */
    public static void selectHotbarSlot(ServerPlayer player, int slot) {
        player.getInventory().selected = slot;
    }

    // -------------------------------------------------------------------------
    // Effect assertions
    // -------------------------------------------------------------------------

    /**
     * Fails the test if {@code player} does not currently have {@code effect}.
     */
    public static void assertEffect(GameTestHelper helper, ServerPlayer player,
            Holder<MobEffect> effect, String failMessage) {
        if (player.getEffect(effect) == null) {
            helper.fail(failMessage);
        }
    }

    /**
     * Fails the test if {@code player} currently has {@code effect}.
     */
    public static void assertNoEffect(GameTestHelper helper, ServerPlayer player,
            Holder<MobEffect> effect, String failMessage) {
        if (player.getEffect(effect) != null) {
            helper.fail(failMessage);
        }
    }
}
