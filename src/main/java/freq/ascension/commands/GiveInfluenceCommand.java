package freq.ascension.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import freq.ascension.managers.AscensionData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;

public class GiveInfluenceCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("giveinfluence")
                .requires(source -> source.hasPermission(2)) // Permission level 2 (admins/cheats)
                .then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                .executes(GiveInfluenceCommand::run))));
    }

    private static int run(CommandContext<CommandSourceStack> context) {
        try {
            Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
            int amount = IntegerArgumentType.getInteger(context, "amount");
            int playersGiven = 0;

            for (ServerPlayer player : targets) {
                if (player instanceof AscensionData data) {
                    data.addInfluence(amount); // Using the method from your AscensionData interface
                    player.sendSystemMessage(Component.literal("You received " + amount + " Influence.")
                            .withStyle(style -> style.withColor(0xFFD700))); // Gold color
                    playersGiven++;
                }
            }

            if (playersGiven > 0) {
                context.getSource().sendSuccess(
                        () -> Component.literal("Gave " + amount + " Influence to " + targets.size() + " player(s)."),
                        true);
                return targets.size();
            } else {
                context.getSource().sendFailure(Component.literal("Could not find valid data holder for targets."));
                return 0;
            }

        } catch (CommandSyntaxException e) {
            context.getSource().sendFailure(Component.literal("Error executing command: " + e.getMessage()));
            return 0;
        }
    }
}
