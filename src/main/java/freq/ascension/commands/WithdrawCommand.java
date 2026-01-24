package com.ascension.commands;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.StringUtil;

import com.ascension.items.InfluenceItem;
import com.ascension.managers.DivineDataManager;

public class WithdrawCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (args.length != 1) {
            player.sendMessage("Usage: /withdraw <amount|all>");
            return true;
        }

        DivineDataManager dataManager = DivineDataManager.getInstance();
        if (dataManager == null) {
            player.sendMessage("Internal error.");
            return true;
        }

        int storedInfluence = dataManager.getInfluence(player);
        if (storedInfluence <= 0) {
            player.sendMessage("You have no influence to withdraw.");
            return true;
        }

        int amount;
        if (args[0].equalsIgnoreCase("all")) {
            amount = storedInfluence;
        } else {
            try {
                amount = Integer.parseInt(args[0]);
            } catch (NumberFormatException ex) {
                player.sendMessage("Usage: /withdraw <amount|all>");
                return true;
            }
        }

        if (amount <= 0) {
            player.sendMessage("You must withdraw at least 1 influence.");
            return true;
        }

        if (amount > storedInfluence) {
            player.sendMessage("You cannot withdraw that much influence.");
            return true;
        }

        dataManager.setInfluence(player, storedInfluence - amount);
        giveInfluenceItems(player, amount);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) return Collections.emptyList();
        if (args.length != 1) return Collections.emptyList();

        DivineDataManager dataManager = DivineDataManager.getInstance();
        if (dataManager == null) return Collections.emptyList();

        int storedInfluence = dataManager.getInfluence(player);

        List<String> options = new ArrayList<>();
        options.add("all");
        options.add("1");
        if (storedInfluence > 0) {
            options.add(String.valueOf(storedInfluence));
        }

        String token = args[0] == null ? "" : args[0];
        List<String> completions = new ArrayList<>();
        StringUtil.copyPartialMatches(token, options, completions);
        Collections.sort(completions);
        return completions;
    }

    private void giveInfluenceItems(Player player, int amount) {
        int remaining = amount;
        while (remaining > 0) {
            int stack = Math.min(remaining, 64);
            ItemStack item = InfluenceItem.createItem();
            item.setAmount(stack);

            var leftovers = player.getInventory().addItem(item);
            for (ItemStack leftover : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
            remaining -= stack;
        }
    }
}
