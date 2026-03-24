package freq.ascension.managers;

import com.mojang.serialization.Codec;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Persistent storage for the End passive's extra ender-chest row.
 *
 * <p>
 * Each player UUID maps to a 9-slot {@link ItemStack} array (one extra
 * ender-chest row).
 * Data survives restarts and dimension changes, keyed to the server's overworld
 * data storage.
 *
 * <p>
 * Only non-empty stacks are written to disk (stored as a
 * UUID→slot-index→ItemStack map).
 * Absent slots deserialise as {@link ItemStack#EMPTY}.
 */
public class EnderRowManager extends SavedData {

    public static final int ROW_SIZE = 9;
    private static final String KEY = "ascension_ender_row";

    private final Map<UUID, ItemStack[]> extraRows = new HashMap<>();

    private EnderRowManager() {
    }

    // ─── Codec ───────────────────────────────────────────────────────────────

    /**
     * Raw codec: UUID-string → (slot-index-string → non-empty ItemStack).
     * Empty slots are omitted on save and default to EMPTY on load.
     */
    private static final Codec<EnderRowManager> CODEC = Codec.unboundedMap(
            Codec.STRING, // UUID
            Codec.unboundedMap(
                    Codec.STRING.xmap(Integer::parseInt, i -> Integer.toString(i)), // slot index
                    ItemStack.CODEC // only non-empty stacks
            )).xmap(EnderRowManager::fromRawMap, EnderRowManager::toRawMap);

    public static final SavedDataType<EnderRowManager> TYPE = new SavedDataType<>(
            KEY,
            EnderRowManager::new,
            CODEC,
            null);

    // ─── Factory helpers ──────────────────────────────────────────────────────

    private static EnderRowManager fromRawMap(Map<String, Map<Integer, ItemStack>> raw) {
        EnderRowManager m = new EnderRowManager();
        for (Map.Entry<String, Map<Integer, ItemStack>> e : raw.entrySet()) {
            try {
                UUID uuid = UUID.fromString(e.getKey());
                ItemStack[] row = new ItemStack[ROW_SIZE];
                Arrays.fill(row, ItemStack.EMPTY);
                for (Map.Entry<Integer, ItemStack> slot : e.getValue().entrySet()) {
                    int i = slot.getKey();
                    if (i >= 0 && i < ROW_SIZE)
                        row[i] = slot.getValue().copy();
                }
                m.extraRows.put(uuid, row);
            } catch (IllegalArgumentException ignored) {
                // Corrupt UUID string — skip
            }
        }
        return m;
    }

    private static Map<String, Map<Integer, ItemStack>> toRawMap(EnderRowManager m) {
        Map<String, Map<Integer, ItemStack>> out = new HashMap<>();
        for (Map.Entry<UUID, ItemStack[]> e : m.extraRows.entrySet()) {
            Map<Integer, ItemStack> slots = new HashMap<>();
            ItemStack[] row = e.getValue();
            for (int i = 0; i < ROW_SIZE; i++) {
                if (row[i] != null && !row[i].isEmpty()) {
                    slots.put(i, row[i].copy());
                }
            }
            if (!slots.isEmpty()) {
                out.put(e.getKey().toString(), slots);
            }
        }
        return out;
    }

    // ─── API ─────────────────────────────────────────────────────────────────

    public static EnderRowManager get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /**
     * Returns the 9-slot extra-row array for the given player UUID.
     * Missing entries are initialised to {@link ItemStack#EMPTY} on first access.
     */
    public ItemStack[] getExtraRow(UUID uuid) {
        return extraRows.computeIfAbsent(uuid, k -> {
            ItemStack[] row = new ItemStack[ROW_SIZE];
            Arrays.fill(row, ItemStack.EMPTY);
            return row;
        });
    }

    /**
     * Sets a single slot in the player's extra row and marks the data dirty.
     *
     * @param slot 0-indexed slot within the extra row (0–8)
     */
    public void setSlot(UUID uuid, int slot, ItemStack stack) {
        ItemStack[] row = getExtraRow(uuid);
        row[slot] = (stack == null) ? ItemStack.EMPTY : stack;
        setDirty();
    }

    /**
     * Returns {@code true} if the player's extra row contains at least one
     * non-empty stack.
     */
    public boolean hasItems(UUID uuid) {
        ItemStack[] row = extraRows.get(uuid);
        if (row == null)
            return false;
        for (ItemStack s : row) {
            if (s != null && !s.isEmpty())
                return true;
        }
        return false;
    }
}
