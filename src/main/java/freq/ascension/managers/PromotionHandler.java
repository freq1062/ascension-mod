package freq.ascension.managers;

import freq.ascension.Ascension;
import freq.ascension.orders.Order;
import freq.ascension.registry.OrderRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles promotion-request flow: checks eligibility, stores a pending confirmation, and sends
 * a clickable chat prompt. On confirmation (via {@code /ascend_confirm}), delegates promotion
 * to {@link GodManager}.
 *
 * <p>Pending entries expire after 30 seconds (600 ticks). Call
 * {@link #cleanExpired(int)} from a {@code ServerTickEvents.END_SERVER_TICK} hook each tick.
 */
public class PromotionHandler {

    /** A pending god-ascension confirmation for a specific player. */
    public record PendingPromotion(String orderName, int expiryTick) {}

    // UUID → pending promotion
    private static final Map<UUID, PendingPromotion> pending = new HashMap<>();

    private PromotionHandler() {}

    /**
     * Processes a POI right-click promotion request for the given player and order.
     *
     * <p>Checks:
     * <ol>
     *   <li>Whether a god of the order already exists.</li>
     *   <li>Whether all three ability slots are set to {@code orderName}.</li>
     *   <li>Whether the player has at least 1 influence.</li>
     * </ol>
     * On success, stores a pending confirmation and sends a clickable chat prompt.
     */
    public static void handlePromotionRequest(ServerPlayer player, String orderName, MinecraftServer server) {
        GodManager gm = GodManager.get(server);

        // Check 1: is there already a god of this order?
        UUID existingGodUUID = gm.getGodUUID(orderName);
        if (existingGodUUID != null) {
            if (existingGodUUID.equals(player.getUUID())) {
                // Player IS the god of this order
                player.sendSystemMessage(Component.literal(
                    "§6You are the God of §e" + capitalize(orderName) + "§6! " +
                    "Protect your shrine from challengers to keep your title."));
            } else {
                // Check if the player is god of a DIFFERENT order
                String playerGodOrder = gm.getGodOrderName(player.getUUID());
                if (playerGodOrder != null) {
                    player.sendSystemMessage(Component.literal(
                        "§cYou are already the God of §e" + capitalize(playerGodOrder) +
                        "§c. You can only be the god of one order at a time."));
                } else {
                    // A different player is the god of this order
                    player.sendSystemMessage(Component.literal(
                        "§cThe God of §e" + capitalize(orderName) +
                        "§c is still ascended. You must challenge them to ascend."));
                }
            }
            return;
        }

        // Check 2: all three slots must be this order
        AscensionData data = (AscensionData) player;
        Order passive = data.getPassive();
        Order utility = data.getUtility();
        Order combat = data.getCombat();
        boolean allMatch = passive != null && utility != null && combat != null
                && passive.getOrderName().equalsIgnoreCase(orderName)
                && utility.getOrderName().equalsIgnoreCase(orderName)
                && combat.getOrderName().equalsIgnoreCase(orderName);
        if (!allMatch) {
            player.sendSystemMessage(Component.literal(
                    "§cYou must have the passive, utility, and combat of the §e" +
                    capitalize(orderName) + "§c order equipped to ascend."));
            return;
        }

        // Check 3: influence cost
        if (data.getInfluence() < 1) {
            player.sendSystemMessage(Component.literal(
                    "§cAscending costs 1 influence. You currently have §e" +
                    data.getInfluence() + "§c."));
            return;
        }

        // Store pending
        int expiry = Ascension.getServer() != null ? Ascension.getServer().getTickCount() + 600 : Integer.MAX_VALUE;
        pending.put(player.getUUID(), new PendingPromotion(orderName.toLowerCase(), expiry));

        // Build clickable chat message
        Order order = OrderRegistry.get(orderName);
        MutableComponent msg = Component.literal("Ascend to become the God of ")
                .withStyle(ChatFormatting.GOLD);
        if (order != null) {
            msg = msg.append(Component.literal(capitalize(orderName)).withStyle(
                    Style.EMPTY.withColor(order.getOrderColor())));
        } else {
            msg = msg.append(Component.literal(capitalize(orderName)));
        }
        msg = msg.append(Component.literal("?\n").withStyle(ChatFormatting.GOLD));
        msg = msg.append(Component.literal("[Yes]").withStyle(
                Style.EMPTY.withColor(0x00FF00)
                        .withClickEvent(new ClickEvent.RunCommand("/ascend_confirm " + orderName.toLowerCase()))));
        msg = msg.append(Component.literal(" ").withStyle(ChatFormatting.RESET));
        msg = msg.append(Component.literal("[No]").withStyle(
                Style.EMPTY.withColor(0xFF4444)
                        .withClickEvent(new ClickEvent.RunCommand("/ascend_deny"))));

        player.sendSystemMessage(msg);
    }

    /**
     * Returns and removes the pending promotion for the given player UUID, or {@code null} if
     * none exists or the entry has expired.
     */
    public static PendingPromotion consumePending(UUID playerUUID, String orderName, int currentTick) {
        PendingPromotion p = pending.get(playerUUID);
        if (p == null) return null;
        if (p.expiryTick() <= currentTick) {
            pending.remove(playerUUID);
            return null;
        }
        if (!p.orderName().equalsIgnoreCase(orderName)) return null;
        pending.remove(playerUUID);
        return p;
    }

    /** Removes the pending entry for the given player (called on /ascend_deny). */
    public static boolean cancelPending(UUID playerUUID) {
        return pending.remove(playerUUID) != null;
    }

    /**
     * Inserts a pending promotion entry directly. Use only in game tests to verify the
     * confirmation flow without going through {@link #handlePromotionRequest}.
     */
    public static void addPendingForTesting(UUID playerUUID, PendingPromotion promotion) {
        pending.put(playerUUID, promotion);
    }

    /** Removes all entries whose expiry tick has passed. Call every server tick. */
    public static void cleanExpired(int currentTick) {
        pending.entrySet().removeIf(e -> e.getValue().expiryTick() <= currentTick);
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
