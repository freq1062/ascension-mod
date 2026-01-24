package freq.ascension.menus;

import java.util.ArrayList;
import java.util.List;

import freq.ascension.Ascension;
import freq.ascension.Utils;
import freq.ascension.items.InfluenceItem;
import freq.ascension.managers.AscensionData;
import freq.ascension.orders.Order;
import freq.ascension.registry.OrderRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

public class OrderDetailMenu {
    private final Order order;

    public OrderDetailMenu(String order) {
        this.order = OrderRegistry.get(order.toLowerCase());
    }

    public boolean hasAllEquipped(ServerPlayer player) {
        AscensionData data = (AscensionData) player;
        return data.getPassive() == order && data.getUtility() == order && data.getCombat() == order;
    }

    public void open(ServerPlayer player) {

        AscensionData data = (AscensionData) player;
        Component title = Component.literal(Utils.smallCaps("Order - " + order.getOrderName()))
                .withStyle(Style.EMPTY.withColor(ChatFormatting.DARK_PURPLE).withItalic(false));
        SimpleMenuProvider menuProvider;

        if (hasAllEquipped(player)) {
            menuProvider = new SimpleMenuProvider(
                    (syncId, playerInventory, playerEntity) -> {
                        ChestMenu menu = new ChestMenu(MenuType.GENERIC_9x2, syncId, playerInventory,
                                new net.minecraft.world.SimpleContainer(9), 1);
                        updateGod(data, menu);
                        return menu;
                    },
                    title);
        } else {
            menuProvider = new SimpleMenuProvider(
                    (syncId, playerInventory, playerEntity) -> {
                        // Create a 9-slot (1 row) generic chest menu
                        ChestMenu menu = new ChestMenu(MenuType.GENERIC_9x1, syncId, playerInventory,
                                new net.minecraft.world.SimpleContainer(9), 1);
                        updateDemigod(data, menu);
                        return menu;
                    },
                    title);
        }

        player.openMenu(menuProvider);
    }

    public void updateDemigod(AscensionData data, ChestMenu menu) {
        menu.getContainer().setItem(0, InfluenceItem.getInfluenceDisplayItem(player));

        menu.getContainer().setItem(2, makeSubIcon(Items.BOOK, "passive",
                data.getUnlockedOrder(order.getOrderName()).hasPassive(), data.getPassive() == order));
        menu.getContainer().setItem(4, makeSubIcon(Items.IRON_BOOTS, "utility",
                data.getUnlockedOrder(order.getOrderName()).hasUtility(), data.getUtility() == order));
        menu.getContainer().setItem(6, makeSubIcon(Items.IRON_SWORD, "combat",
                data.getUnlockedOrder(order.getOrderName()).hasCombat(), data.getCombat() == order));
        menu.getContainer().setItem(8, makeBackButton());
    }

    public void updateGod(AscensionData data, ChestMenu menu) {
        updateDemigod(data, menu);

        menu.getContainer().setItem(9, new ItemStack(Items.PURPLE_STAINED_GLASS_PANE));
        for (int i = 10; i < 17; i++) {
            if (i != 13)
                menu.getContainer().setItem(i, new ItemStack(Items.YELLOW_STAINED_GLASS_PANE));
        }
        menu.getContainer().setItem(17, new ItemStack(Items.PURPLE_STAINED_GLASS_PANE));

        menu.getContainer().setItem(13, makePromotionIcon(data.getGodOrder() == order.getOrderName()));
    }

    // public void playPromotionAnimation(Player player, Inventory inv) {
    // org.bukkit.plugin.Plugin plugin =
    // org.bukkit.Bukkit.getPluginManager().getPlugin("AscensionSMP");
    // if (plugin == null)
    // return;

    // // Stage 1: slots 12 and 14
    // org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
    // inv.setItem(12, new ItemStack(Material.YELLOW_STAINED_GLASS_PANE));
    // inv.setItem(14, new ItemStack(Material.YELLOW_STAINED_GLASS_PANE));
    // }, 10L);

    // // Stage 2: slots 11 and 15
    // org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
    // inv.setItem(11, new ItemStack(Material.YELLOW_STAINED_GLASS_PANE));
    // inv.setItem(15, new ItemStack(Material.YELLOW_STAINED_GLASS_PANE));
    // }, 20L);

    // // Stage 3: slots 10 and 16
    // org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
    // inv.setItem(10, new ItemStack(Material.YELLOW_STAINED_GLASS_PANE));
    // inv.setItem(16, new ItemStack(Material.YELLOW_STAINED_GLASS_PANE));
    // }, 30L);

    // // Stage 4: edge slots 9 and 17 (purple)
    // org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
    // inv.setItem(9, new ItemStack(Material.PURPLE_STAINED_GLASS_PANE));
    // inv.setItem(17, new ItemStack(Material.PURPLE_STAINED_GLASS_PANE));
    // // Update the promotion icon to reflect god status
    // inv.setItem(13, makePromotionIcon(player));
    // }, 40L);
    // }

    private ItemStack makePromotionIcon(boolean currentlyGod) {
        ItemStack icon = order.getOrderItem().copy();
        icon.set(DataComponents.ITEM_NAME, Component.literal(
                Utils.smallCaps(currentlyGod ? "Ascend!" : "Ascended"))
                .withStyle(style -> style
                        .withColor(currentlyGod ? TextColor.fromRgb(ChatFormatting.GOLD.getColor())
                                : order.getOrderColor())
                        .withBold(currentlyGod)
                        .withItalic(false)));

        List<Component> lore = List.of(
                Component
                        .literal((currentlyGod ? "You are the god of " + order.getOrderName() + "!"
                                : "Click to become the god of ") + order.getOrderName() + "!")
                        .withStyle(style -> style
                                .withColor(ChatFormatting.GOLD)
                                .withShadowColor(ChatFormatting.WHITE.getColor())));
        if (!currentlyGod)
            lore.add(Utils.costComponent(1));

        icon.set(DataComponents.LORE, new ItemLore(lore));
        return icon;
    }

    private ItemStack makeSubIcon(Item item, String type, boolean unlocked, boolean equipped) {
        ItemStack icon = new ItemStack(item);
        icon.set(DataComponents.ITEM_NAME, Component.literal(
                Utils.smallCaps(type))
                .withStyle(unlocked ? ChatFormatting.GREEN : ChatFormatting.RED)
                .withStyle(ChatFormatting.BOLD));

        List<Component> lore = new ArrayList<>(Utils.wrapToComponents(order.getDescription(type)));
        lore.add(Component
                .literal(equipped ? "Currently equipped!"
                        : (unlocked ? "Click to equip!" : "Click to unlock (costs 1 influence)"))
                .withStyle(ChatFormatting.GRAY));

        icon.set(DataComponents.LORE, new ItemLore(lore));
        return icon;
    }

    private static ItemStack makeBackButton() {
        ItemStack backButton = new ItemStack(Items.ARROW);
        backButton.set(DataComponents.ITEM_NAME, Component.literal("back").withStyle(ChatFormatting.YELLOW));
        return backButton;
    }
}