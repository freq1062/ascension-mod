package freq.ascension.menus;

import java.util.ArrayList;
import java.util.List;

import eu.pb4.sgui.api.gui.BookGui;
import freq.ascension.Utils;
import freq.ascension.managers.AscensionData;
import freq.ascension.orders.Order;
import freq.ascension.registry.OrderRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
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

                // \uF806\uE181\uF801\uF801\uF801\uF801 \uF804\uF804\uE188\n\uF806\uF804
                // \uE183\uF802\uF802 \uF805\uE187\n\uF806\n\n \uE182\uF802\uF802 \uF804 \uE186
                // \n\n\n\uF806 \uE184\uF802\uF802\uF804 \uE185\n\uF806\uF802\uF802H

                MutableComponent menuIcons = Component.empty()
                                .append(Component.literal(
                                                "\uF806\uE181\uF801\uF801\uF801\uF801  \uF804\uF804"))
                                .append(makeIcon("\uE188", "End", 2))// end
                                .append(Component.literal("\n\uF806\uF804 "))
                                .append(makeIcon("\uE183", "Earth", 3))
                                .append(Component.literal("\uF802\uF802 \uF805"))
                                .append(makeIcon("\uE187", "Nether", 4))
                                .append(Component.literal("\n\uF806\n\n   "))
                                .append(makeIcon("\uE182", "Sky", 5))
                                .append(Component.literal("\uF802\uF802  \uF804 "))
                                .append(makeIcon("\uE186", "Flora", 6))
                                .append(Component.literal("\n\n\n\uF806 "))
                                .append(makeIcon("\uE184", "Ocean", 7))
                                .append(Component.literal("\uF802\uF802\uF804 "))
                                .append(makeIcon("\uE185", "Magic", 8))
                                .withStyle(Style.EMPTY.withColor(0xFFFFFF));

                MutableComponent pageContent = Component.empty()
                                .append(Component.literal("   " + Utils.smallCaps("Ascension SMP"))
                                                .withStyle(Style.EMPTY.withColor(ChatFormatting.DARK_PURPLE)
                                                                .withBold(true)))
                                .append(Component.literal("\n"))
                                .append(Component.literal("Passive: ").withStyle(ChatFormatting.BLACK))
                                .append(passive != null
                                                ? Component.literal(Utils.smallCaps(passive.getOrderName()))
                                                                .withColor(passive.getOrderColor().getValue())
                                                : Component.literal("None").withStyle(ChatFormatting.GRAY))
                                .append(Component.literal("\n"))
                                .append(Component.literal("Utility: ").withStyle(ChatFormatting.BLACK))
                                .append(utility != null
                                                ? Component.literal(Utils.smallCaps(utility.getOrderName()))
                                                                .withColor(utility.getOrderColor().getValue())
                                                : Component.literal("None").withStyle(ChatFormatting.GRAY))
                                .append(Component.literal("\n"))
                                .append(Component.literal("Combat: ").withStyle(ChatFormatting.BLACK))
                                .append(combat != null
                                                ? Component.literal(Utils.smallCaps(combat.getOrderName()))
                                                                .withColor(combat.getOrderColor().getValue())
                                                : Component.literal("None").withStyle(ChatFormatting.GRAY))
                                .append(Component.literal("\uF802"))
                                .append(makeInfluenceIcon(data)).withStyle(Style.EMPTY.withColor(0xFFFFFF))
                                .append(Component.literal("\n\n"))
                                .append(menuIcons);

                ItemStack book = new ItemStack(Items.WRITTEN_BOOK);

                List<Filterable<Component>> pages = new ArrayList<>();
                pages.add(Filterable.passThrough(pageContent));

                int currentPageTracker = 1; // We are starting order pages at index 1 (Page 2)

                for (Order order : OrderRegistry.iterable()) {
                        List<Component> orderPages = createOrderPages(order, data, currentPageTracker);
                        for (Component p : orderPages) {
                                pages.add(Filterable.passThrough(p));
                                currentPageTracker++;
                        }
                }

                book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                                Filterable.passThrough("AscensionMenu"),
                                "freq1062", 3, pages,
                                true));

                BookGui gui = new BookGui(player, book);
                gui.open();
        }

        private Component makeInfluenceIcon(AscensionData data) {
                String text = String.valueOf(data.getInfluence());
                int textWidth = text.length() * 6;
                int backstep = 16 + (textWidth / 2);

                String shift = "";
                // Use your -16px and -1px characters to build the shift
                for (int i = 0; i < backstep / 16; i++)
                        shift = shift.concat("\uF801"); // -16px
                for (int i = 0; i < backstep % 16; i++)
                        shift = shift.concat("\uF805"); // -1px

                return Component.empty()
                                .append(Component.literal("\uE189")) // The Icon (Cursor is now at +32)
                                .append(Component.literal(shift)) // Move back to center
                                .append(Component.literal(text).withStyle(ChatFormatting.WHITE));
        }

        private Component makeIcon(String character, String orderName, int page) {
                return Component.literal(character)
                                .withStyle(style -> style
                                                .withColor(0xFFFFFF)
                                                .withClickEvent(new ClickEvent.ChangePage(page))
                                                .withHoverEvent(new HoverEvent.ShowText(
                                                                Component.literal("Go to " + orderName))));
        }

        public List<Component> createOrderPages(Order order, AscensionData data, int startPageIndex) {
                List<Component> finalPages = new ArrayList<>();

                // 1. Prepare the content sections

                List<Component> contentLines = new ArrayList<>();
                contentLines.add(formatSection("Passive", order,
                                data.getUnlockedOrder(order.getOrderName()).hasPassive(),
                                data.getPassive() == order));
                contentLines.add(Component.literal(""));
                contentLines.add(formatSection("Utility", order,
                                data.getUnlockedOrder(order.getOrderName()).hasUtility(),
                                data.getUtility() == order));
                contentLines.add(Component.literal(""));
                contentLines.add(formatSection("Combat", order, data.getUnlockedOrder(order.getOrderName()).hasCombat(),
                                data.getCombat() == order));

                // 2. Split content across pages
                // Lines per page: 14 total.
                // -1 for Header, -1 for Spacer, -1 for Home Button = 11 lines available for
                // content.
                int linesPerPage = 11;
                int currentLineInPage = 0;
                MutableComponent currentPage = Component.empty();
                int localPageCount = 0;

                for (Component section : contentLines) {
                        // Simple approximation: check if section is long.
                        // In a real scenario, you'd split strings by width, but here we assume sections
                        // fit.
                        currentPage.append(section).append(Component.literal("\n"));
                        currentLineInPage += 2; // Rough estimate for Section Name + Description

                        if (currentLineInPage >= linesPerPage) {
                                finalPages.add(finishPage(currentPage, order, startPageIndex + localPageCount));
                                currentPage = Component.empty();
                                currentLineInPage = 0;
                                localPageCount++;
                        }
                }

                // Add the last page if it has content
                if (currentLineInPage > 0 || finalPages.isEmpty()) {
                        finalPages.add(finishPage(currentPage, order, startPageIndex + localPageCount));
                }

                return finalPages;
        }

        private Component finishPage(MutableComponent content, Order order, int globalPageNumber) {
                MutableComponent icon = Component.literal(order.getOrderIcon())
                                .withStyle(Style.EMPTY.withColor(0xFFFFFF));
                MutableComponent name = Component.literal(Utils.smallCaps(order.getOrderName()))
                                .withStyle(Style.EMPTY.withColor(order.getOrderColor().getValue()).withBold(true));

                MutableComponent header = Component.empty();
                // GlobalPageNumber: assuming Menu is Page 1 (index 0).
                // If index 1 (Page 2) is even, icon comes first.
                if ((globalPageNumber + 1) % 2 == 0) {
                        header.append(icon).append(Component.literal(" ")).append(name);
                } else {
                        header.append(name).append(Component.literal(" ")).append(icon);
                }

                // Pin Home button to line 14
                // We count existing newlines in content to see how much padding we need
                int contentLines = content.getString().split("\n").length;
                int paddingNeeded = 12 - contentLines;

                return Component.empty()
                                .append(header).append(Component.literal("\n\n"))
                                .append(content)
                                .append(Component.literal("\n".repeat(Math.max(0, paddingNeeded))))
                                .append(Component.literal("                    ") // Manual right-align pad
                                                .append(Component.literal("<< Home")
                                                                .withStyle(s -> s.withColor(ChatFormatting.BLUE)
                                                                                .withUnderlined(true)
                                                                                .withClickEvent(new ClickEvent.ChangePage(
                                                                                                1)))));
        }

        private Component formatSection(String type, Order order, boolean unlocked,
                        boolean equipped) {

                HoverEvent hover = new HoverEvent.ShowText(
                                Component.literal(equipped ? "Equipped!"
                                                : (unlocked ? "Click to equip!"
                                                                : "Click to unlock (costs 1 influence)")));

                String typeArg = type.toLowerCase();
                // Command: runs on the server to unlock or equip the order, then reopen menu
                String cmd = "/ascension_action " + order.getOrderName() + " " + typeArg;
                ClickEvent click = new ClickEvent.RunCommand(cmd);

                MutableComponent title = Component.literal(type)
                                .withStyle(style -> style.withBold(true)
                                                .withColor(equipped ? ChatFormatting.GREEN : ChatFormatting.BLACK)
                                                .withShadowColor(ChatFormatting.GOLD.getColor())
                                                .withHoverEvent(hover)
                                                .withClickEvent(click));

                MutableComponent icon = Component.literal((unlocked || equipped) ? " \uE18B" : " \uE18A")
                                .withStyle(Style.EMPTY.withColor(0xFFFFFF).withHoverEvent(hover).withClickEvent(click));

                return Component.empty()
                                .append(title)
                                .append(icon)
                                .append(Component.literal("\n"))
                                .append(Component.literal(order.getDescription(type))
                                                .withStyle(ChatFormatting.DARK_GRAY));
        }
}