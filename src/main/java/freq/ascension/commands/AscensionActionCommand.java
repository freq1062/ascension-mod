package freq.ascension.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import freq.ascension.managers.AscensionData;
import freq.ascension.menus.AscensionMenu;
import freq.ascension.orders.Order;
import freq.ascension.registry.OrderRegistry;

public class AscensionActionCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ascension_action")
                .then(Commands.argument("order", StringArgumentType.word())
                        .then(Commands.argument("type", StringArgumentType.word())
                                .executes(AscensionActionCommand::run))));
    }

    private static int run(CommandContext<CommandSourceStack> context) {
        ServerPlayer player;
        try {
            player = context.getSource().getPlayerOrException();
        } catch (CommandSyntaxException e) {
            context.getSource().sendFailure(Component.literal("Players only."));
            return 0;
        }

        String orderName = StringArgumentType.getString(context, "order");
        String type = StringArgumentType.getString(context, "type").toLowerCase();

        Order order = OrderRegistry.get(orderName);
        if (order == null) {
            context.getSource().sendFailure(Component.literal("Order not found: " + orderName));
            return 0;
        }

        AscensionData data = (AscensionData) player;

        // Check unlocked state
        boolean unlocked;
        switch (type) {
            case "passive" -> unlocked = data.getUnlockedOrder(orderName).hasPassive();
            case "utility" -> unlocked = data.getUnlockedOrder(orderName).hasUtility();
            case "combat" -> unlocked = data.getUnlockedOrder(orderName).hasCombat();
            default -> {
                context.getSource().sendFailure(Component.literal("Invalid type: " + type + ". Use passive, utility, or combat."));
                return 0;
            }
        }

        if (!unlocked) {
            // Unlock it
            data.unlock(orderName, type);
            context.getSource().sendSuccess(() -> Component.literal("Unlocked " + orderName + " (" + type + ")"), true);
        } else {
            // If unlocked but not equipped, equip it
            boolean alreadyEquipped = switch (type) {
                case "passive" -> data.getPassive() == order;
                case "utility" -> data.getUtility() == order;
                case "combat" -> data.getCombat() == order;
                default -> false;
            };

            if (!alreadyEquipped) {
                switch (type) {
                    case "passive" -> data.setPassive(orderName);
                    case "utility" -> data.setUtility(orderName);
                    case "combat" -> data.setCombat(orderName);
                }
                context.getSource().sendSuccess(() -> Component.literal("Equipped " + orderName + " (" + type + ")"), true);
            } else {
                context.getSource().sendSuccess(() -> Component.literal(orderName + " (" + type + ") is already equipped."), false);
            }
        }

        // Reopen the menu to refresh the book UI
        new AscensionMenu().open(player);

        return 1;
    }
}
