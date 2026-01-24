package com.ascension.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.ascension.managers.DivineDataManager;

public class SetInfluenceCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("ascension.admin")) {
            sender.sendMessage("You do not have permission to use this command.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("Usage: /setinfluence <player> <amount>");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage("§cThat player must be online.");
            return true;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cAmount must be a number.");
            return true;
        }
        if (amount < 0)
            amount = 0;

        DivineDataManager dm = DivineDataManager.getInstance();
        if (dm == null) {
            sender.sendMessage("§cData manager not ready.");
            return true;
        }

        dm.setInfluence(target, amount);
        sender.sendMessage("§aSet §e" + target.getName() + "§a influence to §e" + amount + "§a.");
        if (sender instanceof Player p && p.getUniqueId().equals(target.getUniqueId())) {
            // no-op
        } else {
            target.sendMessage("§aYour influence was set to §e" + amount + "§a.");
        }
        return true;
    }
}
