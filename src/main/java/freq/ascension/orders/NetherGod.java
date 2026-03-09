package freq.ascension.orders;

import freq.ascension.managers.Spell;
import freq.ascension.managers.SpellCooldownManager;
import freq.ascension.managers.SpellStats;
import freq.ascension.registry.SpellRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

public class NetherGod extends Nether {
    public static final NetherGod INSTANCE = new NetherGod();

    private NetherGod() {
        super();
    }

    @Override
    public void applyEffect(ServerPlayer player) {
        super.applyEffect(player);
        // Lava swimming speed boost is handled in LavaSwimmingMixin (0.20 b/t for gods
        // vs 0.12 b/t for demigods)
    }

    @Override
    protected float getSoulDrainRatio() {
        return 0.5f;
    }

    /**
     * God: nether mobs are truly passive (isIgnoredBy = never attack under any
     * circumstances).
     * Demigod: isNeutralBy (neutral = don't attack unless provoked).
     */
    @Override
    public boolean isIgnoredBy(ServerPlayer player, Mob mob) {
        return hasCapability(player, "passive") && mob.level().dimension() == Level.NETHER;
    }

    @Override
    public void registerSpells() {
        SpellCooldownManager.register(new Spell("ghast_carry", this, "utility", (player, stats) -> {
            SpellRegistry.ghast_carry(player, true, 11.5 / 6.0);
        }));
        SpellCooldownManager.register(new Spell("soul_drain", this, "combat", (player, stats) -> {
            SpellRegistry.soul_drain(player, stats.getInt(0));
        }));
    }

    @Override
    public SpellStats getSpellStats(String spellId) {
        return switch (spellId.toLowerCase()) {
            case "ghast_carry" -> new SpellStats(60,
                    "Summons a double-health happy ghast you can fly using. 11.5 b/s.",
                    0);
            case "soul_drain" -> new SpellStats(60,
                    "For 15 seconds you gain saturation equivalent to 1/2 of the damage that you deal.",
                    300); // duration ticks (15s)
            default -> null;
        };
    }
}
