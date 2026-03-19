package freq.ascension.orders;

import freq.ascension.Config;
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
        // soul_rage registration is handled by Nether.registerSpells() (the only tier
        // called by OrderRegistry).
        // NetherGod.getSpellStats("soul_rage") provides god-tier cooldown via
        // Spell.getStats(player).
    }

    @Override
    public SpellStats getSpellStats(String spellId) {
        return switch (spellId.toLowerCase()) {
            case "ghast_carry" -> new SpellStats(Config.netherGodGhastCarryCD,
                    "Summons a double-health happy ghast you can fly using. 11.5 b/s.",
                    0);
            case "soul_rage" -> new SpellStats(Config.netherSoulRageCDGod * 20,
                    "Activate a fury that enhances damage when low on health for " + Config.netherSoulRageDurationGod
                            + "s. " +
                            "\u22648h: +1 | \u22646h: +1.5 | \u22644h: +2 | \u22642h: +3. You take 10% more damage while active.",
                    0);
            default -> null;
        };
    }

    @Override
    public String getDescription(String slotType) {
        return switch (slotType.toLowerCase()) {
            case "passive" ->
                "Permanent fire resistance. Mobs in the nether are neutral. Ability to swim in lava. Autocrit when on fire.";
            case "utility" -> {
                SpellStats s = getSpellStats("ghast_carry");
                yield "GHAST CARRY: " + s.getDescription() + " " + s.getCooldownSecs() + "s cooldown.";
            }
            case "combat" -> {
                SpellStats s = getSpellStats("soul_rage");
                yield "SOUL RAGE: " + s.getDescription() + " " + s.getCooldownSecs() + "s cooldown.";
            }
            default -> "";
        };
    }
}
