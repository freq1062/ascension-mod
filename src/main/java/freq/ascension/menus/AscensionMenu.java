package freq.ascension.menus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import eu.pb4.sgui.api.gui.BookGui;
import freq.ascension.Config;
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
        private static record Section(String type, boolean unlocked, boolean equipped) {
        }

        public void open(ServerPlayer player) {
                open(player, 1);
        }

        public void open(ServerPlayer player, int openPage) {
                AscensionData data = (AscensionData) player;
                Order passive = data.getPassive();
                Order utility = data.getUtility();
                Order combat = data.getCombat();

                // \uF806\uE181\uF801\uF801\uF801\uF801 \uF804\uF804\uE188\n\uF806\uF804
                // \uE183\uF802\uF802 \uF805\uE187\n\uF806\n\n \uE182\uF802\uF802 \uF804 \uE186
                // \n\n\n\uF806 \uE184\uF802\uF802\uF804 \uE185\n\uF806\uF802\uF802H

                // menuIcons will be constructed after we know page indices for each order
                MutableComponent menuIcons = Component.empty();

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
                                .append(Component.literal("\n"));

                List<Filterable<Component>> pages = new ArrayList<>();
                // Add the main menu as page 1
                pages.add(Filterable.passThrough(pageContent));

                int currentPageTracker = 2; // Next page index (page numbers are 1-based)

                Map<String, Integer> firstPageMap = new HashMap<>();

                for (Order order : OrderRegistry.iterable()) {
                        firstPageMap.put(order.getOrderName().toLowerCase(), currentPageTracker);
                        List<Component> orderPages = createOrderPages(order, data, currentPageTracker);
                        for (Component p : orderPages) {
                                pages.add(Filterable.passThrough(p));
                                currentPageTracker++;
                        }
                }

                // Now construct the menu icons with the correct page links. If an order isn't
                // present in the registry, or mapping is missing, clicking will go home (page
                // 1).
                menuIcons = Component.empty()
                                .append(Component.literal("\uF806\uE181\uF801\uF801\uF801\uF801\uF804\uF804\n"))
                                .append(Component.literal("\uF802\uF802\uF804   "))
                                .append(makeIcon("\uE188", "End", firstPageMap.getOrDefault("end", 1)))
                                .append(Component.literal("\n\uF806\uF804 "))
                                .append(makeIcon("\uE183", "Earth", firstPageMap.getOrDefault("earth", 1)))
                                .append(Component.literal("\uF802\uF802 \uF805"))
                                .append(makeIcon("\uE187", "Nether", firstPageMap.getOrDefault("nether", 1)))
                                .append(Component.literal("\n\uF806\n\n   "))
                                .append(makeIcon("\uE182", "Sky", firstPageMap.getOrDefault("sky", 1)))
                                .append(Component.literal("\uF802\uF802  \uF804 "))
                                .append(makeIcon("\uE186", "Flora", firstPageMap.getOrDefault("flora", 1)))
                                .append(Component.literal("\n\n\n\uF806 "))
                                .append(makeIcon("\uE184", "Ocean", firstPageMap.getOrDefault("ocean", 1)))
                                .append(Component.literal("\uF802\uF802\uF804 "))
                                .append(makeIcon("\uE185", "Magic", firstPageMap.getOrDefault("magic", 1)));

                pageContent.append(menuIcons);

                ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
                book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                                Filterable.passThrough("AscensionMenu"),
                                player.getName().getString(), 0, pages, true));

                BookGui gui = new BookGui(player, book);
                // BookGui currently only supports open(); don't reopen to a specific page here.
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
                int maxLines = 11; // Header (1) + Spacer (1) + Home (1) + 11 content lines = 14
                int currentLineCount = 0;
                MutableComponent currentPage = Component.empty();
                int localPageCount = 0;

                List<Section> sections = new ArrayList<>();
                sections.add(new Section("Passive", data.getUnlockedOrder(order.getOrderName()).hasPassive(),
                                data.getPassive() == order));
                sections.add(new Section("Utility", data.getUnlockedOrder(order.getOrderName()).hasUtility(),
                                data.getUtility() == order));
                sections.add(new Section("Combat", data.getUnlockedOrder(order.getOrderName()).hasCombat(),
                                data.getCombat() == order));

                // ... (Section setup code same as before)

                for (Section s : sections) {
                        // 1. Add Section Title
                        if (currentLineCount + 1 > maxLines) {
                                finalPages.add(finishPage(currentPage, order, startPageIndex + localPageCount++));
                                currentPage = Component.empty();
                                currentLineCount = 0;
                        }
                        currentPage.append(buildSectionTitle(s, order, startPageIndex + localPageCount))
                                        .append(Component.literal("\n"));
                        currentLineCount++;

                        // 2. Add Wrapped Description
                        List<String> wrapped = wrapTextPixels(order.getDescription(s.type), 140); // 110-114 is safe
                        for (String line : wrapped) {
                                if (currentLineCount >= maxLines) {
                                        finalPages.add(finishPage(currentPage, order,
                                                        startPageIndex + localPageCount++));
                                        currentPage = Component.empty();
                                        currentLineCount = 0;
                                }
                                currentPage.append(Component.literal(line).withStyle(ChatFormatting.DARK_GRAY))
                                                .append(Component.literal("\n"));
                                currentLineCount++;
                        }

                        // 3. Add separator
                        if (currentLineCount < maxLines) {
                                currentPage.append(Component.literal("\n"));
                                currentLineCount++;
                        }
                }
                // Final check
                if (!currentPage.getString().isEmpty()) {
                        finalPages.add(finishPage(currentPage, order, startPageIndex + localPageCount));
                }
                return finalPages;
        }

        // Helper to get pixel width of a string (Default MC Font)
        private int getStringWidth(String text) {
                int width = 0;
                for (char c : text.toCharArray()) {
                        // Very rough approximation of vanilla font widths
                        if ("i.,!l'".indexOf(c) != -1)
                                width += 2;
                        else if ("I[] ".indexOf(c) != -1)
                                width += 4;
                        else if ("tfk\"()".indexOf(c) != -1)
                                width += 5;
                        else if ("ABCDEFGHJKLMNOPQRSTUVWXYZabcdefghjmnoprstuvwxyz0123456789".indexOf(c) != -1)
                                width += 6;
                        else if (c == 'W' || c == 'w' || c == '@')
                                width += 8;
                        else
                                width += 6; // default
                        width += 1; // kerning
                }
                return width;
        }

        private List<String> wrapTextPixels(String text, int maxWidth) {
                List<String> lines = new ArrayList<>();
                String[] words = text.split(" ");
                StringBuilder currentLine = new StringBuilder();
                int currentWidth = 0;

                for (String word : words) {
                        int wordWidth = getStringWidth(word + " ");
                        if (currentWidth + wordWidth > maxWidth) {
                                lines.add(currentLine.toString().trim());
                                currentLine = new StringBuilder(word + " ");
                                currentWidth = wordWidth;
                        } else {
                                currentLine.append(word).append(" ");
                                currentWidth += wordWidth;
                        }
                }
                lines.add(currentLine.toString().trim());
                return lines;
        }

        private MutableComponent buildSectionTitle(Section s, Order order, int globalPageForTitle) {
                HoverEvent hover = new HoverEvent.ShowText(
                                Component.literal(s.equipped ? "Equipped!"
                                                : (s.unlocked ? "Click to equip!"
                                                                : "Click to unlock (costs 1 influence)")));

                String cmd = "/ascension_action " + order.getOrderName() + " " + s.type.toLowerCase() + " "
                                + globalPageForTitle;
                ClickEvent click = (switch (order.getOrderName().toLowerCase()) {
                        case "earth" -> Config.earthEnabled;
                        case "sky" -> Config.skyEnabled;
                        case "ocean" -> Config.oceanEnabled;
                        case "flora" -> Config.floraEnabled;
                        case "magic" -> Config.magicEnabled;
                        case "nether" -> Config.netherEnabled;
                        case "end" -> Config.endEnabled;
                        default -> true;
                }) ? new ClickEvent.RunCommand(cmd) : null;

                MutableComponent title = Component.literal(s.type)
                                .withStyle(style -> style.withBold(true)
                                                .withColor(s.equipped ? ChatFormatting.GREEN : ChatFormatting.BLACK)
                                                .withShadowColor(ChatFormatting.GOLD.getColor()).withHoverEvent(hover)
                                                .withClickEvent(click));

                MutableComponent icon = Component.literal((s.unlocked || s.equipped) ? " \uE18B" : " \uE18A")
                                .withStyle(Style.EMPTY.withColor(0xFFFFFF).withHoverEvent(hover).withClickEvent(click));

                return Component.empty().append(title).append(icon);
        }

        private Component finishPage(MutableComponent content, Order order, int globalPageNumber) {
                // 1. Header (1 line)
                MutableComponent header = (globalPageNumber % 2 == 0)
                                ? Component.empty()
                                                .append(Component.literal(order.getOrderIcon())
                                                                .withStyle(Style.EMPTY.withColor(0xFFFFFF)))
                                                .append(Component.literal(" "))
                                                .append(Component.literal(Utils.smallCaps(order.getOrderName()))
                                                                .withStyle(style -> style
                                                                                .withColor(order.getOrderColor())
                                                                                .withBold(true)))
                                : Component.empty()
                                                .append(Component.literal(Utils.smallCaps(order.getOrderName()))
                                                                .withStyle(style -> style
                                                                                .withColor(order.getOrderColor())
                                                                                .withBold(true)))
                                                .append(Component.literal(" "))
                                                .append(Component.literal(order.getOrderIcon())
                                                                .withStyle(Style.EMPTY.withColor(0xFFFFFF)));

                // 2. Build the result
                MutableComponent result = Component.empty().append(header).append("\n\n").append(content);

                // 3. Precise Footer placement
                int contentLines = content.getString().split("\n", -1).length;
                int padding = 12 - contentLines; // 14 total - 1 (header) - 1 (spacer) - 1 (home) = 11

                return result.append(Component.literal("\n".repeat(Math.max(0, padding))))
                                .append(Component.literal("                  ")) // Nudge right
                                .append(Component.literal("<< Home")
                                                .withStyle(s -> s.withColor(ChatFormatting.BLUE).withUnderlined(true)))
                                .withStyle(s -> s.withClickEvent(new ClickEvent.ChangePage(1)));
        }
}