package freq.ascension.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import freq.ascension.managers.GodManager;
import freq.ascension.managers.PromotionHandler;
import freq.ascension.orders.Order;
import freq.ascension.registry.OrderRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Implements {@code /ascend_confirm <order>} and {@code /ascend_deny}.
 *
 * <p>These commands are intended to be run only via the clickable chat prompt sent by
 * {@link PromotionHandler}. They require no special permission (any player may run them
 * since they are guarded by the pending-confirmation check instead).
 */
public class AscendConfirmCommand {

    private static final SuggestionProvider<CommandSourceStack> ORDER_SUGGESTIONS =
            (ctx, builder) -> {
                List<String> orders = new ArrayList<>();
                for (Order order : OrderRegistry.iterable()) {
                    orders.add(order.getOrderName());
                }
                return SharedSuggestionProvider.suggest(orders, builder);
            };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ascend_confirm")
                .then(Commands.argument("order", StringArgumentType.word())
                        .suggests(ORDER_SUGGESTIONS)
                        .executes(AscendConfirmCommand::confirmAscend)));

        dispatcher.register(Commands.literal("ascend_deny")
                .executes(AscendConfirmCommand::denyAscend));
    }

    private static int confirmAscend(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player;
        try {
            player = ctx.getSource().getPlayerOrException();
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("§cThis command must be run by a player."));
            return 0;
        }

        String orderName = StringArgumentType.getString(ctx, "order").toLowerCase();
        MinecraftServer server = ctx.getSource().getServer();
        int currentTick = server.getTickCount();

        PromotionHandler.PendingPromotion p = PromotionHandler.consumePending(
                player.getUUID(), orderName, currentTick);
        if (p == null) {
            player.sendSystemMessage(Component.literal(
                    "§cNo pending ascension request found for §e" + capitalize(orderName) +
                    "§c. The request may have expired (30 seconds)."));
            return 0;
        }

        Order order = OrderRegistry.get(orderName);
        if (order == null) {
            player.sendSystemMessage(Component.literal("§cInvalid order for ascension."));
            return 0;
        }

        // Deduct influence before promoting
        freq.ascension.managers.AscensionData data = (freq.ascension.managers.AscensionData) player;
        if (data.getInfluence() < 1) {
            player.sendSystemMessage(Component.literal(
                    "§cAscension costs 1 influence. You no longer have enough influence."));
            return 0;
        }
        data.addInfluence(-1);

        GodManager gm = GodManager.get(server);
        gm.promoteToGod(player, order, server);

        player.sendSystemMessage(Component.literal(
                "§6⚔ You have ascended to become the God of " + capitalize(orderName) + "!"));
        return 1;
    }

    private static int denyAscend(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player;
        try {
            player = ctx.getSource().getPlayerOrException();
        } catch (Exception e) {
            return 0;
        }

        PromotionHandler.cancelPending(player.getUUID());
        player.sendSystemMessage(Component.literal("§7Ascension cancelled."));
        return 1;
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
