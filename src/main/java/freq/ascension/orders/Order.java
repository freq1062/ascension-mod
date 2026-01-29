package freq.ascension.orders;

import java.util.ArrayList;
import java.util.List;

import freq.ascension.managers.AscensionData;
import freq.ascension.managers.Spell;
import freq.ascension.managers.SpellCooldownManager;
import freq.ascension.managers.SpellStats;
import freq.ascension.mixin.DamageMixin.DamageContext;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public interface Order {
    String getOrderName();

    ItemStack getOrderItem();

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

    default void onEntityDamageByEntity(ServerPlayer attacker, ServerPlayer victim, DamageContext context) {

    }

    default void onEntityDamage(ServerPlayer victim, DamageContext context) {
    }

    default void onToggleFlight(ServerPlayer player) {
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
                return data.getUtility() != null && data.getCombat().equals(this);
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

}