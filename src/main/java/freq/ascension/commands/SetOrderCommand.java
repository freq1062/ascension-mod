package freq.ascension.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import freq.ascension.managers.AscensionData;
import freq.ascension.orders.Order;
import freq.ascension.registry.OrderRegistry;

public class SetOrderCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("set")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("type", StringArgumentType.word())
                                .then(Commands.argument("order", StringArgumentType.word())
                                        .executes(SetOrderCommand::setOrder)))));
    }

    private static int setOrder(CommandContext<CommandSourceStack> context) {
        ServerPlayer target;
        try {
            target = EntityArgument.getPlayer(context, "player");
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Player not found."));
            return 0;
        }
        String type = StringArgumentType.getString(context, "type").toLowerCase();
        String orderName = StringArgumentType.getString(context, "order");

        AscensionData data = (AscensionData) target;
        Order order = OrderRegistry.get(orderName);
        if (order == null) {
            context.getSource().sendFailure(Component.literal("Order not found: " + orderName));
            return 0;
        }
        switch (type) {
            case "passive" -> data.setPassive(orderName);
            case "utility" -> data.setUtility(orderName);
            case "combat" -> data.setCombat(orderName);
            default -> {
                context.getSource()
                        .sendFailure(Component.literal("Invalid type: " + type + ". Use passive, utility, or combat."));
                return 0;
            }
        }
        context.getSource().sendSuccess(
                () -> Component
                        .literal("Set " + type + " order for " + target.getName().getString() + " to " + orderName),
                true);
        return 1;
    }
}
