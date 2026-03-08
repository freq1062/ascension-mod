package freq.ascension.items;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;

import java.util.List;

/**
 * The Challenger's Sigil — a {@code heart_of_the_sea} with CustomModelData string
 * {@code "challengers_sigil"}.
 *
 * <p>Uses the same vanilla-item + CustomModelData pattern as {@link InfluenceItem}, so zero
 * client-side item registry requirements are needed. Sigils can be obtained via admin command
 * (e.g. {@code /give @s heart_of_the_sea}) after the server sets the custom model data, or
 * by using {@link #createSigil()} from server code.
 */
public class ChallengerSigil {

    private static final String CUSTOM_MODEL_DATA_STRING = "challengers_sigil";

    private ChallengerSigil() {}

    /** Returns {@code true} if this stack is a Challenger's Sigil (heart_of_the_sea + CMData). */
    public static boolean isSigil(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (!stack.is(Items.HEART_OF_THE_SEA)) return false;
        CustomModelData cmd = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        if (cmd == null) return false;
        List<String> strings = cmd.strings();
        return strings != null && strings.contains(CUSTOM_MODEL_DATA_STRING);
    }

    /** Creates a named, glowing Challenger's Sigil item stack. */
    public static ItemStack createSigil() {
        ItemStack stack = new ItemStack(Items.HEART_OF_THE_SEA);
        stack.set(DataComponents.ITEM_NAME,
                Component.literal("Challenger's Sigil")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        stack.set(DataComponents.CUSTOM_MODEL_DATA,
                new CustomModelData(List.of(), List.of(), List.of(CUSTOM_MODEL_DATA_STRING), List.of()));
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        // Intentionally non-stackable: each challenge must consume exactly one sigil
        stack.set(DataComponents.MAX_STACK_SIZE, 1);
        return stack;
    }
}

