package freq.ascension.orders;

import freq.ascension.Config;
import freq.ascension.managers.SpellStats;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

/**
 * God-tier End order. Extends the demigod End order with stronger ability values.
 * <ul>
 *   <li><b>Passive</b>: End mobs neutral; ¼ ender pearl cooldown (instead of ½).</li>
 *   <li><b>Utility</b>: Teleport 15 blocks through up to 4 solid blocks.</li>
 *   <li><b>Combat</b>: Desolation of Time — 10s disable + Weakness I 15s in 7-block radius.</li>
 * </ul>
 */
public class EndGod extends End {

    public static final EndGod INSTANCE = new EndGod();

    @Override
    public Order getVersion(String rank) {
        return switch (rank.toLowerCase()) {
            case "god" -> this;
            default -> End.INSTANCE;
        };
    }

    @Override
    public SpellStats getSpellStats(String spellId) {
        return switch (spellId.toLowerCase()) {
            // extra[0] = maxBlocks for teleport, extra[1] = maxSolidBlocks
            case "teleport" -> new SpellStats(Config.endGodTeleportCD, "Teleport up to 15 blocks in your look direction.", Config.endGodTeleportRange, 4);
            case "desolation_of_time" -> new SpellStats(Config.endGodDesolationCD,
                    "Within 7 blocks: disable combat abilities 10 s, Weakness I 15 s.",
                    200, // disableDurationTicks (10 s)
                    300  // weaknessDurationTicks (15 s)
            );
            default -> null;
        };
    }

    @Override
    public String getDescription(String slotType) {
        return switch (slotType.toLowerCase()) {
            case "passive" -> "End mobs neutral. Ender pearl cooldown quartered. Ender chest +1 row.";
            case "utility" -> "TELEPORT: Teleport up to 15 blocks through up to 4 solid blocks. " + getSpellStats("teleport").getCooldownSecs() + "s cooldown.";
            case "combat" -> "DESOLATION OF TIME: Disable combat abilities 10s + Weakness I 15s within 7 blocks. " + getSpellStats("desolation_of_time").getCooldownSecs() + "s cooldown.";
            default -> "";
        };
    }

    @Override
    public boolean isNeutralBy(ServerPlayer player, Mob mob) {
        EntityType<?> type = mob.getType();
        return type == EntityType.ENDERMAN
                || type == EntityType.ENDERMITE
                || type == EntityType.SHULKER;
    }
}
