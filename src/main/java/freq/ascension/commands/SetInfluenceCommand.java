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

public class SetInfluenceCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("setinfluence")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.argument("amount", IntegerArgumentType.integer(-5))
                                .executes(SetInfluenceCommand::run))));
    }

    private static int run(CommandContext<CommandSourceStack> context) {
        try {
            Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
            int amount = IntegerArgumentType.getInteger(context, "amount");
            int playersSet = 0;

            for (ServerPlayer player : targets) {
                if (player instanceof AscensionData data) {
                    data.setInfluence(amount);
                    player.sendSystemMessage(Component.literal("Your Influence has been set to " + amount + ".")
                            .withStyle(style -> style.withColor(0xFFD700)));
                    playersSet++;
                }
            }

            if (playersSet > 0) {
                final int finalCount = playersSet;
                context.getSource().sendSuccess(
                        () -> Component.literal("Set Influence to " + amount + " for " + finalCount + " player(s)."),
                        true);
                return playersSet;
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

