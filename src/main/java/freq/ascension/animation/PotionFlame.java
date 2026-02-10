package freq.ascension.animation;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class PotionFlame {

    public static void spawnPotionFlame(ServerPlayer player, int duration) {
        Vector3f origin = player.position().toVector3f().add(0, 1, 0);

        int numParticles = 15 + (duration / 2);

        for (int i = 0; i < numParticles; i++) {
            // Random horizontal offset
            float offsetX = (float) (Math.random() - 0.5) * 0.8f;
            float offsetZ = (float) (Math.random() - 0.5) * 0.8f;
            Vector3f startPos = new Vector3f(offsetX, 0, offsetZ);

            // Random upward velocity (flame rises at different speeds)
            float riseHeight = 1.0f + (float) Math.random() * 2.5f;
            float riseTime = 10 + (int) (Math.random() * 15);

            // Random rotation and scale
            Quaternionf randomRot = GeometrySource.randomRot();
            Vector3f randomScale = GeometrySource.randomScaleEqual(0.15f, 0.4f);

            // Choose block based on progression (more transparent over time)
            float progress = (float) i / numParticles;
            BlockState block;
            if (progress < 0.3f) {
                block = Blocks.PINK_CONCRETE.defaultBlockState();
            } else if (progress < 0.6f) {
                block = Blocks.PINK_STAINED_GLASS.defaultBlockState();
            } else {
                block = Blocks.PINK_WOOL.defaultBlockState();
            }

            Vector3f endPos = new Vector3f(
                    startPos.x * 0.3f,
                    riseHeight,
                    startPos.z * 0.3f);

            // Spin animation: rotate while rising
            Quaternionf endRot = new Quaternionf().rotateXYZ(
                    (float) Math.toRadians(randomRot.x + (float) (Math.random() * 360)),
                    (float) Math.toRadians(randomRot.y + (float) (Math.random() * 360)),
                    (float) Math.toRadians(randomRot.z + (float) (Math.random() * 360)));

            new VFXBuilder(
                    player.level(),
                    origin,
                    block,
                    VFXBuilder.instant(startPos, randomRot, randomScale))
                    .addKeyframe(endPos, endRot, randomScale, (int) riseTime)
                    .addKeyframe(endPos, endRot, new Vector3f(0.0f), 5);
        }
    }
}
