package freq.ascension.menus;

import java.util.List;

import eu.pb4.sgui.api.gui.BookGui;
import freq.ascension.Utils;
import freq.ascension.managers.AscensionData;
import freq.ascension.orders.Order;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
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
                                // \n\n\n\n\n\uF802\uF803
                                .append("\n")
                                .append(Component
                                                .literal("\uF806\uE181\uF801\uF801\uF801\uF801\uF801\uF802\uF802\uF804G\n\uF806"
                                                                + //
                                                                "   \uF804A\uF802\uF802    B\n\uF806" + //
                                                                "\n" + //
                                                                "\n\uF806" + //
                                                                " \uF804\uF804\uF804C\uF802\uF802\uF802  \uF804 D\n"
                                                                + //
                                                                "\n" + //
                                                                "\n\uF806" + //
                                                                "   E\uF802\uF802   \uF804 F\n\uF806" + //
                                                                "\uF802\uF802  H") // forwards
                                                .withStyle(Style.EMPTY.withColor(0xFFFFFF)));

                ItemStack book = new ItemStack(Items.WRITTEN_BOOK);

                book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                                Filterable.passThrough("AscensionMenu"),
                                "freq1062", 3, List.of(
                                                Filterable.passThrough(pageContent)),
                                true));

                BookGui gui = new BookGui(player, book);
                gui.open();
        }
}