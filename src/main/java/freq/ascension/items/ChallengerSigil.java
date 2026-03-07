package freq.ascension.items;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The Challenger's Sigil — an item that, when held, allows players to initiate a god challenge
 * by right-clicking an Order POI cube.
 *
 * <p>Registered during {@code ModInitializer.onInitialize()} via {@link #register()}.
 * The crafting recipe is defined in {@code data/ascension/recipe/challenger_sigil.json}.
 *
 * <p>In 1.21.10, {@link Item.Properties} must have the resource key set before the
 * {@link Item} constructor is invoked. ITEM is therefore initialised lazily inside
 * {@link #register()} (after KEY is available) rather than as a static field.
 */
public class ChallengerSigil {

    public static final ResourceKey<Item> KEY = ResourceKey.create(
            net.minecraft.core.registries.Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("ascension", "challenger_sigil"));

    // Non-final: set during register() to avoid the "Item id not set" error
    // that occurs when Item is constructed before the resource key is available.
    private static Item ITEM;

    public static void register() {
        ITEM = new Item(new Item.Properties().stacksTo(1).setId(KEY));
        Registry.register(BuiltInRegistries.ITEM, KEY, ITEM);
    }

    /** Returns true if the given item stack is a Challenger's Sigil. */
    public static boolean isSigil(ItemStack stack) {
        return ITEM != null && stack != null && !stack.isEmpty() && stack.getItem() == ITEM;
    }

    /** Creates a named, glowing Challenger's Sigil item stack. */
    public static ItemStack createSigil() {
        if (ITEM == null) throw new IllegalStateException("ChallengerSigil not yet registered");
        ItemStack stack = new ItemStack(ITEM);
        stack.set(DataComponents.CUSTOM_NAME,
                Component.literal("Challenger's Sigil")
                        .withStyle(ChatFormatting.GOLD)
                        .withStyle(ChatFormatting.BOLD));
        // Enchantment-glow override to make it appear enchanted
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return stack;
    }
}
