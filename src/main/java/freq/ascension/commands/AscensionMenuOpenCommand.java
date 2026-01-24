package freq.ascension.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import freq.ascension.menus.AscensionMenu;

public class AscensionMenuOpenCommand {

    // Registers the command "ascension"
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ascension")
                .executes(AscensionMenuOpenCommand::run));
    }

    private static int run(CommandContext<CommandSourceStack> context) {
        try {
            // Throws exception if sender is console or command block
            ServerPlayer player = context.getSource().getPlayerOrException();

            // Logic to open the menu (Ensure AscensionMenu is adapted for Fabric)
            new AscensionMenu().open(player);

            return 1; // Success
        } catch (CommandSyntaxException e) {
            context.getSource().sendFailure(Component.literal("Players only."));
            return 0;
        }
    }
}