package freq.ascension.menus;

import java.util.List;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import freq.ascension.Utils;
import freq.ascension.items.InfluenceItem;
import freq.ascension.managers.AscensionData;
import freq.ascension.orders.Order;
import freq.ascension.registry.OrderRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

public class AscensionMenu {
        public void open(ServerPlayer player) {
                SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x1, player, false);
                gui.setTitle(Component.literal(Utils.smallCaps("Ascension SMP"))
                                .withStyle(Style.EMPTY.withColor(ChatFormatting.DARK_PURPLE).withItalic(false)));

                update(player, gui);
                gui.open();
        }

        public static void update(ServerPlayer player, SimpleGui gui) {
                AscensionData data = (AscensionData) player;
                // Setting items in the container associated with the menu
                gui.setSlot(0, InfluenceItem.getInfluenceDisplayItem(data));

                int i = 0;
                Iterable<Order> orders = OrderRegistry.iterable();
                if (orders != null) {
                        for (Order order : orders) {
                                if (i + 2 < 9) { // Ensure we don't go out of bounds
                                        gui.setSlot(i + 2, new GuiElementBuilder(makeIcon(order, player))
                                                        .setCallback((index, type, action) -> {
                                                                new OrderDetailMenu(order.getOrderName()).open(player);
                                                                // play sound?
                                                        }));
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

                ItemStack item = order.getOrderItem().copy(); // Make sure to copy if getOrderItem returns a static
                                                              // reference

                item.set(DataComponents.ITEM_NAME, Component.literal(
                                Utils.smallCaps(order.getOrderName()))
                                .withColor(order.getOrderColor().getValue())
                                .withStyle(ChatFormatting.BOLD));

                item.set(DataComponents.LORE, new ItemLore(
                                List.of(
                                                Component.literal("Click to view abilities")
                                                                .withStyle(style -> style
                                                                                .withItalic(false)
                                                                                .withColor(ChatFormatting.GRAY)),

                                                Component.literal("Passive")
                                                                .withStyle(style -> style
                                                                                .withColor(passiveUnlocked
                                                                                                ? ChatFormatting.GREEN
                                                                                                : ChatFormatting.RED)
                                                                                .withItalic(false)
                                                                                .withBold(data.getPassive() == order)),

                                                Component.literal("Utility")
                                                                .withStyle(style -> style
                                                                                .withColor(utilityUnlocked
                                                                                                ? ChatFormatting.GREEN
                                                                                                : ChatFormatting.RED)
                                                                                .withItalic(false)
                                                                                .withBold(data.getUtility() == order)),

                                                Component.literal("Combat")
                                                                .withStyle(style -> style
                                                                                .withColor(combatUnlocked
                                                                                                ? ChatFormatting.GREEN
                                                                                                : ChatFormatting.RED)
                                                                                .withItalic(false)
                                                                                .withBold(data.getCombat() == order))

                                )));
                return item;
        }
}