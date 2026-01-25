package freq.ascension.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import freq.ascension.managers.AscensionData;
import freq.ascension.managers.SpellCooldownManager;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class UnbindCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("unbind")
                // Argument 1: Spell ID (String) or "all"
                .then(Commands.argument("spellId", StringArgumentType.string())
                        .suggests(UnbindCommand::suggestBoundSpells) // Tab Completion
                        .executes(UnbindCommand::execute) // Main Logic
                ));
    }

    private static int execute(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            AscensionData data = (AscensionData) player;
            String arg = StringArgumentType.getString(context, "spellId");

            // Handle "all" case
            if (arg.equalsIgnoreCase("all")) {
                data.unbindAll();
                context.getSource().sendSuccess(
                        () -> Component.literal("All spell bindings cleared.").withStyle(ChatFormatting.GREEN), false);
                return 1;
            }

            // Verify spell exists in general registry
            if (SpellCooldownManager.get(arg) == null) {
                context.getSource().sendFailure(
                        Component.literal("Spell not found: " + arg).withStyle(ChatFormatting.RED));
                return 0;
            }

            // Verify spell is actually bound
            Map<Integer, String> bindings = data.getSpellBindings();
            boolean found = bindings.values().stream().anyMatch(s -> s.equals(arg));
            if (!found) {
                context.getSource().sendFailure(
                        Component.literal("That spell is not bound.").withStyle(ChatFormatting.RED));
                return 0;
            }

            // Unbind logic
            data.unbind(arg);
            context.getSource().sendSuccess(
                    () -> Component.literal("Unbound " + arg).withStyle(ChatFormatting.GREEN), false);

            return 1;
        } catch (CommandSyntaxException e) {
            context.getSource().sendFailure(Component.literal("Players only."));
            return 0;
        }
    }

    private static CompletableFuture<Suggestions> suggestBoundSpells(CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder) {
        CommandSourceStack source = context.getSource();

        if (source.getEntity() instanceof ServerPlayer player) {
            AscensionData data = (AscensionData) player;
            String remaining = builder.getRemaining().toLowerCase();

            // Suggest "all"
            if ("all".startsWith(remaining)) {
                builder.suggest("all");
            }

            // Suggest currently bound spells
            for (String boundSpellId : data.getSpellBindings().values()) {
                if (boundSpellId.toLowerCase().startsWith(remaining)) {
                    builder.suggest(boundSpellId);
                }
            }
        }
        return builder.buildFuture();
    }
}
