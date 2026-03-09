package freq.ascension.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import freq.ascension.items.ChallengerSigil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;

/**
 * Implements {@code /givesigil <targets> <amount>}.
 * Gives Challenger's Sigil items to the specified players.
 * Requires operator level 2.
 */
public class GiveSigilCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("givesigil")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                .executes(GiveSigilCommand::run))));
    }

    private static int run(CommandContext<CommandSourceStack> context) {
        try {
            Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
            int amount = IntegerArgumentType.getInteger(context, "amount");
            int playersGiven = 0;

            for (ServerPlayer player : targets) {
                for (int i = 0; i < amount; i++) {
                    if (!player.getInventory().add(ChallengerSigil.createSigil())) {
                        player.drop(ChallengerSigil.createSigil(), false);
                    }
                }
                player.sendSystemMessage(Component.literal(
                        "§6You received " + amount + " Challenger's Sigil(s)."));
                playersGiven++;
            }

            if (playersGiven > 0) {
                context.getSource().sendSuccess(
                        () -> Component.literal("Gave " + amount + " Challenger's Sigil(s) to "
                                + targets.size() + " player(s)."),
                        true);
                return targets.size();
            } else {
                context.getSource().sendFailure(Component.literal("No valid targets found."));
                return 0;
            }
        } catch (CommandSyntaxException e) {
            context.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }
}
