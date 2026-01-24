package com.ascension.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.ascension.managers.DivineDataManager;

public class GetInfluenceCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("ascension.admin")) {
            sender.sendMessage("You do not have permission to use this command.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("Usage: /getinfluence <player>");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage("§cThat player must be online.");
            return true;
        }

        DivineDataManager dm = DivineDataManager.getInstance();
        if (dm == null) {
            sender.sendMessage("§cData manager not ready.");
            return true;
        }

        int influence = dm.getInfluence(target);
        sender.sendMessage("§e" + target.getName() + "§7 has §e" + influence + "§7 influence.");
        return true;
    }
}
