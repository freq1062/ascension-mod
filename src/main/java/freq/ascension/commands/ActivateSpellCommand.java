package freq.ascension.commands;

import com.mojang.brigadier.CommandDispatcher;
import freq.ascension.managers.SpellCooldownManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * /activatespell
 *
 * <p>Activates the spell bound to the player's currently held hotbar slot.
 * Intended for use with client-side keybinding mods that can execute commands on key press.
 */
public class ActivateSpellCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("activatespell")
                .executes(ctx -> {
                    CommandSourceStack source = ctx.getSource();
                    if (!(source.getEntity() instanceof ServerPlayer player)) {
                        source.sendFailure(Component.literal("Must be a player."));
                        return 0;
                    }
                    boolean triggered = SpellCooldownManager.triggerSpellAtCurrentSlot(player);
                    if (!triggered) {
                        source.sendFailure(Component.literal(
                                "No spell bound in your current hotbar slot."));
                        return 0;
                    }
                    return 1;
                })
        );
    }
}
