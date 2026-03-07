package freq.ascension.managers;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import freq.ascension.Ascension;
import freq.ascension.api.ContinuousTask;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class PlantProximityManager {
    private static final Map<UUID, Boolean> NEAR_PLANTS = new ConcurrentHashMap<>();
    private static final int PLANT_CHECK_RADIUS = 5;

    public static void init() {
        // Check every 5 ticks for more responsive plant proximity detection
        Ascension.scheduler.schedule(new ContinuousTask(5, () -> {
            for (ServerPlayer player : Ascension.getServer().getPlayerList().getPlayers()) {
                boolean nearPlant = isPlayerNearPlant(player);
                NEAR_PLANTS.put(player.getUUID(), nearPlant);
            }
        }));
    }

    public static boolean isNearPlant(ServerPlayer player) {
        return NEAR_PLANTS.getOrDefault(player.getUUID(), false);
    }

    /** Synchronous on-demand check — bypasses the cache for time-critical callers like sculk mixins. */
    public static boolean isNearPlantSync(ServerPlayer player) {
        return isPlayerNearPlant(player);
    }

    private static boolean isPlayerNearPlant(ServerPlayer player) {
        BlockPos playerPos = player.blockPosition();

        // Check all blocks in a 5-block radius
        for (int x = -PLANT_CHECK_RADIUS; x <= PLANT_CHECK_RADIUS; x++) {
            for (int y = -PLANT_CHECK_RADIUS; y <= PLANT_CHECK_RADIUS; y++) {
                for (int z = -PLANT_CHECK_RADIUS; z <= PLANT_CHECK_RADIUS; z++) {
                    BlockPos checkPos = playerPos.offset(x, y, z);
                    Block block = player.level().getBlockState(checkPos).getBlock();

                    if (isPlantBlock(block, checkPos, player)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private static boolean isPlantBlock(Block block, BlockPos pos, ServerPlayer player) {
        // Check if block is in the plants tag or is a known plant block
        if (player.level().getBlockState(pos).is(BlockTags.FLOWERS) ||
                player.level().getBlockState(pos).is(BlockTags.CROPS) ||
                player.level().getBlockState(pos).is(BlockTags.LEAVES) ||
                player.level().getBlockState(pos).is(BlockTags.SAPLINGS) ||
                player.level().getBlockState(pos).is(BlockTags.SMALL_FLOWERS)) {
            return true;
        }

        // Additional plant blocks not always in tags
        return block == Blocks.WHEAT ||
                block == Blocks.CARROTS ||
                block == Blocks.POTATOES ||
                block == Blocks.BEETROOTS ||
                block == Blocks.MELON_STEM ||
                block == Blocks.PUMPKIN_STEM ||
                block == Blocks.SWEET_BERRY_BUSH ||
                block == Blocks.CACTUS ||
                block == Blocks.SUGAR_CANE ||
                block == Blocks.BAMBOO ||
                block == Blocks.KELP ||
                block == Blocks.KELP_PLANT ||
                block == Blocks.SEAGRASS ||
                block == Blocks.TALL_SEAGRASS ||
                block == Blocks.SEA_PICKLE ||
                block == Blocks.NETHER_WART ||
                block == Blocks.CRIMSON_FUNGUS ||
                block == Blocks.WARPED_FUNGUS ||
                block == Blocks.CRIMSON_ROOTS ||
                block == Blocks.WARPED_ROOTS ||
                block == Blocks.BROWN_MUSHROOM ||
                block == Blocks.RED_MUSHROOM ||
                block == Blocks.VINE ||
                block == Blocks.GLOW_LICHEN ||
                block == Blocks.MOSS_CARPET ||
                block == Blocks.MOSS_BLOCK ||
                block == Blocks.AZALEA ||
                block == Blocks.FLOWERING_AZALEA ||
                block == Blocks.SPORE_BLOSSOM ||
                block == Blocks.LILY_PAD ||
                block == Blocks.COCOA ||
                block == Blocks.CAVE_VINES ||
                block == Blocks.CAVE_VINES_PLANT ||
                block == Blocks.PITCHER_CROP ||
                block == Blocks.PITCHER_PLANT ||
                block == Blocks.TORCHFLOWER ||
                block == Blocks.TORCHFLOWER_CROP;
    }
}
