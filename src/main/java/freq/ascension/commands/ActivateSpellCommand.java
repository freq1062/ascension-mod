package freq.ascension.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import freq.ascension.managers.SpellCooldownManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * /activatespell <passive|utility|combat>
 *
 * <p>Activates the player's spell for the requested slot type. Intended for use
 * with client-side keybinding mods that can execute commands on key press.
 */
public class ActivateSpellCommand {

    private static final SuggestionProvider<CommandSourceStack> SLOT_SUGGESTIONS =
            (ctx, builder) -> {
                builder.suggest("passive");
                builder.suggest("utility");
                builder.suggest("combat");
                return builder.buildFuture();
            };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("activatespell")
                .then(Commands.argument("slot", StringArgumentType.word())
                        .suggests(SLOT_SUGGESTIONS)
                        .executes(ctx -> {
                            CommandSourceStack source = ctx.getSource();
                            if (!(source.getEntity() instanceof ServerPlayer player)) {
                                source.sendFailure(Component.literal("Must be a player."));
                                return 0;
                            }
                            String slot = StringArgumentType.getString(ctx, "slot");
                            if (!slot.equals("passive") && !slot.equals("utility") && !slot.equals("combat")) {
                                source.sendFailure(Component.literal(
                                        "Slot must be 'passive', 'utility', or 'combat'."));
                                return 0;
                            }
                            boolean triggered = SpellCooldownManager.triggerSpell(player, slot);
                            if (!triggered) {
                                source.sendFailure(Component.literal(
                                        "No spell available or spell is on cooldown for slot: " + slot));
                                return 0;
                            }
                            return 1;
                        })
                )
        );
    }
}
