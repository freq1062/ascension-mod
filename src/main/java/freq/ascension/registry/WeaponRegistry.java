package freq.ascension.registry;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;


import freq.ascension.weapons.MythicWeapon;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Registry that maps weapon IDs and order names to their {@link MythicWeapon} instances.
 *
 * <p>Architecture mirrors {@link OrderRegistry}: concrete weapon singletons are registered here
 * during mod initialization. Provides helpers for inventory scanning.
 *
 * <p><b>Registration order:</b> call {@link #register(MythicWeapon)} in {@code Ascension.onInitialize()}
 * after the order registry is ready. No weapons are registered by default; concrete weapon classes
 * will register themselves here once implemented.
 */
public class WeaponRegistry {

    private static final Map<String, MythicWeapon> BY_ID    = new HashMap<>();
    private static final Map<String, MythicWeapon> BY_ORDER = new HashMap<>();

    private WeaponRegistry() {}

    /**
     * Registers a mythical weapon. Keyed by both {@link MythicWeapon#getWeaponId()} and
     * {@link MythicWeapon#getParentOrder()}'s order name (lowercase).
     *
     * @throws IllegalArgumentException if a weapon with the same ID or order is already registered.
     */
    public static void register(MythicWeapon weapon) {
        String id = weapon.getWeaponId().toLowerCase();
        String order = weapon.getParentOrder().getOrderName().toLowerCase();

        if (BY_ID.containsKey(id)) {
            throw new IllegalArgumentException(
                    "Duplicate mythical weapon ID: " + id);
        }
        if (BY_ORDER.containsKey(order)) {
            throw new IllegalArgumentException(
                    "Order '" + order + "' already has a registered mythical weapon.");
        }

        BY_ID.put(id, weapon);
        BY_ORDER.put(order, weapon);
    }

    /** Returns the weapon with the given ID, or {@code null} if none is registered. */
    public static MythicWeapon get(String weaponId) {
        if (weaponId == null) return null;
        return BY_ID.get(weaponId.toLowerCase());
    }

    /** Returns the mythical weapon for the given order name, or {@code null} if none. */
    public static MythicWeapon getForOrder(String orderName) {
        if (orderName == null) return null;
        return BY_ORDER.get(orderName.toLowerCase());
    }

    /**
     * Returns {@code true} if the given ItemStack is any registered mythical weapon, as
     * determined by each weapon's {@link MythicWeapon#isItem(ItemStack)} check.
     */
    public static boolean isMythicalWeapon(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        for (MythicWeapon weapon : BY_ID.values()) {
            if (weapon.isItem(stack)) return true;
        }
        return false;
    }

    /**
     * Returns {@code true} if any slot in the given player's inventory (including armour and
     * off-hand) contains a registered mythical weapon.
     */
    public static boolean hasWeapon(ServerPlayer player) {
        return findMythicalWeaponStack(player.getInventory()) != null;
    }

    /**
     * Returns the first mythical weapon ItemStack found in the given inventory, or
     * {@link ItemStack#EMPTY} if none.
     */
    public static ItemStack getMythicalWeaponIn(Inventory inv) {
        ItemStack found = findMythicalWeaponStack(inv);
        return found != null ? found : ItemStack.EMPTY;
    }

    /**
     * Returns the {@link MythicWeapon} definition that matches the given stack, or {@code null}.
     */
    public static MythicWeapon identifyWeapon(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        for (MythicWeapon weapon : BY_ID.values()) {
            if (weapon.isItem(stack)) return weapon;
        }
        return null;
    }

    /** Returns an unmodifiable view of all registered weapons. */
    public static Collection<MythicWeapon> allWeapons() {
        return Collections.unmodifiableCollection(BY_ID.values());
    }

    /** Returns {@code true} if any weapon is registered for the given order name. */
    public static boolean hasWeaponForOrder(String orderName) {
        return orderName != null && BY_ORDER.containsKey(orderName.toLowerCase());
    }

    // ─── private helpers ──────────────────────────────────────────────────────

    private static ItemStack findMythicalWeaponStack(Inventory inv) {
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (isMythicalWeapon(stack)) return stack;
        }
        return null;
    }
}
