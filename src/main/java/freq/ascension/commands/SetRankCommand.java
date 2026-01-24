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
import com.ascension.managers.DivineDataManager;
import com.ascension.managers.GodSelector;
import com.ascension.managers.OrderRegistry;
import com.ascension.orders.Order;
import com.ascension.weapons.ColossusHammer;
import com.ascension.weapons.TempestTrident;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class SetRankCommand implements CommandExecutor, TabCompleter {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("ascension.admin")) {
            sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /setrank <player|@p|@s|@a> <d/g> [order]", NamedTextColor.RED));
            return true;
        }

        // Handle @a selector (all players)
        if (args[0].equalsIgnoreCase("@a")) {
            String rankArg = args[1].toLowerCase();
            String orderName = args.length >= 3 ? args[2] : null;

            if (rankArg.equals("g") && orderName == null) {
                sender.sendMessage(Component.text("Must specify order when promoting to god: /setrank @a g <order>",
                        NamedTextColor.RED));
                return true;
            }

            int playersSet = 0;
            for (Player target : sender.getServer().getOnlinePlayers()) {
                if (setRank(sender, target, rankArg, orderName)) {
                    playersSet++;
                }
            }

            sender.sendMessage(Component.text("Set rank for " + playersSet + " player(s).", NamedTextColor.GREEN));
            return true;
        }

        Player target = Utils.resolvePlayer(sender, args[0]);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
            return true;
        }

        String rankArg = args[1].toLowerCase();
        String orderName = args.length >= 3 ? args[2] : null;

        if (setRank(sender, target, rankArg, orderName)) {
            sender.sendMessage(
                    Component.text("Set " + target.getName() + "'s rank successfully.", NamedTextColor.GREEN));
        }

        return true;
    }

    private boolean setRank(CommandSender sender, Player target, String rankArg, String orderName) {
        DivineDataManager dm = DivineDataManager.getInstance();
        if (dm == null) {
            sender.sendMessage(Component.text("DivineDataManager not initialized.", NamedTextColor.RED));
            return false;
        }

        if (rankArg.equals("d") || rankArg.equals("demigod")) {
            dm.setRank(target, "demigod");
            dm.setGodOrder(target, null);
            dm.syncToPDC(target);
            target.sendMessage(Component.text("Your rank has been set to Demigod.", NamedTextColor.GOLD));
            return true;

        } else if (rankArg.equals("g") || rankArg.equals("god")) {
            if (orderName == null || orderName.isEmpty()) {
                sender.sendMessage(Component.text(
                        "Must specify order when promoting to god: /setrank <player> g <order>", NamedTextColor.RED));
                return false;
            }

            Order order = OrderRegistry.get(orderName);
            if (order == null) {
                sender.sendMessage(Component.text("Unknown order: " + orderName, NamedTextColor.RED));
                return false;
            }

            dm.setRank(target, "god");
            dm.setGodOrder(target, order.getOrderName());
            GodSelector.removeMythics(target);

            // Equip all three slots with the god order
            dm.setPassive(target, order.getOrderName());
            dm.setUtility(target, order.getOrderName());
            dm.setCombat(target, order.getOrderName());

            // Give mythical weapon for the order
            ItemStack weapon = getMythicalWeapon(order.getOrderName());
            if (weapon != null) {
                target.getInventory().addItem(weapon);
                target.sendMessage(Component.text("You received your mythical weapon!", NamedTextColor.GOLD));
            }

            dm.syncToPDC(target);
            target.sendMessage(Component.text("Your rank has been set to God of " + order.getOrderName() + "!",
                    NamedTextColor.GOLD));
            return true;

        } else {
            sender.sendMessage(Component.text("Invalid rank. Use 'd' for demigod or 'g' for god.", NamedTextColor.RED));
            return false;
        }
    }

    private ItemStack getMythicalWeapon(String orderName) {
        if (orderName == null)
            return null;

        switch (orderName.toLowerCase()) {
            case "ocean":
                return new TempestTrident().createItem();
            case "earth":
                return new ColossusHammer().createItem();
            // Add more weapons as they're implemented
            // case "sky":
            // return new SkyWeapon().createItem();
            default:
                return null;
        }
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
        } else if (args.length == 2) {
            // Suggest rank options
            completions.add("d");
            completions.add("g");
            completions.add("demigod");
            completions.add("god");
        } else if (args.length == 3) {
            // Suggest order names
            for (Order order : OrderRegistry.iterable()) {
                completions.add(order.getOrderName());
            }
        }

        return completions;
    }
}
