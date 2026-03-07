package freq.ascension.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;

import freq.ascension.managers.AscensionData;
import freq.ascension.managers.GodManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Implements {@code /getloss <player>} and {@code /setloss <player> <amount>}.
 *
 * <p>/getloss requires no permission (usable by any player to inspect a god's loss counter).
 * /setloss requires operator level 2.
 */
public class LossCounterCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("getloss")
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(LossCounterCommand::getLoss)));

        dispatcher.register(Commands.literal("setloss")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                .executes(LossCounterCommand::setLoss))));
    }

    private static int getLoss(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        MinecraftServer server = ctx.getSource().getServer();
        GodManager gm = GodManager.get(server);

        String godOrder = gm.getGodOrderName(target);
        if (godOrder == null) {
            ctx.getSource().sendSuccess(
                    () -> Component.literal("§7" + target.getName().getString() + " is not a god."),
                    false);
            return 0;
        }

        int losses = gm.getLossCounter(godOrder);
        String finalGodOrder = godOrder;
        ctx.getSource().sendSuccess(
                () -> Component.literal("§eGod of " + capitalize(finalGodOrder) + " §7(" +
                        target.getName().getString() + ")§7 — Loss counter: §c" + losses),
                false);
        return 1;
    }

    private static int setLoss(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        MinecraftServer server = ctx.getSource().getServer();
        GodManager gm = GodManager.get(server);

        String godOrder = gm.getGodOrderName(target);
        if (godOrder == null) {
            ctx.getSource().sendFailure(
                    Component.literal("§c" + target.getName().getString() + " is not a god."));
            return 0;
        }

        gm.setLossCounter(godOrder, amount);
        String finalGodOrder = godOrder;
        ctx.getSource().sendSuccess(
                () -> Component.literal("§aSet loss counter for God of " + capitalize(finalGodOrder) +
                        " §7(" + target.getName().getString() + ")§a to §c" + amount + "§a."),
                true);
        return 1;
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
