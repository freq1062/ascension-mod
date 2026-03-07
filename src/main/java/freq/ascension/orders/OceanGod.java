package freq.ascension.orders;

import freq.ascension.managers.ActiveSpell;
import freq.ascension.managers.Spell;
import freq.ascension.managers.SpellCooldownManager;
import freq.ascension.managers.SpellStats;
import freq.ascension.registry.SpellRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class OceanGod extends Ocean {
    public static final OceanGod INSTANCE = new OceanGod();

    // Tracks dolphins grace cycle state per player: 0=off, 1=DG1(amp=0), 2=DG2(amp=1)
    private static final Map<UUID, Integer> DOLPHINS_GRACE_STATE = new ConcurrentHashMap<>();

    private OceanGod() {
        super();
    }

    @Override
    public void applyEffect(ServerPlayer player) {
        if (hasCapability(player, "passive"))
            player.addEffect(new MobEffectInstance(MobEffects.CONDUIT_POWER, 80, 0, true, false, true));

        // Haste 2 only when actually submerged (not just in rain or at water surface)
        if (hasCapability(player, "passive") && player.isUnderWater())
            player.addEffect(new MobEffectInstance(MobEffects.HASTE, 80, 1, true, false, true));

        // Haste 1 for the caster while drown spell is active
        ActiveSpell drownAs = SpellCooldownManager.getActiveSpell(player, SpellCooldownManager.get("drown"));
        if (drownAs != null && drownAs.isInUse() && hasCapability(player, "combat"))
            player.addEffect(new MobEffectInstance(MobEffects.HASTE, 80, 0, true, false, true));
    }

    @Override
    public void registerSpells() {
        // Override dolphins_grace to cycle none → DG1 → DG2 → none
        SpellCooldownManager.register(new Spell("dolphins_grace", this, "passive", (player, stats) -> {
            ActiveSpell as = SpellCooldownManager.addToActiveSpells(player,
                    SpellCooldownManager.get("dolphins_grace"));

            UUID id = player.getUUID();
            int currentState = DOLPHINS_GRACE_STATE.getOrDefault(id, 0);
            int nextState = (currentState + 1) % 3;
            DOLPHINS_GRACE_STATE.put(id, nextState);

            player.removeEffect(MobEffects.DOLPHINS_GRACE);
            switch (nextState) {
                case 1 -> player.addEffect(new MobEffectInstance(
                        MobEffects.DOLPHINS_GRACE, Integer.MAX_VALUE, 0, true, false, true));
                case 2 -> player.addEffect(new MobEffectInstance(
                        MobEffects.DOLPHINS_GRACE, Integer.MAX_VALUE, 1, true, false, true));
                default -> { /* state 0 = off, already removed */ }
            }
            as.setInUse(false);
        }));

        SpellCooldownManager.register(new Spell("molecular_flux", this, "utility", (player, stats) -> {
            SpellRegistry.molecularFlux(player, stats.getInt(0), stats.getInt(1));
        }));

        SpellCooldownManager.register(new Spell("drown", this, "combat", (player, stats) -> {
            SpellRegistry.drown(player, stats.getInt(0), stats.getInt(1));
        }));
    }

    @Override
    public SpellStats getSpellStats(String spellId) {
        return switch (spellId.toLowerCase()) {
            case "dolphins_grace" -> new SpellStats(60,
                    "Cycle between no speed boost, Dolphins' Grace 1, and Dolphins' Grace 2.",
                    0);
            case "molecular_flux" -> new SpellStats(300,
                    "Transforms water-related blocks between states",
                    40, 15); // range(blocks), duration(seconds)
            case "drown" -> new SpellStats(600,
                    "Drowns players within 12 blocks for 10s. Grants Haste 1 to caster.",
                    10, 12); // duration, radius
            default -> null;
        };
    }

    /** Remove dolphins grace state on player disconnect or ability unequip. */
    public static void clearState(UUID playerId) {
        DOLPHINS_GRACE_STATE.remove(playerId);
    }
}
