package com.ascension.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import com.ascension.managers.DivineDataManager;
import com.ascension.managers.SpellCooldownManager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class UnbindCommand implements CommandExecutor, TabCompleter {
    private final DivineDataManager dataManager = DivineDataManager.getInstance();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (args.length != 1) {
            player.sendMessage(Component.text("Usage: /unbind <spellId|all>", NamedTextColor.RED));
            return true;
        }
        String arg = args[0];
        if (arg.equalsIgnoreCase("all")) {
            dataManager.unbindAll(player);
            player.sendMessage(Component.text("All spell bindings cleared.", NamedTextColor.GREEN));
            return true;
        }

        String spellId = arg;
        if (SpellCooldownManager.get(spellId) == null) {
            player.sendMessage(Component.text("Spell not found: " + spellId, NamedTextColor.RED));
            return true;
        }

        Map<Integer, String> bindings = dataManager.getSpellBindings(player);
        boolean found = bindings.values().stream().anyMatch(s -> s.equals(spellId));
        if (!found) {
            player.sendMessage(Component.text("That spell is not bound.", NamedTextColor.RED));
            return true;
        }

        dataManager.unbindSpell(player, spellId);
        player.sendMessage(Component.text("Unbound " + spellId, NamedTextColor.GREEN));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return null;
        }

        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.addAll(dataManager.getSpellBindings((Player) sender).values());
            completions.add("all");
        }
        return completions;
    }
}
