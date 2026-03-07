package freq.ascension.orders;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import freq.ascension.managers.Spell;
import freq.ascension.managers.SpellCooldownManager;
import freq.ascension.managers.SpellStats;
import freq.ascension.registry.SpellRegistry;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

/**
 * Stub scaffold for the End Order (Demigod tier).
 *
 * <p>This file exists so that {@code EndDemigodTests} compiles and fails on
 * assertion (not on missing class). All methods return safe defaults; the
 * actual implementation is Phase 2 work.
 *
 * <p><b>Abilities (to be implemented):</b>
 * <ul>
 *   <li><b>Passive</b>: Non-boss End mobs neutral (Enderman, Endermite,
 *       Shulker); ender pearl cooldown −50%; ender chest +1 extra row;
 *       custom purple+bold chest title; unequip guard while extra row
 *       contains items.</li>
 *   <li><b>Utility</b>: Teleport — 10-block ray trace, stops at 2nd solid
 *       block, teleports to farthest valid position, enderman particles.</li>
 *   <li><b>Combat</b>: Desolation of Time — 7-block radius disables combat
 *       abilities for 5 s + Weakness I for 10 s; DragonCurve VFX.</li>
 * </ul>
 */
public class End implements Order {

    public static final End INSTANCE = new End();

    @Override
    public String getOrderName() {
        return "end";
    }

    @Override
    public String getOrderIcon() {
        return "\uE188";
    }

    @Override
    public TextColor getOrderColor() {
        return TextColor.fromRgb(0x7B2FBE);
    }

    @Override
    public Order getVersion(String rank) {
        // No god tier defined yet — return self
        return this;
    }

    /** Players currently affected by Desolation of Time (combat disabled). */
    public static final Set<UUID> DESOLATED_PLAYERS = ConcurrentHashMap.newKeySet();

    public static boolean isAffectedByDesolation(ServerPlayer player) {
        return DESOLATED_PLAYERS.contains(player.getUUID());
    }

    @Override
    public void registerSpells() {
        SpellCooldownManager.register(new Spell("teleport", this, "utility", (player, stats) -> {
            SpellRegistry.teleport(player);
        }));
        SpellCooldownManager.register(new Spell("desolation_of_time", this, "combat", (player, stats) -> {
            SpellRegistry.desolationOfTime(player, stats);
        }));
    }

    @Override
    public SpellStats getSpellStats(String spellId) {
        // Stubs — durations/cooldowns filled in during Phase 2
        return switch (spellId.toLowerCase()) {
            case "teleport" -> new SpellStats(30, "Teleport up to 10 blocks in your look direction.");
            case "desolation_of_time" -> new SpellStats(120,
                    "Within 7 blocks: disable combat abilities 5 s, Weakness I 10 s.",
                    100, // disableDurationTicks (5 s)
                    200  // weaknessDurationTicks (10 s)
            );
            default -> null;
        };
    }

    @Override
    public String getDescription(String slotType) {
        return switch (slotType.toLowerCase()) {
            case "passive" ->
                "End mobs neutral. Ender pearl cooldown halved. Ender chest +1 row.";
            case "utility" -> "Teleport up to 10 blocks.";
            case "combat" -> "Desolation of Time — disable combat + Weakness I in radius.";
            default -> "";
        };
    }

    /**
     * End mob neutrality: Enderman, Endermite, and Shulker are neutral when the
     * player has the End passive equipped. Boss mobs (Ender Dragon) are excluded.
     */
    @Override
    public boolean isNeutralBy(ServerPlayer player, Mob mob) {
        // MobTargetMixin only reaches here when the player has End equipped.
        // Enderman, Endermite, and Shulker are neutral; Ender Dragon (boss) is excluded.
        EntityType<?> type = mob.getType();
        return type == EntityType.ENDERMAN
                || type == EntityType.ENDERMITE
                || type == EntityType.SHULKER;
    }

    @Override
    public void applyEffect(ServerPlayer player) {
        // Phase 2: apply passive effects (fire resistance not needed; passive is
        // mob neutrality + cooldown reduction — no potion effect tick needed)
    }
}
