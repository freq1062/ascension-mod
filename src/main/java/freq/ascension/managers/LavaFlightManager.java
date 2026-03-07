package freq.ascension.managers;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which players currently have lava-flight active (elytra-style creative
 * flight granted while submerged in lava by the Nether order passive).
 *
 * <p>Kept outside {@code LavaSwimmingMixin} because Mixin rules forbid non-private
 * static members on Mixin classes.
 */
public final class LavaFlightManager {

    private static final Map<UUID, Boolean> LAVA_FLIGHT_ACTIVE = new ConcurrentHashMap<>();

    private LavaFlightManager() {}

    public static boolean isActive(UUID uuid) {
        return LAVA_FLIGHT_ACTIVE.getOrDefault(uuid, false);
    }

    public static void setActive(UUID uuid, boolean active) {
        if (active) {
            LAVA_FLIGHT_ACTIVE.put(uuid, true);
        } else {
            LAVA_FLIGHT_ACTIVE.remove(uuid);
        }
    }

    /** Called from the DISCONNECT handler to purge stale flight state. */
    public static void cleanup(UUID uuid) {
        LAVA_FLIGHT_ACTIVE.remove(uuid);
    }
}
