package freq.ascension.menus;

import java.util.List;

import freq.ascension.Utils;
import freq.ascension.items.InfluenceItem;
import freq.ascension.managers.AscensionData;
import freq.ascension.orders.Order;
import freq.ascension.registry.OrderRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;

public class AscensionMenu {

    // In Fabric, we don't just "createInventory", we open a MenuProvider.
    // This method triggers the opening on the server side.
    public void open(ServerPlayer player) {
        Component title = Component.literal(Utils.smallCaps("Ascension SMP"))
                .withStyle(Style.EMPTY.withColor(ChatFormatting.DARK_PURPLE).withItalic(false));

        SimpleMenuProvider menuProvider = new SimpleMenuProvider(
                (syncId, playerInventory, playerEntity) -> {
                    // Create a 9-slot (1 row) generic chest menu
                    ChestMenu menu = new ChestMenu(MenuType.GENERIC_9x1, syncId, playerInventory,
                            new net.minecraft.world.SimpleContainer(9), 1);
                    update(playerEntity, menu);
                    return menu;
                },
                title);

        player.openMenu(menuProvider);
    }

    public static void update(Player player, ChestMenu menu) {
        // Setting items in the container associated with the menu
        menu.getContainer().setItem(0, InfluenceItem.getInfluenceDisplayItem(player));

        int i = 0;
        Iterable<Order> orders = OrderRegistry.iterable();
        if (orders != null) {
            for (Order order : orders) {
                if (i + 2 < 9) { // Ensure we don't go out of bounds
                    menu.getContainer().setItem(i + 2, makeIcon(order, player));
                }
                i++;
            }
        }
    }

    private static ItemStack makeIcon(Order order, Player player) {
        AscensionData data = (AscensionData) player;
        boolean passiveUnlocked = data.getUnlockedOrder(order.getOrderName()).hasPassive();
        boolean utilityUnlocked = data.getUnlockedOrder(order.getOrderName()).hasUtility();
        boolean combatUnlocked = data.getUnlockedOrder(order.getOrderName()).hasCombat();

        ItemStack item = order.getOrderItem().copy(); // Make sure to copy if getOrderItem returns a static reference

        item.set(DataComponents.ITEM_NAME, Component.literal(
                Utils.smallCaps(order.getOrderName()))
                .withColor(order.getOrderColor().getValue())
                .withStyle(ChatFormatting.BOLD));

        item.set(DataComponents.LORE, new ItemLore(
                List.of(
                        Component.literal("Click to view abilities").withStyle(ChatFormatting.GRAY),

                        Component.literal("Passive")
                                .withStyle(style -> style
                                        .withColor(passiveUnlocked ? ChatFormatting.GREEN : ChatFormatting.RED)
                                        .withItalic(false)
                                        .withBold(data.getPassive() == order)),

                        Component.literal("Utility")
                                .withStyle(style -> style
                                        .withColor(utilityUnlocked ? ChatFormatting.GREEN : ChatFormatting.RED)
                                        .withItalic(false)
                                        .withBold(data.getPassive() == order)),

                        Component.literal("Passive")
                                .withStyle(style -> style
                                        .withColor(combatUnlocked ? ChatFormatting.GREEN : ChatFormatting.RED)
                                        .withItalic(false)
                                        .withBold(data.getPassive() == order))

                )));
        return item;
    }
}