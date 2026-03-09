package freq.ascension.commands;

import com.mojang.brigadier.CommandDispatcher;
import freq.ascension.Config;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class ReloadConfigCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ascension_reload")
            .requires(src -> src.hasPermission(2))
            .executes(ctx -> {
                Config.load();
                ctx.getSource().sendSuccess(
                    () -> Component.literal("[Ascension] Config reloaded successfully."),
                    true);
                return 1;
            }));
    }
}
