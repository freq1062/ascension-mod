package freq.ascension.managers;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import freq.ascension.Ascension;
import freq.ascension.api.ContinuousTask;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.TallFlowerBlock;

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
        if (isHoldingPlant(player)) return true;

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

    /** Returns true if the player is holding a plant item in mainhand or offhand. */
    public static boolean isHoldingPlant(ServerPlayer player) {
        return isPlantItem(player.getMainHandItem()) || isPlantItem(player.getOffhandItem());
    }

    /** Returns true if the given ItemStack is a plant-type item. */
    public static boolean isPlantItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.is(ItemTags.FLOWERS) || stack.is(ItemTags.SAPLINGS) || stack.is(ItemTags.LEAVES))
            return true;
        if (stack.getItem() instanceof BlockItem bi) {
            Block block = bi.getBlock();
            if (block instanceof BushBlock
                    || block instanceof FlowerBlock
                    || block instanceof TallFlowerBlock
                    || block instanceof SaplingBlock
                    || block instanceof LeavesBlock)
                return true;
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
        return block == Blocks.SHORT_GRASS ||
                block == Blocks.TALL_GRASS ||
                block == Blocks.FERN ||
                block == Blocks.LARGE_FERN ||
                block == Blocks.DEAD_BUSH ||
                block == Blocks.GRASS_BLOCK ||
                block == Blocks.WHEAT ||
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
