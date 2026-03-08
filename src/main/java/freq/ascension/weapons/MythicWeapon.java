package freq.ascension.weapons;

import java.util.List;

import freq.ascension.orders.Order;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.util.Unit;

/**
 * Interface that every mythical weapon must implement.
 *
 * <p>Architecture mirrors {@code Order.java}: concrete weapon classes implement this interface,
 * default methods are no-ops that concrete classes override as needed, and all game event
 * callbacks are routed through {@code AbilityManager.broadcastWeapon()}.
 *
 * <p>Mythical weapons are god-exclusive. WeaponRegistry associates each weapon with an order.
 * AbilityManager calls the appropriate hook methods during relevant game events.
 *
 * <p><b>Identity:</b> weapons are identified by a custom model data string (e.g.
 * {@code "tempest_trident"}) set on the item when created. Use {@link #isItem(ItemStack)} to
 * detect the weapon in any inventory slot.
 */
public interface MythicWeapon {

    // ═══ IDENTITY ═══

    /** Unique ID string, used as the custom model data string. Example: {@code "tempest_trident"}. */
    String getWeaponId();

    /** The base Minecraft item (e.g. {@code Items.TRIDENT} for the Tempest Trident). */
    Item getBaseItem();

    /** The order this weapon is associated with. Used by WeaponRegistry for lookup. */
    Order getParentOrder();

    // ═══ ITEM CREATION ═══

    /**
     * Builds and returns the mythical weapon ItemStack.
     *
     * <p>Concrete implementations must call or replicate the base setup:
     * <ul>
     *   <li>Set custom model data string to {@link #getWeaponId()}</li>
     *   <li>Mark the item as unbreakable</li>
     *   <li>Set a display name styled with the parent order's color</li>
     *   <li>Add Curse of Vanishing (requires enchantment registry; do this with server context)</li>
     *   <li>Add weapon-specific enchantments</li>
     * </ul>
     *
     * <p>Callers that need enchantments should pass a {@code HolderLookup.Provider} and add
     * enchantments to the returned stack. The {@link #buildBaseItem()} helper builds the stack
     * without any enchantments.
     */
    ItemStack createItem();

    /**
     * Helper: creates the ItemStack with base properties only — custom model data, unbreakable
     * flag, and display name. Concrete implementations may call this inside {@link #createItem()}
     * before adding enchantments.
     */
    default ItemStack buildBaseItem() {
        ItemStack stack = new ItemStack(getBaseItem());
        stack.set(DataComponents.CUSTOM_MODEL_DATA,
                new CustomModelData(List.of(), List.of(), List.of(getWeaponId()), List.of()));
        stack.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        stack.set(DataComponents.CUSTOM_NAME,
                Component.literal(formatWeaponName(getWeaponId()))
                        .withStyle(style -> style
                                .withColor(getParentOrder().getOrderColor())
                                .withItalic(false)
                                .withBold(true)));
        return stack;
    }

    /**
     * Returns {@code true} if the given stack is this mythical weapon, identified by its custom
     * model data string.
     */
    default boolean isItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        CustomModelData cmd = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        if (cmd == null) return false;
        return cmd.strings().contains(getWeaponId());
    }

    // ═══ EVENT HOOKS (all no-ops; concrete weapons override as needed) ═══

    /**
     * Called every 40 ticks while the player holds this weapon in any inventory slot.
     * Routed through {@code AbilityManager.broadcastWeapon()}.
     */
    default void onHold(ServerPlayer player) {}

    /**
     * Called when the player releases (no longer holds) this weapon in the active slot.
     * Routed through {@code AbilityManager.broadcastWeapon()}.
     */
    default void onRelease(ServerPlayer player) {}

    /**
     * Called when the god player attacks a living entity while holding this weapon.
     * The DamageContext can be used to modify or cancel the damage.
     */
    default void onAttack(ServerPlayer attacker, LivingEntity victim, Order.DamageContext ctx) {}

    /**
     * Called after the god player kills a living entity while holding this weapon.
     */
    default void onEntityKill(ServerPlayer killer, LivingEntity victim) {}

    /**
     * Called after the god player kills another player while holding this weapon.
     */
    default void onPlayerKill(ServerPlayer killer, ServerPlayer victim) {}

    /**
     * Called when the player shift+right-clicks while this weapon is in the active slot.
     * Use for primary weapon ability activation.
     */
    default void onUse(ServerPlayer player) {}

    /**
     * Called when the player right-clicks (no shift) while this weapon is in the active slot.
     */
    default void onShiftUse(ServerPlayer player) {}

    /**
     * Called when the player shift+left-clicks while this weapon is in the active slot.
     * Use for mode toggles that should NOT also activate a spell.
     */
    default void onShiftLeftClick(ServerPlayer player) {}

    /**
     * Called when the god player who holds this weapon dies.
     * Runs before GodManager.demoteFromGod() removes the weapon.
     */
    default void onDeath(ServerPlayer player) {}

    /**
     * Called when a projectile owned by the god player (e.g. a thrown trident or fired firework)
     * hits a living entity. Routed through broadcastWeapon in the projectile-hit handler.
     */
    default void onProjectileHit(ServerPlayer owner, LivingEntity victim, Order.DamageContext ctx) {}

    // ═══ HELPERS ═══

    /** Converts a weapon ID like {@code "tempest_trident"} to {@code "Tempest Trident"}. */
    static String formatWeaponName(String weaponId) {
        if (weaponId == null || weaponId.isEmpty()) return "";
        String[] words = weaponId.replace('_', ' ').split(" ");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) sb.append(word.substring(1));
                sb.append(' ');
            }
        }
        return sb.toString().trim();
    }
}
