package freq.ascension.menus;

import java.util.List;
import java.util.ArrayList;
import freq.ascension.registry.OrderRegistry;

import eu.pb4.sgui.api.gui.BookGui;
import freq.ascension.Utils;
import freq.ascension.managers.AscensionData;
import freq.ascension.orders.Order;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

public class AscensionMenu {
        public void open(ServerPlayer player) {
                AscensionData data = (AscensionData) player;
                Order passive = data.getPassive();
                Order utility = data.getUtility();
                Order combat = data.getCombat();

                Component menuIcons = Component.empty()
                                .append(Component.literal(
                                                "\uF806\uE181\uF801\uF801\uF801\uF801\uF801\uF802\uF802\uF804"))
                                .append(makeIcon("A", "End", 2))// end
                                .append(Component.literal("\n\uF806   \uF804"))
                                .append(makeIcon("B", "Magic", 3))
                                .append(Component.literal("\uF802\uF802    "))
                                .append(makeIcon("C", "Ocean", 4))
                                .append(Component.literal("\n\uF806\n\n\uF806 \uF804\uF804\uF804"))
                                .append(makeIcon("D", "Flora", 5))
                                .append(Component.literal("\uF802\uF802\uF802  \uF804 "))
                                .append(makeIcon("E", "Sky", 6))
                                .append(Component.literal("\n\n\n\uF806   "))
                                .append(makeIcon("F", "Nether", 7))
                                .append(Component.literal("\uF802\uF802   \uF804 "))
                                .append(makeIcon("G", "Earth", 8))
                                // .append(Component.literal("\n\uF806\uF802\uF802 "))
                                // .append(makeIcon("H", 8))
                                .withStyle(Style.EMPTY.withColor(0xFFFFFF));

                Component pageContent = Component.empty()
                                .append(Component.literal("   " + Utils.smallCaps("Ascension SMP"))
                                                .withStyle(Style.EMPTY.withColor(ChatFormatting.DARK_PURPLE)
                                                                .withBold(true)))
                                .append("\n")
                                .append(Component.literal("Influence: " + data.getInfluence())
                                                .withStyle(ChatFormatting.BLACK))
                                .append("\n")
                                .append(Component.literal("Passive: ").withStyle(ChatFormatting.BLACK))
                                .append(passive != null
                                                ? Component.literal(Utils.smallCaps(passive.getOrderName()))
                                                                .withColor(passive.getOrderColor().getValue())
                                                : Component.literal("None").withStyle(ChatFormatting.GRAY))
                                .append("\n")
                                .append(Component.literal("Utility: ").withStyle(ChatFormatting.BLACK))
                                .append(utility != null
                                                ? Component.literal(Utils.smallCaps(utility.getOrderName()))
                                                                .withColor(utility.getOrderColor().getValue())
                                                : Component.literal("None").withStyle(ChatFormatting.GRAY))
                                .append("\n")
                                .append(Component.literal("Combat: ").withStyle(ChatFormatting.BLACK))
                                .append(combat != null
                                                ? Component.literal(Utils.smallCaps(combat.getOrderName()))
                                                                .withColor(combat.getOrderColor().getValue())
                                                : Component.literal("None").withStyle(ChatFormatting.GRAY))
                                .append("\n\n")
                                .append(menuIcons);

                ItemStack book = new ItemStack(Items.WRITTEN_BOOK);

                List<Filterable<Component>> pages = new ArrayList<>();
                pages.add(Filterable.passThrough(pageContent));

                for (Order order : OrderRegistry.iterable()) {
                        pages.add(Filterable.passThrough(createOrderPage(order, data)));
                }

                book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                                Filterable.passThrough("AscensionMenu"),
                                "freq1062", 3, pages,
                                true));

                BookGui gui = new BookGui(player, book);
                gui.open();
        }

        private Component makeIcon(String character, String orderName, int page) {
                return Component.literal(character)
                                .withStyle(style -> style
                                                .withColor(0xFFFFFF)
                                                .withClickEvent(new ClickEvent.ChangePage(page))
                                                .withHoverEvent(new HoverEvent.ShowText(
                                                                Component.literal("Go to " + orderName))));
        }

        public Component createOrderPage(Order order, AscensionData data) {
                Order passiveOrder = data.getPassive();
                Order utilityOrder = data.getUtility();
                Order combatOrder = data.getCombat();

                String passiveStatus = passiveOrder == null ? "Locked"
                                : (passiveOrder == order ? "Equipped" : "Unlocked");
                String utilityStatus = utilityOrder == null ? "Locked"
                                : (utilityOrder == order ? "Equipped" : "Unlocked");
                String combatStatus = combatOrder == null ? "Locked"
                                : (combatOrder == order ? "Equipped" : "Unlocked");

                ChatFormatting passiveColor = passiveStatus.equals("Equipped") ? ChatFormatting.GREEN
                                : passiveStatus.equals("Unlocked") ? ChatFormatting.GOLD : ChatFormatting.RED;
                ChatFormatting utilityColor = utilityStatus.equals("Equipped") ? ChatFormatting.GREEN
                                : utilityStatus.equals("Unlocked") ? ChatFormatting.GOLD : ChatFormatting.RED;
                ChatFormatting combatColor = combatStatus.equals("Equipped") ? ChatFormatting.GREEN
                                : combatStatus.equals("Unlocked") ? ChatFormatting.GOLD : ChatFormatting.RED;

                Component title = Component.literal(Utils.smallCaps(order.getOrderName()) + " ")
                                .withStyle(Style.EMPTY.withColor(order.getOrderColor().getValue()).withBold(true));

                Component description = Component.literal(order.getDescription(order.getOrderName()))
                                .withStyle(ChatFormatting.DARK_GRAY);

                return Component.empty()
                                .append(title)
                                .append("\n\n")
                                // Passive
                                .append(Component.literal("Passive: ").withStyle(ChatFormatting.BLACK))
                                .append(Component.literal(passiveStatus).withStyle(passiveColor))
                                .append("\n\n")
                                // Description (placed after Passive as requested)
                                .append(description)
                                .append("\n\n")
                                // Utility
                                .append(Component.literal("Utility: ").withStyle(ChatFormatting.BLACK))
                                .append(Component.literal(utilityStatus).withStyle(utilityColor))
                                .append("\n\n")
                                // Combat
                                .append(Component.literal("Combat: ").withStyle(ChatFormatting.BLACK))
                                .append(Component.literal(combatStatus).withStyle(combatColor))
                                .append("\n\n")
                                // Back Button
                                .append(Component.literal("<< Back")
                                                .withStyle(style -> style
                                                                .withColor(ChatFormatting.BLUE)
                                                                .withUnderlined(true)
                                                                .withClickEvent(new ClickEvent.ChangePage(1))));
        }
}