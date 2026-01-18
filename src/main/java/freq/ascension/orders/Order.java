package freq.ascension.orders;

import com.ascension.managers.DivineDataManager;
import com.ascension.managers.Spell;
import com.ascension.managers.SpellCooldownManager;
import com.ascension.managers.SpellStats;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public interface Order {
    // Event listeners
    default void onEntityDamageByEntity(EntityDamageByEntityEvent event, Player player) {
    }

    default void onEntityDamage(EntityDamageEvent event, Player player) {
    }

    default void onToggleFlight(PlayerToggleFlightEvent event, Player player) {
    }

    default void onBlockDamage(BlockDamageEvent event, Player player) {
    }

    default void onBlockBreak(ServerLevel world, BlockPos pos, BlockState state, BlockEntity entity) {
    }

    default void onAnvilPrepare(PrepareAnvilEvent event, Player player) {
    }

    // Ability methods
    default void applyEffect(Player player) {
    }

    default String getDescription(String slotType) {
        return switch (slotType.toLowerCase()) {
            case "passive" -> "§7No passive effect defined.";
            case "utility" -> "§7No utility effect defined.";
            case "combat" -> "§7No combat effect defined.";
            default -> "";
        };
    }

    default boolean hasCapability(Player player, String type) {
        DivineDataManager.DivineData data = DivineDataManager.get(player);
        switch (type) {
            case "passive":
                return data.passive != null && data.passive.equals(this.getOrderName());
            case "utility":
                return data.utility != null && data.utility.equals(this.getOrderName());
            case "combat":
                return data.combat != null && data.combat.equals(this.getOrderName());
            default:
                return false;
        }
    }

    default void executeActiveSpell(String spellId, Player player) {
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

    default Order getVersion(DivineDataManager.Rank rank) {
        return this;
    }

    String getOrderName();

    Item getOrderItem();

    TextColor getOrderColor();

}