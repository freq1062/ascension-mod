package freq.ascension.commands;

import com.mojang.brigadier.CommandDispatcher;
import freq.ascension.registry.SpellRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class ClearStunCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("clearstun")
            .requires(src -> src.hasPermission(2))
            .then(Commands.argument("player", EntityArgument.player())
                .executes(ctx -> {
                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                    SpellRegistry.clearStun(target.getUUID());
                    ctx.getSource().sendSuccess(() ->
                        Component.literal("Cleared stun for " + target.getName().getString()), true);
                    return 1;
                })
            )
            .executes(ctx -> {
                SpellRegistry.clearAllStuns();
                ctx.getSource().sendSuccess(() ->
                    Component.literal("Cleared all active stuns"), true);
                return 1;
            })
        );
    }
}
