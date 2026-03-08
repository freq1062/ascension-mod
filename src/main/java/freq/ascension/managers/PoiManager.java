package freq.ascension.managers;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.math.Transformation;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import freq.ascension.Ascension;
import freq.ascension.api.ContinuousTask;
import freq.ascension.api.TaskScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Interaction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

/**
 * Persistent store for Order POI (Point of Interest) positions, dimensions, terrain snapshots,
 * and runtime references to the spawned block display / interaction entities.
 *
 * <p>Positions are stored as packed {@code long} via {@link BlockPos#asLong()} /
 * {@link BlockPos#of(long)}.
 *
 * <p>Terrain snapshots are stored as lists of {@link SnapshotEntry} per order, where each
 * entry holds a relative offset and the original {@link BlockState}. Block states are encoded
 * as their integer registry ID via {@link Block#BLOCK_STATE_REGISTRY}.
 */
public class PoiManager extends SavedData {

    private static final String KEY = "ascension_poi";

    // Persistent fields (saved to disk)
    private final Map<String, Long> packedPositions = new HashMap<>();
    private final Map<String, String> dimensionKeys = new HashMap<>();
    private final Map<String, Integer> radii = new HashMap<>();
    private final Map<String, List<SnapshotEntry>> terrainSnapshots = new HashMap<>();

    // Transient runtime fields (not saved)
    private final Map<String, UUID> displayEntityUUIDs = new HashMap<>();
    private final Map<String, UUID> interactionEntityUUIDs = new HashMap<>();
    private final Map<String, ContinuousTask> rotationTasks = new HashMap<>();

    // Per-display accumulated rotation angles [x, y, z], degrees
    private final Map<String, float[]> rotationAngles = new HashMap<>();

    /** Base64 skull textures for each order — injected into BlockDisplay NBT. */
    private static final Map<String, String> ORDER_HEAD_TEXTURES = Map.of(
        "sky",    "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOWU1MmY3OTYwZmYzY2VjMmY1MTlhNjM1MzY0OGM2ZTMzYmM1MWUxMzFjYzgwOTE3Y2YxMzA4MWRlY2JmZjI0ZCJ9fX0=",
        "earth",  "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTlhNWExZTY5YjRmODEwNTYyNTc1MmJjZWUyNTM0MDY2NGIwODlmYTFiMmY1MjdmYTkxNDNkOTA2NmE3YWFkMiJ9fX0=",
        "ocean",  "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzQxMzBjYTY5NzQ5MTRjYmFhYmFlYzJkYzRkMWVkNmMzM2EzMGIzOGQ3OTFjMWQwNTdhMjlhNWQ1MWI5OWZmOSJ9fX0=",
        "magic",  "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZWYwYTY0ZGM3NmE2NTY3MDM3ZmQ5ZmUyZDQwYTk4MDNmMGZlYTg5MzFmMWM2NDI0ZWE0OGNhYjVmNjI5NTBlZSJ9fX0=",
        "flora",  "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvM2JlOGRiNDhhNjNmMDliYjg1MDQ0ZDlkYmQzMjVjZmMzODgxYzAwOTEyZTllZGMzM2JmMjJmZGEzMjBiZWE2NiJ9fX0=",
        "nether", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNmI5NWRjMDBiZTQxOTIxOGRhM2VjMTY2ODVjZjA5OTAyYzE5M2YwMDRhMDlmNGY3M2VhYTJiOWRkMTA4MGM1ZSJ9fX0=",
        "end",    "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzc2MzhjMzhkMzlhNjdkODk2MjY3NDNmMDZiOWM3YmU1YzUwY2Y4MzM1ZDJlOGYzOWViMWRhZDBkZjBmNzNkNiJ9fX0="
    );

    /** A single captured block from a terrain snapshot (relative offsets + original state). */
    public record SnapshotEntry(int dx, int dy, int dz, BlockState state) {}

    private PoiManager() {}

    // ─── Codec ───────────────────────────────────────────────────────────────

    /** Codec for a single snapshot entry as [dx, dy, dz, blockStateId]. */
    private static final Codec<SnapshotEntry> ENTRY_CODEC = Codec.list(Codec.INT).xmap(
            list -> {
                BlockState state = Block.BLOCK_STATE_REGISTRY.byId(list.get(3));
                if (state == null) state = Blocks.AIR.defaultBlockState();
                return new SnapshotEntry(list.get(0), list.get(1), list.get(2), state);
            },
            entry -> List.of(entry.dx(), entry.dy(), entry.dz(),
                    Block.BLOCK_STATE_REGISTRY.getId(entry.state()))
    );

    private static final Codec<PoiManager> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.unboundedMap(Codec.STRING, Codec.LONG)
                            .optionalFieldOf("positions", Map.of())
                            .forGetter(m -> Map.copyOf(m.packedPositions)),
                    Codec.unboundedMap(Codec.STRING, Codec.STRING)
                            .optionalFieldOf("dimension_keys", Map.of())
                            .forGetter(m -> Map.copyOf(m.dimensionKeys)),
                    Codec.unboundedMap(Codec.STRING, Codec.INT)
                            .optionalFieldOf("radii", Map.of())
                            .forGetter(m -> Map.copyOf(m.radii)),
                    Codec.unboundedMap(Codec.STRING, Codec.list(ENTRY_CODEC))
                            .optionalFieldOf("terrain_snapshots", Map.of())
                            .forGetter(m -> Map.copyOf(m.terrainSnapshots))
            ).apply(instance, PoiManager::fromData)
    );

    public static final SavedDataType<PoiManager> TYPE = new SavedDataType<>(
            KEY,
            PoiManager::new,
            CODEC,
            null
    );

    private static PoiManager fromData(
            Map<String, Long> positions,
            Map<String, String> dimensionKeys,
            Map<String, Integer> radii,
            Map<String, List<SnapshotEntry>> snapshots) {
        PoiManager m = new PoiManager();
        m.packedPositions.putAll(positions);
        m.dimensionKeys.putAll(dimensionKeys);
        m.radii.putAll(radii);
        m.terrainSnapshots.putAll(snapshots);
        return m;
    }

    // ─── Retrieval ────────────────────────────────────────────────────────────

    public static PoiManager get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean hasPoiPosition(String orderName) {
        return packedPositions.containsKey(orderName.toLowerCase());
    }

    public BlockPos getPoiPosition(String orderName) {
        Long packed = packedPositions.get(orderName.toLowerCase());
        return packed == null ? null : BlockPos.of(packed);
    }

    public String getPoiDimension(String orderName) {
        return dimensionKeys.getOrDefault(orderName.toLowerCase(), "minecraft:overworld");
    }

    public int getPoiRadius(String orderName) {
        return radii.getOrDefault(orderName.toLowerCase(), 10);
    }

    public List<SnapshotEntry> getTerrainSnapshot(String orderName) {
        return terrainSnapshots.getOrDefault(orderName.toLowerCase(), List.of());
    }

    public Set<String> getAllPoiOrders() {
        return Collections.unmodifiableSet(packedPositions.keySet());
    }

    // ─── Mutation ─────────────────────────────────────────────────────────────

    public void setPoiRadius(String orderName, int radius) {
        radii.put(orderName.toLowerCase(), radius);
        setDirty();
    }

    public void setPoiData(String orderName, BlockPos pos, String dimensionKey, int radius,
            List<SnapshotEntry> snapshot) {
        String key = orderName.toLowerCase();
        packedPositions.put(key, pos.asLong());
        dimensionKeys.put(key, dimensionKey);
        radii.put(key, radius);
        terrainSnapshots.put(key, new ArrayList<>(snapshot));
        setDirty();
    }

    public void clearPoiData(String orderName) {
        String key = orderName.toLowerCase();
        packedPositions.remove(key);
        dimensionKeys.remove(key);
        radii.remove(key);
        terrainSnapshots.remove(key);
        displayEntityUUIDs.remove(key);
        interactionEntityUUIDs.remove(key);
        ContinuousTask task = rotationTasks.remove(key);
        if (task != null) task.stop();
        rotationAngles.remove(key);
        setDirty();
    }

    // ─── Entity management ────────────────────────────────────────────────────

    /**
     * Creates a {@link Items#PLAYER_HEAD} {@link ItemStack} with the order-specific base64 skull
     * texture set via {@link DataComponents#PROFILE}.
     */
    private static ItemStack createOrderSkull(String key) {
        ItemStack skull = new ItemStack(Items.PLAYER_HEAD);
        String base64 = ORDER_HEAD_TEXTURES.get(key);
        if (base64 != null) {
            GameProfile profile = new GameProfile(java.util.UUID.randomUUID(), "AscensionPOI");
            profile.properties().put("textures", new Property("textures", base64));
            skull.set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile));
        }
        return skull;
    }

    /**
     * Removes any previously-spawned display / interaction entities for the order, then spawns
     * a new spinning {@link Display.BlockDisplay} and a {@link Interaction} entity. A
     * {@link ContinuousTask} is scheduled in the provided {@link TaskScheduler} to rotate the
     * display every tick.
     *
     * <p>Must be called on the server thread.
     */
    public void spawnPoiEntities(String orderName, ServerLevel level, BlockPos pos, TaskScheduler scheduler) {
        String key = orderName.toLowerCase();

        // Remove old entities if present
        removePreviousEntities(key, level);

        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;

                // Spawn ItemDisplay showing the order's custom skull, centered on the block
        Display.ItemDisplay display = EntityType.ITEM_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
        if (display == null) {
            Ascension.LOGGER.warn("[PoiManager] Failed to create ItemDisplay for order: " + orderName);
            return;
        }
        display.setItemStack(createOrderSkull(key));
        display.setPos(cx, cy, cz);
        display.setTransformation(new Transformation(
                new Vector3f(-0.25f, -0.25f, -0.25f),
                new Quaternionf(),
                new Vector3f(0.5f, 0.5f, 0.5f),
                new Quaternionf()));
        display.setBrightnessOverride(Brightness.FULL_BRIGHT);
        display.setTransformationInterpolationDuration(2);

        level.addFreshEntity(display);
        displayEntityUUIDs.put(key, display.getUUID());

        // Spawn Interaction entity for right-click detection
        Interaction interaction = EntityType.INTERACTION.create(level, EntitySpawnReason.TRIGGERED);
        if (interaction == null) {
            Ascension.LOGGER.warn("[PoiManager] Failed to create Interaction entity for order: " + orderName);
            return;
        }
        interaction.setPos(cx, cy - 0.25, cz);
        interaction.setCustomName(Component.literal("ascension_poi_" + key));
        level.addFreshEntity(interaction);
        interactionEntityUUIDs.put(key, interaction.getUUID());

        // Stop any existing rotation task before scheduling a new one
        ContinuousTask old = rotationTasks.get(key);
        if (old != null) old.stop();

        // 3-axis accumulated angles in degrees: [x, y, z]
        float[] angles = {0f, 0f, 0f};
        rotationAngles.put(key, angles);

        // Fix 1: rotate around the visual center of the 0.5×0.5×0.5 cube on all 3 axes.
        // The cube occupies local-space [0,0.5]³ so its center is at (0.25,0.25,0.25).
        // After applying leftRotation R, that center moves to R*(0.25,0.25,0.25).
        // We must translate by -(R*center) so the visual center stays at entity origin.
        ContinuousTask rotTask = new ContinuousTask(1, () -> {
            net.minecraft.world.entity.Entity ent = level.getEntity(displayEntityUUIDs.getOrDefault(key, new UUID(0, 0)));
            if (!(ent instanceof Display.ItemDisplay disp) || disp.isRemoved()) return;

            angles[0] = (angles[0] + 0.7f) % 360f; // X axis — slow
            angles[1] = (angles[1] + 1.5f) % 360f; // Y axis — medium
            angles[2] = (angles[2] + 0.4f) % 360f; // Z axis — slowest

            Quaternionf rot = new Quaternionf()
                    .rotateX((float) Math.toRadians(angles[0]))
                    .rotateY((float) Math.toRadians(angles[1]))
                    .rotateZ((float) Math.toRadians(angles[2]));

            // Pivot-corrected translation: keep visual center at entity origin
            Vector3f center = new Vector3f(0.25f, 0.25f, 0.25f);
            Vector3f rotatedCenter = rot.transform(center, new Vector3f());
            Vector3f translation = new Vector3f(-rotatedCenter.x, -rotatedCenter.y, -rotatedCenter.z);

            disp.setTransformation(new Transformation(translation, rot, new Vector3f(0.5f, 0.5f, 0.5f), new Quaternionf()));
            disp.setTransformationInterpolationDuration(2);
            disp.setTransformationInterpolationDelay(0);
        });
        rotationTasks.put(key, rotTask);
        scheduler.schedule(rotTask);
    }

    private void removePreviousEntities(String key, ServerLevel level) {
        UUID oldDisplay = displayEntityUUIDs.get(key);
        if (oldDisplay != null) {
            net.minecraft.world.entity.Entity e = level.getEntity(oldDisplay);
            if (e != null) e.discard();
            displayEntityUUIDs.remove(key);
        }
        UUID oldInteraction = interactionEntityUUIDs.get(key);
        if (oldInteraction != null) {
            net.minecraft.world.entity.Entity e = level.getEntity(oldInteraction);
            if (e != null) e.discard();
            interactionEntityUUIDs.remove(key);
        }
        ContinuousTask old = rotationTasks.remove(key);
        if (old != null) old.stop();
        rotationAngles.remove(key);
    }

    /**
     * Returns the {@link Display.ItemDisplay} entity associated with the given order's POI, or
     * {@code null} if not tracked or not loaded.
     */
    public Display.ItemDisplay getDisplayEntity(String orderName, MinecraftServer server) {
        String key = orderName.toLowerCase();
        UUID uuid = displayEntityUUIDs.get(key);
        if (uuid == null) return null;
        ServerLevel level = getPoiLevel(server, orderName);
        if (level == null) return null;
        net.minecraft.world.entity.Entity entity = level.getEntity(uuid);
        return entity instanceof Display.ItemDisplay id ? id : null;
    }

    /**
     * Returns the {@link ServerLevel} that hosts this order's POI, or the overworld as fallback.
     */
    public ServerLevel getPoiLevel(MinecraftServer server, String orderName) {
        String dimKey = getPoiDimension(orderName);
        net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> levelKey =
                net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.DIMENSION,
                        net.minecraft.resources.ResourceLocation.parse(dimKey));
        ServerLevel level = server.getLevel(levelKey);
        return level != null ? level : server.overworld();
    }

    /** Stops all active rotation tasks and discards all spawned display + interaction entities. */
    public void stopAllRotationTasks(ServerLevel... levels) {
        for (ContinuousTask task : rotationTasks.values()) {
            if (task != null) task.stop();
        }
        rotationTasks.clear();
        rotationAngles.clear();

        // Discard all BlockDisplay entities across the provided levels
        for (String key : new HashSet<>(displayEntityUUIDs.keySet())) {
            UUID uuid = displayEntityUUIDs.get(key);
            if (uuid == null) continue;
            for (ServerLevel level : levels) {
                net.minecraft.world.entity.Entity e = level.getEntity(uuid);
                if (e != null) e.discard();
            }
        }
        displayEntityUUIDs.clear();

        // Discard all Interaction entities
        for (String key : new HashSet<>(interactionEntityUUIDs.keySet())) {
            UUID uuid = interactionEntityUUIDs.get(key);
            if (uuid == null) continue;
            for (ServerLevel level : levels) {
                net.minecraft.world.entity.Entity e = level.getEntity(uuid);
                if (e != null) e.discard();
            }
        }
        interactionEntityUUIDs.clear();
    }

    /** Returns {@code true} if there are any active rotation {@link ContinuousTask}s. */
    public boolean hasActiveRotationTasks() {
        return !rotationTasks.isEmpty();
    }

    // ─── Snapshot Capture ─────────────────────────────────────────────────────

    /**
     * Captures all non-air {@link BlockState}s within a sphere of the given radius centered on
     * {@code center}. Radius is clamped to 20 to prevent runaway captures.
     */
    public static List<SnapshotEntry> captureSnapshot(ServerLevel level, BlockPos center, int radius) {
        int r = Math.min(radius, 20);
        List<SnapshotEntry> entries = new ArrayList<>();
        int rSq = r * r;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dy * dy + dz * dz > rSq) continue;
                    BlockPos bp = center.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(bp);
                    if (!state.isAir()) {
                        entries.add(new SnapshotEntry(dx, dy, dz, state));
                    }
                }
            }
        }
        return entries;
    }

    /**
     * Restores the terrain for the given order using the stored snapshot.
     *
     * <p>First, blocks in the radius that were NOT in the original snapshot (i.e. placed after
     * the snapshot was taken) are set to air. Then all snapshotted blocks are restored.
     */
    public void resetTerrain(String orderName, ServerLevel level) {
        String key = orderName.toLowerCase();
        List<SnapshotEntry> snapshot = terrainSnapshots.getOrDefault(key, List.of());
        BlockPos center = getPoiPosition(key);
        if (center == null) return;
        int radius = Math.min(getPoiRadius(key), 20);

        // Build a set of relative positions that were in the snapshot
        Set<Long> snapshotOffsets = new HashSet<>();
        for (SnapshotEntry e : snapshot) {
            snapshotOffsets.add(BlockPos.asLong(e.dx(), e.dy(), e.dz()));
        }

        // Clear blocks placed SINCE snapshot (not in snapshot, not air)
        int rSq = radius * radius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dy * dy + dz * dz > rSq) continue;
                    long offset = BlockPos.asLong(dx, dy, dz);
                    if (!snapshotOffsets.contains(offset)) {
                        BlockPos bp = center.offset(dx, dy, dz);
                        if (!level.getBlockState(bp).isAir()) {
                            level.setBlock(bp, Blocks.AIR.defaultBlockState(), 3);
                        }
                    }
                }
            }
        }

        // Restore all snapshotted blocks
        for (SnapshotEntry e : snapshot) {
            level.setBlock(center.offset(e.dx(), e.dy(), e.dz()), e.state(), 3);
        }
    }
}
