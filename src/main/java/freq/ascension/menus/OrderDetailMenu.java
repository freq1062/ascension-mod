package com.ascension.menus;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.ascension.Utils;
import com.ascension.managers.DivineDataManager;
import com.ascension.managers.GodSelector;
import com.ascension.managers.OrderRegistry;
import com.ascension.orders.Order;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class OrderDetailMenu {
    private final Order order;
    private final DivineDataManager dm = DivineDataManager.getInstance();

    public OrderDetailMenu(String order) {
        this.order = OrderRegistry.get(order.toLowerCase());
    }

    public void open(Player player) {
        if (!hasAllEquipped(player) || !GodSelector.isInitialPromotionEnabled(order.getOrderName())
                || GodSelector.isOnGodCooldown(player)) {
            Inventory inv = Bukkit.createInventory(null, 9,
                    Component.text(Utils.smallCaps("Order - " + order.getOrderName()))
                            .color(NamedTextColor.DARK_PURPLE)
                            .decoration(TextDecoration.ITALIC, false));
            updateDemigod(player, inv);
            player.openInventory(inv);
        } else {
            Inventory inv = Bukkit.createInventory(null, 18,
                    Component.text(Utils.smallCaps("Order - " + order.getOrderName()))
                            .color(NamedTextColor.DARK_PURPLE)
                            .decoration(TextDecoration.ITALIC, false));
            updateGod(player, inv, true);
            player.openInventory(inv);
        }
    }

    public void update(Player player, Inventory inv) {
        if (!hasAllEquipped(player)) {
            updateDemigod(player, inv);
        } else {
            if (inv.getSize() == 9) {
                // Need to reopen with larger inventory
                player.closeInventory();
                open(player);
            } else {
                updateGod(player, inv, dm.getGodOrder(player) == order.getOrderName());
            }
        }
    }

    public void updateDemigod(Player player, Inventory inv) {
        DivineDataManager dm = DivineDataManager.getInstance();
        inv.setItem(0, dm.getInfluenceDisplayItem(player));

        inv.setItem(2, makeSubIcon(Material.BOOK, "passive",
                dm.isOrderPassiveUnlocked(player, order.getOrderName()), dm.getPassive(player) == order));
        inv.setItem(4, makeSubIcon(Material.IRON_BOOTS, "utility",
                dm.isOrderUtilityUnlocked(player, order.getOrderName()), dm.getUtility(player) == order));
        inv.setItem(6, makeSubIcon(Material.IRON_SWORD, "combat",
                dm.isOrderCombatUnlocked(player, order.getOrderName()), dm.getCombat(player) == order));
        inv.setItem(8, makeBackButton());
    }

    public void updateGod(Player player, Inventory inv, boolean showGlassPanes) {
        updateDemigod(player, inv);

        if (showGlassPanes) {
            inv.setItem(9, new ItemStack(Material.PURPLE_STAINED_GLASS_PANE));
            inv.setItem(10, new ItemStack(Material.YELLOW_STAINED_GLASS_PANE));
            inv.setItem(11, new ItemStack(Material.YELLOW_STAINED_GLASS_PANE));
            inv.setItem(12, new ItemStack(Material.YELLOW_STAINED_GLASS_PANE));
            inv.setItem(14, new ItemStack(Material.YELLOW_STAINED_GLASS_PANE));
            inv.setItem(15, new ItemStack(Material.YELLOW_STAINED_GLASS_PANE));
            inv.setItem(16, new ItemStack(Material.YELLOW_STAINED_GLASS_PANE));
            inv.setItem(17, new ItemStack(Material.PURPLE_STAINED_GLASS_PANE));
        } else {
            // Clear glass panes
            inv.setItem(9, null);
            inv.setItem(10, null);
            inv.setItem(11, null);
            inv.setItem(12, null);
            inv.setItem(14, null);
            inv.setItem(15, null);
            inv.setItem(16, null);
            inv.setItem(17, null);
        }

        inv.setItem(13, makePromotionIcon(player));
        inv.setItem(8, makeBackButton());
    }

    public void playPromotionAnimation(Player player, Inventory inv) {
        org.bukkit.plugin.Plugin plugin = org.bukkit.Bukkit.getPluginManager().getPlugin("AscensionSMP");
        if (plugin == null)
            return;

        // Stage 1: slots 12 and 14
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            inv.setItem(12, new ItemStack(Material.YELLOW_STAINED_GLASS_PANE));
            inv.setItem(14, new ItemStack(Material.YELLOW_STAINED_GLASS_PANE));
        }, 10L);

        // Stage 2: slots 11 and 15
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            inv.setItem(11, new ItemStack(Material.YELLOW_STAINED_GLASS_PANE));
            inv.setItem(15, new ItemStack(Material.YELLOW_STAINED_GLASS_PANE));
        }, 20L);

        // Stage 3: slots 10 and 16
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            inv.setItem(10, new ItemStack(Material.YELLOW_STAINED_GLASS_PANE));
            inv.setItem(16, new ItemStack(Material.YELLOW_STAINED_GLASS_PANE));
        }, 30L);

        // Stage 4: edge slots 9 and 17 (purple)
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            inv.setItem(9, new ItemStack(Material.PURPLE_STAINED_GLASS_PANE));
            inv.setItem(17, new ItemStack(Material.PURPLE_STAINED_GLASS_PANE));
            // Update the promotion icon to reflect god status
            inv.setItem(13, makePromotionIcon(player));
        }, 40L);
    }

    private boolean hasAllEquipped(Player player) {
        return dm.getPassive(player) == order
                && dm.getUtility(player) == order
                && dm.getCombat(player) == order;
    }

    private ItemStack makePromotionIcon(Player player) {
        ItemStack item = order.getOrderItem();
        ItemMeta meta = item.getItemMeta();
        String godOrder = dm.getGodOrder(player);
        boolean ascended = godOrder != null && godOrder.equalsIgnoreCase(order.getOrderName());
        meta.displayName(Component.text(Utils.smallCaps("Ascend"))
                .color(ascended ? NamedTextColor.GOLD : NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, ascended));
        List<Component> lore = new ArrayList<>();
        if (ascended) {
            lore.add(Component.text("You are the god of " + order.getOrderName() + "!")
                    .color(NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("Click to become the god of " + order.getOrderName() + "!")
                    .color(NamedTextColor.LIGHT_PURPLE)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Cost: 1 influence")
                    .color(NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeSubIcon(Material mat, String type, boolean unlocked, boolean equipped) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(Utils.smallCaps(type))
                .color(unlocked ? NamedTextColor.GREEN : NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, equipped));
        List<Component> lore = new ArrayList<>();
        List<Component> description = Utils.wrapToComponents(order.getDescription(type));
        if (description != null) {
            lore.addAll(description.stream().map(c -> c.decoration(TextDecoration.ITALIC, false)).toList());
        }

        lore.add(Component.text(equipped ? "Currently equipped!"
                : (unlocked ? "Click to equip!"
                        : "Click to unlock (costs 1 influence)"))
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack makeBackButton() {
        ItemStack backButton = new ItemStack(Material.ARROW);
        ItemMeta meta = backButton.getItemMeta();
        meta.displayName(Component.text("Back")
                .color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        backButton.setItemMeta(meta);
        return backButton;
    }
}