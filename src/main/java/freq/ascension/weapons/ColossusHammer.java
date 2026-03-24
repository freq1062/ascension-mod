package freq.ascension.weapons;

import java.util.List;

import freq.ascension.Ascension;
import freq.ascension.orders.Earth;
import freq.ascension.orders.Order;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * The Colossus Hammer — Earth order mythical weapon.
 *
 * <p>A retextured Mace enchanted with Breach II, Wind Burst I, and Curse of Vanishing.
 * It has no active ability hooks; its power comes entirely from its base Mace mechanics
 * and the pre-applied enchantments.
 */
public class ColossusHammer implements MythicWeapon {

    public static final ColossusHammer INSTANCE = new ColossusHammer();

    @Override
    public String getWeaponId() {
        return "colossus_hammer";
    }

    @Override
    public Item getBaseItem() {
        return Items.MACE;
    }

    @Override
    public Order getParentOrder() {
        return Earth.INSTANCE;
    }

    @Override
    public ItemStack createItem() {
        ItemStack stack = buildBaseItem();

        var registries = Ascension.getServer().registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT);

        stack.enchant(registries.getOrThrow(Enchantments.BREACH), 2);
        stack.enchant(registries.getOrThrow(Enchantments.WIND_BURST), 1);
        stack.enchant(registries.getOrThrow(Enchantments.VANISHING_CURSE), 1);

        stack.set(DataComponents.LORE, new ItemLore(List.of(
            Component.literal("A devastating mace empowered by Breach and Wind Burst.").withStyle(s -> s.withItalic(true).withColor(ChatFormatting.GRAY)),
            Component.literal("Deals bonus damage with fall momentum; Wind Burst").withStyle(s -> s.withItalic(true).withColor(ChatFormatting.GRAY)),
            Component.literal("launches nearby foes on a critical hit.").withStyle(s -> s.withItalic(true).withColor(ChatFormatting.GRAY))
        )));

        return stack;
    }
}
