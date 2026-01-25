package freq.ascension.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
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
import freq.ascension.managers.Spell;
import freq.ascension.managers.SpellCooldownManager;
import freq.ascension.registry.SpellRegistry;

import java.util.concurrent.CompletableFuture;
import java.util.List;

public class BindCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("bind")
                // Argument 1: Slot (Integer 1-9)
                .then(Commands.argument("slot", IntegerArgumentType.integer(1, 9))
                        // Argument 2: Spell ID (String)
                        .then(Commands.argument("spellId", StringArgumentType.string())
                                .suggests(BindCommand::suggestSpells) // Tab Completion
                                .executes(BindCommand::execute) // Main Logic
                        )));
    }

    private static int execute(CommandContext<CommandSourceStack> context) {
        try {
            // Throws exception if sender is console or command block
            ServerPlayer player = context.getSource().getPlayerOrException();
            AscensionData data = (AscensionData) player;

            // Get arguments directly from context
            int slot = IntegerArgumentType.getInteger(context, "slot");
            String spellId = StringArgumentType.getString(context, "spellId");

            // Logic Check 1: Does spell exist globally?
            Spell spell = SpellCooldownManager.get(spellId);
            if (spell == null) {
                context.getSource()
                        .sendFailure(Component.literal("Spell not found: " + spellId).withStyle(ChatFormatting.RED));
                return 0;
            }

            List<Spell> equippable = SpellRegistry.getEquippableSpells(player);
            List<String> equippableIds = equippable.stream().map(Spell::getId).toList();

            if (!equippableIds.contains(spellId)) {
                Spell s = SpellRegistry.SPELLS.get(spellId);
                context.getSource().sendFailure(
                        Component.literal("That spell requires equipping " + s.getOrder().getOrderName() + " "
                                + s.getType() + "!").withStyle(ChatFormatting.RED));
                return 0;
            }
            // Bind Action
            data.bind(slot - 1, spellId);
            context.getSource().sendSuccess(
                    () -> Component.literal("Bound " + spellId + " to slot " + slot).withStyle(ChatFormatting.GREEN),
                    false);

            return 1; // Success
        } catch (CommandSyntaxException e) {
            context.getSource().sendFailure(Component.literal("Players only."));
            return 0;
        }
    }

    private static CompletableFuture<Suggestions> suggestSpells(CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder) {
        CommandSourceStack source = context.getSource();

        if (source.getEntity() instanceof ServerPlayer player) {
            List<Spell> equippable = SpellRegistry.getEquippableSpells(player);
            List<String> equippableIds = equippable.stream().map(Spell::getId).toList();
            String remaining = builder.getRemaining().toLowerCase();

            for (String spellId : equippableIds) {
                if (spellId.toLowerCase().startsWith(remaining)) {
                    builder.suggest(spellId);
                }
            }
        }
        return builder.buildFuture();
    }
}
