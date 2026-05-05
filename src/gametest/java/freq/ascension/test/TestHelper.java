package freq.ascension.test;

import freq.ascension.managers.AscensionData;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.player.Player;

/**
 * Shared helper utilities for Ascension integration tests.
 *
 * <p>All equip/bind/activate helpers dispatch real in-game commands so tests
 * replicate exact player behaviour rather than calling internal Java APIs.
 * Admin-level commands (/set, /setrank, /setinfluence) are dispatched via the
 * server command source (permission level 4). Player-level commands (/bind,
 * /activatespell) are dispatched via the mock player's command source.
 */
public class TestHelper {

    private static String target(Player player) {
        return "@s";
    }

    private static ServerPlayer serverPlayer(Player player) {
        return (ServerPlayer) player;
    }

    private static void runAdminCommand(GameTestHelper helper, Player player, String command) {
        helper.getLevel().getServer().getCommands()
                .performPrefixedCommand(serverPlayer(player).createCommandSourceStack().withPermission(4), command);
    }

    // -------------------------------------------------------------------------
    // Order / rank setup
    // -------------------------------------------------------------------------

    /**
     * Equips {@code order} into {@code slot} ("passive", "utility", or "combat")
     * for {@code player} by running {@code /set @s <slot> <order>} as that
     * player with elevated permission.
     */
    public static void equip(GameTestHelper helper, Player player, String slot, String order) {
        String cmd = "set " + target(player) + " " + slot + " " + order;
        runAdminCommand(helper, player, cmd);
    }

    /**
     * Clears {@code slot} for {@code player} by equipping the "none" order.
     */
    public static void unequip(GameTestHelper helper, Player player, String slot) {
        equip(helper, player, slot, "none");
    }

    /**
     * Sets {@code player}'s rank ("demigod" or "god") via {@code /setrank}.
     */
    public static void setRank(GameTestHelper helper, Player player, String rank) {
        String cmd = "setrank " + target(player) + " " + rank;
        runAdminCommand(helper, player, cmd);
    }

    /**
     * Sets {@code player}'s rank, including the required {@code order} argument
     * for god promotions, via {@code /setrank}.
     */
    public static void setRank(GameTestHelper helper, Player player, String rank, String order) {
        String cmd = "setrank " + target(player) + " " + rank;
        if (order != null && !order.isBlank()) {
            cmd += " " + order;
        }
        runAdminCommand(helper, player, cmd);
    }

    /**
     * Promotes {@code player} to god rank for {@code order}.
     */
    public static void promoteToGod(GameTestHelper helper, Player player, String order) {
        setRank(helper, player, "god", order);
    }

    /**
     * Sets god status directly without triggering GodManager promotion logic.
     * Used by tests to avoid concurrent god demotion conflicts.
     * Directly sets rank, order, and all ability slots to the given order.
     */
    public static void setGodDirect(GameTestHelper helper, Player player, String order) {
        AscensionData data = (AscensionData) player;
        data.setRank("god");
        data.setGodOrder(order);
        data.setPassive(order);
        data.setUtility(order);
        data.setCombat(order);
    }

    /**
     * Sets {@code player}'s influence to {@code amount} via {@code /setinfluence}.
     */
    public static void setInfluence(GameTestHelper helper, Player player, int amount) {
        String cmd = "setinfluence " + target(player) + " " + amount;
        runAdminCommand(helper, player, cmd);
    }

    // -------------------------------------------------------------------------
    // Spell binding / activation
    // -------------------------------------------------------------------------

    /**
     * Binds {@code spellId} to hotbar slot {@code hotbarSlot} (1-9) for
     * {@code player} via {@code /bind <slot> <spellId>}.
     */
    public static void bind(GameTestHelper helper, Player player, int hotbarSlot, String spellId) {
        String cmd = "bind " + hotbarSlot + " " + spellId;
        ServerPlayer splayer = serverPlayer(player);
        helper.getLevel().getServer().getCommands()
                .performPrefixedCommand(splayer.createCommandSourceStack(), cmd);
    }

    /**
     * Activates the spell in the player's current hotbar slot via
     * {@code /activatespell}.
     */
    public static void activateSpell(GameTestHelper helper, Player player) {
        ServerPlayer splayer = serverPlayer(player);
        helper.getLevel().getServer().getCommands()
                .performPrefixedCommand(splayer.createCommandSourceStack(), "activatespell");
    }

    /**
     * Selects hotbar {@code slot} (0-8) for {@code player}.
     */
    public static void selectHotbarSlot(Player player, int slot) {
        try {
            var field = player.getInventory().getClass().getDeclaredField("selected");
            field.setAccessible(true);
            field.setInt(player.getInventory(), slot);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to select hotbar slot", e);
        }
    }

    // -------------------------------------------------------------------------
    // Effect assertions
    // -------------------------------------------------------------------------

    /**
     * Fails the test if {@code player} does not currently have {@code effect}.
     */
    public static void assertEffect(GameTestHelper helper, Player player,
            Holder<MobEffect> effect, String failMessage) {
        if (player.getEffect(effect) == null) {
            helper.fail(failMessage);
        }
    }

    /**
     * Fails the test if {@code player} currently has {@code effect}.
     */
    public static void assertNoEffect(GameTestHelper helper, Player player,
            Holder<MobEffect> effect, String failMessage) {
        if (player.getEffect(effect) != null) {
            helper.fail(failMessage);
        }
    }
}
