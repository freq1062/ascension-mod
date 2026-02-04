package freq.ascension.animation;

import org.joml.Vector3f;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class MagmaBubble {
    public static void spawnMagmaSpikes(Player player, int numSpikes, float radius, float heightScale) {
        Vector3f playerPos = player.position().toVector3f();
        Vector3f origin = playerPos.sub(new Vector3f(radius / 2, radius / 2, radius / 2));

        for (var i = 0; i < numSpikes; i++) {
            float rand = (float) (Math.random() * radius);
            Vector3f relativeStart = origin.add(new Vector3f(rand, 0, rand));
            resolveSurface(player.level(), relativeStart, 4);

            Vector3f dir = GeometrySource.circle(relativeStart, new Vector3f(0, 2, 0), 1, false);

            new VFXBuilder(player.level(), relativeStart, Blocks.MAGMA_BLOCK.defaultBlockState(),
                    VFXBuilder.instant(relativeStart, GeometrySource.faceVector(dir), new Vector3f(1)))
                    .addKeyframeS(null, null, new Vector3f(1, 2, 1), 20)
                    .addKeyframeS(null, null, new Vector3f(1, 0, 1), 20);
        }
    }

    // Sets the y coord of the input loc to the nearest solid block or lava
    private static void resolveSurface(Level level, Vector3f loc, int searchDepth) {
        BlockPos under = new BlockPos(
                new Vec3i((int) Math.floor(loc.x), (int) Math.floor(loc.y), (int) Math.floor(loc.z)));

        for (int depth = 0; depth < searchDepth && under.getY() - depth >= level.getMinY(); depth++) {
            Block block = level.getBlockState(under).getBlock();
            // Block block = world.getBlockAt(baseX, startY - depth, baseZ);
            if (!block.defaultBlockState().isAir()
                    || level.getFluidState(under).is(net.minecraft.tags.FluidTags.LAVA)) {
                loc.set(loc.x, under.getY(), loc.z);
            }
            under = under.below();
        }
    }
}
