package freq.ascension.managers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import freq.ascension.Ascension;
import freq.ascension.api.ContinuousTask;
import freq.ascension.registry.SpellRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class SpellCooldownManager {
    private static final SpellCooldownManager instance = new SpellCooldownManager();
    private static final Map<String, Spell> SPELLS = new HashMap<>();
    private static final List<ActiveSpell> ACTIVE_SPELLS = new ArrayList<>();

    public static void register(Spell spell) {
        SPELLS.put(spell.getId(), spell);
        SpellRegistry.register(spell);
    }

    public static SpellCooldownManager getInstance() {
        return instance;
    }

    public static Spell get(String id) {
        return SPELLS.get(id);
    }

    /**
     * Returns a snapshot of all registered spell IDs.
     */
    public static List<String> getSpellIds() {
        synchronized (SPELLS) {
            return new ArrayList<>(SPELLS.keySet());
        }
    }

    public static String getDisplayName(String id) {
        if (id == null || id.isEmpty())
            return "";
        // replace underscores/dashes with spaces and split on whitespace
        String normalized = id.replaceAll("[_-]+", " ").trim();
        String[] words = normalized.split("\\s+");
        StringBuilder displayName = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty())
                continue;
            // capitalize first char, keep rest as lower
            String w = word.toLowerCase();
            displayName.append(Character.toUpperCase(w.charAt(0)));
            if (w.length() > 1)
                displayName.append(w.substring(1));
            displayName.append(' ');
        }
        return displayName.toString().trim();
    }

    public static String getDescription(String id, ServerPlayer player) {
        Spell spell = SPELLS.get(id);
        if (spell == null)
            return "";
        return spell.getStats(player).getDescription();
    }

    // Called once at initialization
    public static void updateActiveSpells() {
        Ascension.scheduler.schedule(new ContinuousTask(1, () -> {
            Map<ServerPlayer, List<ActiveSpell>> playerSpells = new HashMap<>();

            synchronized (ACTIVE_SPELLS) {
                Iterator<ActiveSpell> it = ACTIVE_SPELLS.iterator();
                while (it.hasNext()) {
                    ActiveSpell active = it.next();
                    if (!active.isInUse()) {
                        active.decrementCooldown();
                    }
                    if (active.isCooldownFinished()) {
                        it.remove();
                    } else {
                        playerSpells.computeIfAbsent(active.getPlayer(), k -> new ArrayList<>()).add(active);
                    }
                }
            }

            for (Map.Entry<ServerPlayer, List<ActiveSpell>> entry : playerSpells.entrySet()) {
                ServerPlayer player = entry.getKey();
                if (player == null)
                    continue;

                StringBuilder msg = new StringBuilder();
                for (ActiveSpell spell : entry.getValue()) {
                    if (msg.length() > 0)
                        msg.append(" | ");
                    String name = getDisplayName(spell.getSpell().getId());
                    float seconds = spell.getRemainingCooldown() / 20.0f;
                    msg.append(name).append(": ").append(String.format("%.1fs", seconds));
                }
                player.displayClientMessage(Component.literal(msg.toString()), true);
            }
        }));
    }

    public static ActiveSpell addToActiveSpells(ServerPlayer player, Spell spell) {
        synchronized (ACTIVE_SPELLS) {
            ActiveSpell activeSpell = new ActiveSpell(player, spell, spell.getStats(player).getCooldownTicks());
            activeSpell.setInUse(true);
            // Mark spell as in use to prevent immediate cooldown decrement
            // Remove set in use in activate implementation
            ACTIVE_SPELLS.add(activeSpell);
            return activeSpell;
        }
    }

    /**
     * Returns true if the given spell is currently on cooldown for the player.
     */
    public static boolean isSpellOnCooldown(ServerPlayer player, Spell spell) {
        if (player == null || spell == null)
            return false;
        synchronized (ACTIVE_SPELLS) {
            for (ActiveSpell a : ACTIVE_SPELLS) {
                if (a.getPlayer().equals(player) && a.getSpell() != null
                        && a.getSpell().getId().equals(spell.getId())) {
                    // If still in use or cooldown > 0 then it's considered on cooldown
                    if (a.isInUse() || !a.isCooldownFinished())
                        return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns the ActiveSpell instance tracking this player's spell, or null.
     */
    public static ActiveSpell getActiveSpell(ServerPlayer player, Spell spell) {
        if (player == null || spell == null)
            return null;
        synchronized (ACTIVE_SPELLS) {
            for (ActiveSpell a : ACTIVE_SPELLS) {
                if (a.getPlayer().equals(player) && a.getSpell() != null
                        && a.getSpell().getId().equals(spell.getId())) {
                    return a;
                }
            }
        }
        return null;
    }
}