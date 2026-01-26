package freq.ascension.items;

import java.util.List;

import freq.ascension.Utils;
import freq.ascension.managers.AscensionData;
import freq.ascension.orders.Order;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;

public class InfluenceItem {
        private static final String CUSTOM_MODEL_DATA_STRING = "influence_item";

        public static ItemStack createItem() {
                ItemStack item = new ItemStack(Items.AMETHYST_SHARD);

                // Set Name
                // In 1.21+, we use DataComponents to set the name.
                // Assuming 'Text' refers to 'net.minecraft.network.chat.Component' and
                // 'Formatting' to 'net.minecraft.ChatFormatting'
                // or the specific mapping the environment is using (e.g., Yarn vs MojMap).
                // Based on typical 1.21 mappings (MojMap/NeoForge/Fabric):

                item.set(DataComponents.ITEM_NAME,
                                Component.literal("Influence")
                                                .withStyle(ChatFormatting.BOLD)
                                                .withStyle(ChatFormatting.GOLD));

                item.set(DataComponents.CUSTOM_MODEL_DATA,
                                new CustomModelData(List.of(), List.of(), List.of(CUSTOM_MODEL_DATA_STRING),
                                                List.of()));

                item.set(DataComponents.LORE, new ItemLore(
                                List.of(Component.literal("Power is a zero-sum game.")
                                                .withStyle(ChatFormatting.GRAY))));

                return item;
        }

        public static boolean isInfluenceItem(ItemStack stack) {
                if (stack == null || stack.isEmpty())
                        return false;

                DataComponentMap customData = stack.getComponents();
                if (customData != null && customData.has(DataComponents.CUSTOM_DATA))
                        return customData.get(DataComponents.CUSTOM_MODEL_DATA)
                                        .getString(0) == CUSTOM_MODEL_DATA_STRING;
                return false;
        }

        public static ItemStack getInfluenceDisplayItem(AscensionData data) {
                ItemStack item = createItem();
                int amount = data.getInfluence();
                if (amount > 0)
                        item.setCount(amount);

                item.set(DataComponents.ITEM_NAME,
                                Component.literal("Influence: ")
                                                .withStyle(ChatFormatting.BOLD)
                                                .withStyle(ChatFormatting.GOLD)
                                                .append(Component.literal(String.valueOf(amount))
                                                                .withStyle(ChatFormatting.WHITE)));

                Order passive = data.getPassive();
                Order utility = data.getUtility();
                Order combat = data.getCombat();

                item.set(DataComponents.LORE, new ItemLore(
                                List.of(
                                                Component.literal("Passive: ")
                                                                .withStyle(style -> style
                                                                                .withItalic(false)
                                                                                .withColor(ChatFormatting.AQUA))

                                                                .withStyle(ChatFormatting.AQUA)
                                                                .append(Component
                                                                                .literal(Utils.smallCaps(passive != null
                                                                                                ? passive.getOrderName()
                                                                                                : "None"))),
                                                Component.literal("Utility: ")
                                                                .withStyle(style -> style
                                                                                .withItalic(false)
                                                                                .withColor(ChatFormatting.LIGHT_PURPLE))
                                                                .append(Component
                                                                                .literal(Utils.smallCaps(utility != null
                                                                                                ? utility.getOrderName()
                                                                                                : "None"))),
                                                Component.literal("Combat: ")
                                                                .withStyle(style -> style
                                                                                .withItalic(false)
                                                                                .withColor(ChatFormatting.RED))
                                                                .append(Component.literal(
                                                                                Utils.smallCaps(combat != null
                                                                                                ? combat.getOrderName()
                                                                                                : "None"))))));
                return item;
        }
}
