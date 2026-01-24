package com.ascension.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.ascension.managers.DivineDataManager;
import com.ascension.managers.Spell;
import com.ascension.managers.SpellCooldownManager;
import org.bukkit.command.TabCompleter;
import java.util.ArrayList;
import java.util.List;

public class BindCommand implements CommandExecutor, TabCompleter {
    private final DivineDataManager dataManager = DivineDataManager.getInstance();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return true;
        }
        if (args.length != 2) {
            player.sendMessage(Component.text("Usage: /bind <slot> <spellId>", NamedTextColor.RED));
            return true;
        }
        int slot;
        try {
            slot = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("Slot must be a number.", NamedTextColor.RED));
            return true;
        }
        if (slot < 1 || slot > 9) {
            player.sendMessage(Component.text("Slot must be between 1 and 9.", NamedTextColor.RED));
            return true;
        }
        String spellId = args[1];
        Spell spell = SpellCooldownManager.get(spellId);
        if (spell == null) {
            player.sendMessage(Component.text("Spell not found: " + spellId, NamedTextColor.RED));
            return true;
        }
        ArrayList<String> unlocked = dataManager.getUnlockedSpellIds(player);

        if (!unlocked.contains(spellId)) {
            player.sendMessage(Component.text("You do not have that spell unlocked.", NamedTextColor.RED));
            return true;
        }
        ArrayList<String> equipped = dataManager.getEquippableSpellids(player);
        if (!equipped.contains(spellId)) {
            player.sendMessage(Component.text("You do not have the associated order equipped.", NamedTextColor.RED));
            return true;
        }

        dataManager.bindSpell(player, slot - 1, spellId); // Subtract 1 from slot
        player.sendMessage(Component.text("Bound " + spellId + " to slot " + slot, NamedTextColor.GREEN));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return null;
        }

        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            // Suggest slots 1-9
            for (int i = 1; i <= 9; i++) {
                completions.add(String.valueOf(i));
            }
        } else if (args.length == 2) {
            // Suggest available spell IDs
            completions.addAll(dataManager.getEquippableSpellids((Player) sender));
        }
        return completions;
    }
}
