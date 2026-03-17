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

    // State tracking for action bar updates
    private static final Map<java.util.UUID, String> lastActionBarText = new HashMap<>();
    private static final Map<java.util.UUID, Long> lastActionBarTime = new HashMap<>();
    private static final Map<java.util.UUID, Integer> lastHeldSlot = new HashMap<>();

    // Called once at initialization
    public static void updateActiveSpells() {
        // Combined Task: Decrement cooldowns AND Update Action Bar (1 tick)
        Ascension.scheduler.schedule(new ContinuousTask(1, () -> {
            // 1. Decrement Cooldowns
            synchronized (ACTIVE_SPELLS) {
                Iterator<ActiveSpell> it = ACTIVE_SPELLS.iterator();
                while (it.hasNext()) {
                    ActiveSpell active = it.next();
                    if (!active.isInUse()) {
                        active.decrementCooldown();
                    }
                    if (active.isCooldownFinished()) {
                        it.remove();
                    }
                }
            }

            // 2. Update Action Bar
            long currentTick = Ascension.getServer().getTickCount();
            for (ServerPlayer player : Ascension.getServer().getPlayerList().getPlayers()) {
                try {
                    AscensionData data = (AscensionData) player;
                    Map<Integer, String> bindings = data.getSpellBindings();

                    int slot = player.getInventory().getSelectedSlot();
                    java.util.UUID uuid = player.getUUID();

                    // Detect slot change
                    boolean slotChanged = false;
                    if (!lastHeldSlot.containsKey(uuid) || lastHeldSlot.get(uuid) != slot) {
                        lastHeldSlot.put(uuid, slot);
                        slotChanged = true;
                    }

                    String spellId = (bindings != null) ? bindings.get(slot) : null;

                    String textToSend = ""; // Default empty

                    if (spellId != null) {
                        Spell spell = get(spellId);
                        if (spell != null && !"shapeshift".equalsIgnoreCase(spellId)) {
                            ActiveSpell active = getActiveSpell(player, spell);
                            if (active == null) {
                                // Ready: Green text
                                textToSend = "READY:" + spellId;
                            } else if (active.isInUse()) {
                                textToSend = "INUSE:" + spellId;
                            } else {
                                // Cooldown
                                int remaining = active.getRemainingCooldown();
                                textToSend = "CD:" + spellId + ":" + remaining;
                            }
                        }
                    }

                    // Determine if we need to send update
                    // 1. Text content changed (State change, spell switch)
                    // 2. Slot changed (even if text is same, e.g. switching between two unbounded
                    // slots? No, text would be empty)
                    // 3. Time elapsed > 40 ticks (Keep alive)

                    String lastText = lastActionBarText.getOrDefault(uuid, "");
                    long lastTime = lastActionBarTime.getOrDefault(uuid, 0L);

                    boolean textChanged = !textToSend.equals(lastText);
                    boolean shouldResend = (currentTick - lastTime) > 40; // 2 seconds

                    if (slotChanged || textChanged || shouldResend || textToSend.startsWith("CD:")) {
                        // We send if slot changed, text changed, keepalive needed, OR it's a cooldown
                        // (animation needs 1-tick updates)

                        if (spellId == null) {
                            // If we switched to empty slot and had text before, clear it
                            if (!lastText.isEmpty()) {
                                player.displayClientMessage(Component.empty(), true);
                            }
                        } else {
                            // Construct actual component
                            Spell spell = get(spellId);
                            if (spell != null) {
                                ActiveSpell active = getActiveSpell(player, spell);
                                String display = getDisplayName(spellId);
                                SpellStats stats = spell.getStats(player);
                                int maxCooldown = (stats != null) ? Math.max(1, stats.getCooldownTicks()) : 1;
                                String spacer = "                                        ";

                                // Dynamic spacer based on text width
                                int textWidth = 0;
                                for (char c : display.toCharArray()) {
                                    if (c == ' ' || c == '.' || c == ',' || c == 'i' || c == ':' || c == ';')
                                        textWidth += 2;
                                    else if (c == 'l' || c == 't' || c == 'I' || c == '[' || c == ']')
                                        textWidth += 3;
                                    else if (c == 'f' || c == 'k')
                                        textWidth += 4;
                                    else
                                        textWidth += 5;
                                }
                                int extraSpaces = (int) Math.ceil(textWidth / 4.0);
                                spacer += " ".repeat(extraSpaces);

                                String backwards = "\uF801\uF801\uF801\uF801";

                                Component comp;
                                if (active == null) {
                                    comp = Component.literal(spacer + " \uE17F" + backwards)
                                            .withStyle(style -> style.withShadowColor(0))
                                            .append(Component.literal(display)
                                                    .withStyle(style -> style.withColor(0x00ff00)));
                                } else if (active.isInUse()) {
                                    comp = Component.literal(spacer + " \uE180" + backwards)
                                            .withStyle(style -> style.withShadowColor(0))
                                            .append(Component.literal(display)
                                                    .withStyle(style -> style.withColor(0xf5ea1b)));
                                } else {
                                    int remaining = active.getRemainingCooldown();
                                    float progress = 1.0f - ((float) remaining / (float) maxCooldown);
                                    progress = Math.max(0.0f, Math.min(1.0f, progress));

                                    int index = (int) (progress * 127.0f);
                                    char icon = (char) (0xE100 + index);

                                    comp = Component.literal(spacer + " " + icon + backwards)
                                            .withStyle(style -> style.withShadowColor(0))
                                            .append(Component.literal(display)
                                                    .withStyle(style -> style.withColor(0xff0000)));
                                }
                                player.displayClientMessage(comp, true);
                            }
                        }

                        lastActionBarText.put(uuid, textToSend);
                        lastActionBarTime.put(uuid, currentTick);
                    }
                } catch (Exception e) {
                    // Ignore
                }
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

    /**
     * Triggers the spell of the given slot type ("passive", "utility", "combat") for the player.
     * Finds the first bound spell matching that slot type, checks cooldown, and activates it.
     *
     * @return true if the spell was successfully triggered; false if none found or on cooldown.
     */
    public static boolean triggerSpell(ServerPlayer player, String slotType) {
        freq.ascension.managers.AscensionData data = (freq.ascension.managers.AscensionData) player;
        java.util.Map<Integer, String> bindings = data.getSpellBindings();
        if (bindings == null) return false;

        for (String spellId : bindings.values()) {
            if (spellId == null || spellId.isEmpty()) continue;
            Spell spell = get(spellId);
            if (spell == null || !slotType.equals(spell.getType())) continue;

            if (isSpellOnCooldown(player, spell)) return false;

            // Check Desolation of Time
            if (freq.ascension.orders.End.isAffectedByDesolation(player) && "combat".equals(slotType)) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§cYour combat abilities are disabled by Desolation of Time!"));
                return false;
            }

            try {
                spell.getOrder().executeActiveSpell(spellId, player);
                return true;
            } catch (Exception e) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "Error activating spell: " + e.getMessage()));
                return false;
            }
        }
        return false;
    }
}