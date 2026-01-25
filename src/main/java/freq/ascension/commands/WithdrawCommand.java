package freq.ascension.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import freq.ascension.items.InfluenceItem;
import freq.ascension.managers.AscensionData;

public class WithdrawCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("withdraw")
                .then(Commands.literal("all")
                        .executes(context -> withdraw(context, -1))) // Use -1 as a flag for "all"
                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                        .executes(context -> withdraw(context, IntegerArgumentType.getInteger(context, "amount")))));
    }

    private static int withdraw(CommandContext<CommandSourceStack> context, int requestedAmount) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            AscensionData data = (AscensionData) player;

            int storedInfluence = data.getInfluence();
            if (storedInfluence <= 0) {
                context.getSource().sendFailure(Component.literal("You have no influence to withdraw."));
                return 0;
            }

            int amountToWithdraw;
            // If requestedAmount is -1 (from 'all'), take everything. Otherwise take the
            // number.
            if (requestedAmount == -1) {
                amountToWithdraw = storedInfluence;
            } else {
                amountToWithdraw = requestedAmount;

                if (amountToWithdraw > storedInfluence) {
                    context.getSource().sendFailure(Component
                            .literal("You cannot withdraw that much influence (Current: " + storedInfluence + ")."));
                    return 0;
                }
            }

            // Deduct influence
            data.setInfluence(storedInfluence - amountToWithdraw);

            // Give items
            giveInfluenceItems(player, amountToWithdraw);

            context.getSource().sendSuccess(() -> Component.literal("Withdrew " + amountToWithdraw + " influence."),
                    true);
            return 1;

        } catch (CommandSyntaxException e) {
            context.getSource().sendFailure(Component.literal("Only players can use this command."));
            return 0;
        } catch (ClassCastException e) {
            context.getSource().sendFailure(Component.literal("Internal error: Player data capabilities missing."));
            return 0;
        }
    }

    private static void giveInfluenceItems(ServerPlayer player, int amount) {
        int remaining = amount;
        while (remaining > 0) {
            int stackSize = Math.min(remaining, 64);

            // Assuming InfluenceItem.createItem() returns a
            // net.minecraft.world.item.ItemStack
            ItemStack item = InfluenceItem.createItem();
            item.setCount(stackSize);

            // Attempt to add to inventory
            boolean added = player.getInventory().add(item);

            // If the inventory was full or couldn't take the whole stack, drop the
            // remainder
            if (!added || !item.isEmpty()) {
                player.drop(item, false);
            }

            remaining -= stackSize;
        }
    }
}
