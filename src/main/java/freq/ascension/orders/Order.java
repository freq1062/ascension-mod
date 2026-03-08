package freq.ascension.orders;

import java.util.ArrayList;
import java.util.List;

import freq.ascension.managers.AscensionData;
import freq.ascension.managers.Spell;
import freq.ascension.managers.SpellCooldownManager;
import freq.ascension.managers.SpellStats;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public interface Order {

    class DamageContext {
        private final DamageSource source;
        private float amount;
        private boolean cancelled = false;

        public DamageContext(DamageSource source, float amount) {
            this.source = source;
            this.amount = amount;
        }

        public DamageSource getSource() {
            return source;
        }

        public float getAmount() {
            return amount;
        }

        public void setAmount(float amount) {
            this.amount = amount;
        }

        public boolean isCancelled() {
            return cancelled;
        }

        public void setCancelled(boolean cancelled) {
            this.cancelled = cancelled;
        }
    }

    String getOrderName();

    String getOrderIcon();

    TextColor getOrderColor();

    default String getDescription(String slotType) {
        return switch (slotType.toLowerCase()) {
            case "passive" -> "§7No passive effect defined.";
            case "utility" -> "§7No utility effect defined.";
            case "combat" -> "§7No combat effect defined.";
            default -> "";
        };
    }

    default List<Spell> getOrderSpells() {
        return new ArrayList<>();
    }

    // Event listeners

    default void onBlockDamage(ServerPlayer player, ServerLevel level, BlockPos pos, ItemStack stack) {
    }

    default void onBlockBreak(ServerPlayer player, ServerLevel world, BlockPos pos, BlockState state,
            BlockEntity entity) {
    }

    default void onAnvilPrepare(AnvilMenu menu) {
    }

    default boolean ignoreAnvilCostLimit() {
        return false;
    }

    default boolean preventAnvilDamage() {
        return false;
    }

    default boolean isDoubleJumpEnabled() {
        return false;
    }

    default void onEntityDamageByEntity(ServerPlayer attacker, LivingEntity victim, DamageContext context) {

    }

    default void onEntityDamage(ServerPlayer victim, DamageContext context) {
    }

    default void onPlayerKill(ServerPlayer killer, LivingEntity victim) {
    }

    default void onToggleFlight(ServerPlayer player) {
    }

    default boolean isIgnoredBy(ServerPlayer player, Mob mob) {
        return false;
    }

    default boolean isNeutralBy(ServerPlayer player, Mob mob) {
        return false;
    }

    default void applyProjectileShield(ServerPlayer player, Projectile projectile) {

    }

    default boolean canWalkOnPowderSnow(ServerPlayer player) {
        return false;
    }

    default boolean canTrampleCrops(ServerPlayer player) {
        return true;
    }

    default float modifySaturation(ServerPlayer player, float saturation) {
        return saturation;
    }

    default int modifyEnchantmentCost(int originalCost) {
        return originalCost;
    }

    default MobEffectInstance onPotionEffect(ServerPlayer player, MobEffectInstance effectInstance) {
        return effectInstance;
    }

    default boolean hasPlantProximityEffect(ServerPlayer player) {
        return false;
    }

    /** FloraGod: returns true when utility is equipped AND player has a plant block in their inventory. */
    default boolean hasInventoryPlantEffect(ServerPlayer player) {
        return false;
    }

    /** Called when a player finishes eating or drinking an item. */
    default void onItemEaten(ServerPlayer player, ItemStack stack) {
    }

    default boolean canSwimInlava(ServerPlayer player) {
        return false;
    }

    // Ability methods
    default void applyEffect(ServerPlayer player) {
    }

    default boolean hasCapability(ServerPlayer player, String type) {
        AscensionData data = (AscensionData) player;
        switch (type) {
            case "passive":
                return data.getPassive() != null && data.getPassive().equals(this);
            case "utility":
                return data.getUtility() != null && data.getUtility().equals(this);
            case "combat":
                return data.getCombat() != null && data.getCombat().equals(this);
            default:
                return false;
        }
    }

    default void executeActiveSpell(String spellId, ServerPlayer player) {
        Spell spell = SpellCooldownManager.get(spellId);
        if (spell == null)
            return;

        SpellStats stats = spell.getStats(player);
        if (stats == null)
            return;

        spell.run(player, stats);
    }

    default void registerSpells() {
    }

    default SpellStats getSpellStats(String spellId) {
        return null;
    }

    default Order getVersion(String rank) {
        return this;
    }

    /**
     * Called before the player unequips (changes) this order in a given slot.
     *
     * @return {@code true} if unequipping is allowed; {@code false} to block it.
     *         Implementations that block must send the player an explanatory message.
     */
    default boolean canUnequip(ServerPlayer player) {
        return true;
    }

    /**
     * Called when this order is removed from a slot (passive/utility/combat).
     * Use this to clean up any persistent effects applied by {@link #applyEffect}.
     *
     * @param player   the player unequipping this order
     * @param slotType "passive", "utility", or "combat"
     */
    default void onUnequip(ServerPlayer player, String slotType) {
    }

}