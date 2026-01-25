package freq.ascension.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;

import freq.ascension.managers.AscensionData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class GetInfluenceCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("getinfluence")
                .requires(source -> source.hasPermission(2)) // Permission level 2 (op)
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(GetInfluenceCommand::run)));
    }

    private static int run(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer target = EntityArgument.getPlayer(context, "target");
            AscensionData data = (AscensionData) target;
            CommandSourceStack source = context.getSource();

            int influence = data.getInfluence();
            source.sendSuccess(
                    () -> Component
                            .literal("§e" + target.getName().getString() + "§7 has §e" + influence + "§7 influence."),
                    false);

            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("An error occurred while querying influence."));
            return 0;
        }
    }
}
