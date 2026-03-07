package freq.ascension.managers;

import com.mojang.math.Transformation;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import freq.ascension.Ascension;
import freq.ascension.api.ContinuousTask;
import freq.ascension.api.TaskScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Interaction;
import net.minecraft.network.chat.Component;
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

    // Per-display accumulated rotation angle (Y axis), degrees
    private final Map<String, float[]> rotationAngles = new HashMap<>();

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
     * Returns a {@link BlockState} representing the given order — one colored stained glass
     * per order for a distinctive visual without requiring skull NBT injection.
     */
    private static BlockState getOrderBlock(String orderName) {
        return switch (orderName.toLowerCase()) {
            case "sky"    -> Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();
            case "earth"  -> Blocks.BROWN_STAINED_GLASS.defaultBlockState();
            case "ocean"  -> Blocks.CYAN_STAINED_GLASS.defaultBlockState();
            case "magic"  -> Blocks.PURPLE_STAINED_GLASS.defaultBlockState();
            case "flora"  -> Blocks.GREEN_STAINED_GLASS.defaultBlockState();
            case "nether" -> Blocks.RED_STAINED_GLASS.defaultBlockState();
            case "end"    -> Blocks.BLACK_STAINED_GLASS.defaultBlockState();
            default       -> Blocks.WHITE_STAINED_GLASS.defaultBlockState();
        };
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

        // Spawn BlockDisplay centered on the block, 0.5×0.5×0.5 scale
        Display.BlockDisplay display = EntityType.BLOCK_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
        if (display == null) {
            Ascension.LOGGER.warn("[PoiManager] Failed to create BlockDisplay for order: " + orderName);
            return;
        }
        display.setBlockState(getOrderBlock(key));
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

        // Schedule rotation task
        float[] angle = {0f};
        rotationAngles.put(key, angle);

        // Stop any existing rotation task
        ContinuousTask old = rotationTasks.get(key);
        if (old != null) old.stop();

        ContinuousTask rotTask = new ContinuousTask(1, () -> {
            net.minecraft.world.entity.Entity ent = level.getEntity(displayEntityUUIDs.getOrDefault(key, new UUID(0, 0)));
            if (!(ent instanceof Display.BlockDisplay disp)) return;
            if (disp.isRemoved()) return;
            angle[0] = (angle[0] + 1.5f) % 360f;
            Quaternionf rot = new Quaternionf().rotateY((float) Math.toRadians(angle[0]));
            disp.setTransformation(new Transformation(
                    new Vector3f(-0.25f, -0.25f, -0.25f),
                    rot,
                    new Vector3f(0.5f, 0.5f, 0.5f),
                    new Quaternionf()));
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
     * Returns the {@link Display.BlockDisplay} entity associated with the given order's POI, or
     * {@code null} if not tracked or not loaded.
     */
    public Display.BlockDisplay getDisplayEntity(String orderName, MinecraftServer server) {
        String key = orderName.toLowerCase();
        UUID uuid = displayEntityUUIDs.get(key);
        if (uuid == null) return null;
        ServerLevel level = getPoiLevel(server, orderName);
        if (level == null) return null;
        net.minecraft.world.entity.Entity entity = level.getEntity(uuid);
        return entity instanceof Display.BlockDisplay bd ? bd : null;
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

    /** Stops all active rotation tasks. Called on server shutdown. */
    public void stopAllRotationTasks() {
        for (ContinuousTask task : rotationTasks.values()) {
            task.stop();
        }
        rotationTasks.clear();
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
