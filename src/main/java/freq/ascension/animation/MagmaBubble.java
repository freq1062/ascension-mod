package freq.ascension.animation;

import java.util.concurrent.ThreadLocalRandom;

import org.joml.Vector3f;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

public class MagmaBubble {
    public static void spawnMagmaSpikes(Player player, int numSpikes, float radius, float heightScale) {
        Level level = player.level();
        Vector3f playerPos = player.position().toVector3f();

        for (var i = 0; i < numSpikes; i++) {
            // 1. Calculate a World Position for this spike
            float rx = (float) ((Math.random() - 0.5) * radius);
            float rz = (float) ((Math.random() - 0.5) * radius);
            Vector3f worldPos = new Vector3f(playerPos).add(rx, 0, rz);

            // 2. Anchor base to the ground via heightmap (reliable for any Y offset)
            int topY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) worldPos.x, (int) worldPos.z);
            worldPos.y = topY;

            // 3. Direction of the spike (randomly tilted up)
            Vector3f spikeDir = new Vector3f(
                    (float) (Math.random() - 0.5) * 0.5f,
                    0.5f,
                    (float) (Math.random() - 0.5) * 0.5f).normalize();

            ThreadLocalRandom random = ThreadLocalRandom.current();
            float thickness = 0.35f + random.nextFloat(0.25f);
            int initialDelay = 3 + random.nextInt(2);

            // 4. THE VFX BUILDER
            // Tripod (Entity) goes at worldPos.
            // Camera (Translation) stays at 0,0,0 relative to tripod.
            new VFXBuilder(level, worldPos, Blocks.MAGMA_BLOCK.defaultBlockState(),
                    VFXBuilder.instant(new Vector3f(0), GeometrySource.faceVector(spikeDir),
                            new Vector3f(thickness, 0.0f, thickness)))
                    .addKeyframeS(null, null, new Vector3f(thickness, heightScale - 0.5f, thickness), 4, initialDelay) // Grow
                    .withAction(() -> {
                        if (level instanceof ServerLevel sl) {
                            sl.sendParticles(
                                    net.minecraft.core.particles.ParticleTypes.FLAME,
                                    worldPos.x, worldPos.y + 0.2, worldPos.z,
                                    3, 0.12, 0.25, 0.12, 0.01);
                            sl.sendParticles(
                                    net.minecraft.core.particles.ParticleTypes.LAVA,
                                    worldPos.x, worldPos.y + 0.2, worldPos.z,
                                    1, 0.04, 0.05, 0.04, 0);
                        }
                    })
                    .addKeyframeS(null, null, new Vector3f(thickness, 0.0f, thickness), 10, 20) // Sink
                    .withAction(() -> {
                        if (level instanceof ServerLevel sl) {
                            sl.sendParticles(
                                    net.minecraft.core.particles.ParticleTypes.CAMPFIRE_COSY_SMOKE,
                                    worldPos.x, worldPos.y + 0.2, worldPos.z,
                                    2, 0.2, 0.05, 0.2, 0.005);
                        }
                    });

        }
    }

    // Sets the y coord of the input loc to the nearest solid (non-passable) block or lava surface.
    // Skips passable blocks like grass, flowers, and plants so thorns spawn on solid ground.
    public static void resolveSurface(Level level, Vector3f loc, int searchDepth) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(loc.x, loc.y, loc.z);

        for (int i = 0; i < searchDepth; i++) {
            BlockState state = level.getBlockState(pos);
            boolean isLava = level.getFluidState(pos).is(net.minecraft.tags.FluidTags.LAVA);
            if (isLava) {
                loc.y = pos.getY() + 1.0f;
                return;
            }
            // Skip air and passable blocks (grass, plants, flowers, etc.)
            if (!state.isAir() && state.isSolid()) {
                loc.y = pos.getY() + 1.0f; // spawn on top of the solid block
                return;
            }
            pos.move(0, -1, 0);
        }
    }
}
