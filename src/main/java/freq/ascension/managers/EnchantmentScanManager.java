package freq.ascension.managers;

import freq.ascension.Ascension;
import freq.ascension.api.ContinuousTask;
import freq.ascension.registry.WeaponRegistry;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

public class EnchantmentScanManager {
    /*
     * This class is a temporary one that was needed for Ascension SMP S1 due to us
     * not adding the mod that restricts protection, sharpness, and power soon
     * enough before players already had maxed sets.
     */

    private static final int ENCHANTMENT_SCAN_INTERVAL_TICKS = 200;
    private static MinecraftServer server;

    public static void init() {
        server = Ascension.getServer();
        // Check every 5 ticks for more responsive plant proximity detection
        Ascension.scheduler.schedule(new ContinuousTask(ENCHANTMENT_SCAN_INTERVAL_TICKS, () -> {
            downgradeRestrictedEnchantments();
            // TODO: This should be with mythical weapon manager or something
            clearIllegalMythicalWeapons();
        }));
    }

    private static void downgradeRestrictedEnchantmentsOnStack(ItemStack stack,
            Holder<Enchantment> protection,
            Holder<Enchantment> sharpness,
            Holder<Enchantment> power) {
        if (stack.isEmpty() || WeaponRegistry.isMythicalWeapon(stack)) {
            return;
        }

        ItemEnchantments current = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        int protectionLevel = current.getLevel(protection);
        int sharpnessLevel = current.getLevel(sharpness);
        int powerLevel = current.getLevel(power);

        if (protectionLevel != 4 && sharpnessLevel != 5 && powerLevel != 5) {
            return;
        }

        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(current);
        if (protectionLevel == 4) {
            mutable.set(protection, 3);
        }
        if (sharpnessLevel == 5) {
            mutable.set(sharpness, 4);
        }
        if (powerLevel == 5) {
            mutable.set(power, 4);
        }
        stack.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
    }

    private static void downgradeRestrictedEnchantments() {
        var enchantmentRegistry = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        Holder<Enchantment> protection = enchantmentRegistry.getOrThrow(Enchantments.PROTECTION);
        Holder<Enchantment> sharpness = enchantmentRegistry.getOrThrow(Enchantments.SHARPNESS);
        Holder<Enchantment> power = enchantmentRegistry.getOrThrow(Enchantments.POWER);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                downgradeRestrictedEnchantmentsOnStack(stack, protection, sharpness, power);
            }
        }
    }

    private static void clearIllegalMythicalWeapons() {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            AscensionData data = (AscensionData) player;
            boolean isGod = "god".equals(data.getRank());
            String godOrder = data.getGodOrder();

            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                freq.ascension.weapons.MythicWeapon weapon = WeaponRegistry.identifyWeapon(stack);
                if (weapon == null)
                    continue;

                boolean isCorrectGod = isGod
                        && godOrder != null
                        && weapon.getParentOrder().getOrderName().equalsIgnoreCase(godOrder);

                if (!isCorrectGod) {
                    player.getInventory().setItem(slot, ItemStack.EMPTY);
                    String orderName = weapon.getParentOrder().getOrderName();
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "§c[Ascension] Your "
                                    + freq.ascension.weapons.MythicWeapon.formatWeaponName(weapon.getWeaponId())
                                    + " has been removed: only the " + orderName + " god may wield it."));
                }
            }
        }
    }
}
