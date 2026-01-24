package com.ascension.commands;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.ascension.Utils;
import com.ascension.items.InfluenceItem;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class GiveInfluenceCommand implements CommandExecutor, TabCompleter {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("ascension.admin")) {
            sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /giveinfluence <player|@p|@s|@a> <count>", NamedTextColor.RED));
            return true;
        }

        // Handle @a selector (all players)
        if (args[0].equalsIgnoreCase("@a")) {
            int amount;
            try {
                amount = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("Invalid number for count.", NamedTextColor.RED));
                return true;
            }

            if (amount <= 0) {
                sender.sendMessage(Component.text("Count must be a positive number.", NamedTextColor.RED));
                return true;
            }

            int playersGiven = 0;
            for (Player target : sender.getServer().getOnlinePlayers()) {
                ItemStack influence = InfluenceItem.createItem();
                influence.setAmount(amount);
                target.getInventory().addItem(influence);
                target.sendMessage(Component.text("You received " + amount + " Influence.", NamedTextColor.GOLD));
                playersGiven++;
            }

            sender.sendMessage(Component.text("Gave " + amount + " Influence to " + playersGiven + " player(s).",
                    NamedTextColor.GREEN));
            return true;
        }

        Player target = Utils.resolvePlayer(sender, args[0]);

        if (target == null) {
            sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
            return true;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Invalid number for count.", NamedTextColor.RED));
            return true;
        }

        if (amount <= 0) {
            sender.sendMessage(Component.text("Count must be a positive number.", NamedTextColor.RED));
            return true;
        }

        ItemStack influence = InfluenceItem.createItem();
        influence.setAmount(amount);
        target.getInventory().addItem(influence);
        target.sendMessage(Component.text("You received " + amount + " Influence.", NamedTextColor.GOLD));
        sender.sendMessage(
                Component.text("Gave " + amount + " Influence to " + target.getName() + ".", NamedTextColor.GREEN));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return null;
        }

        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            // Suggest online player names and selectors
            completions.add("@s");
            completions.add("@p");
            completions.add("@a");
            for (Player player : sender.getServer().getOnlinePlayers()) {
                completions.add(player.getName());
            }
        }
        return completions;
    }
}
