package freq.ascension.animation;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class PotionFlame {

    private static final BlockState[] PINK_PALETTE = new BlockState[] {
            Blocks.PINK_STAINED_GLASS.defaultBlockState(),
            Blocks.PINK_CONCRETE.defaultBlockState(),
            Blocks.PINK_GLAZED_TERRACOTTA.defaultBlockState(),
    };

    /** @param durationTicks display duration for the flame animation */
    public static void spawnPotionFlame(ServerPlayer player, int durationTicks) {
        spawnPotionFlame(player, durationTicks, 0);
    }

    /**
     * Spawn pink flame VFX when a potion effect is extended. PDF: random points around feet,
     * pink block palette (stained glass, concrete, glazed terracotta), vectors face up,
     * more transparent toward top of hitbox; particle count scaled by duration granted.
     *
     * @param durationTicks display duration for the flame animation
     * @param durationGrantedTicks extra duration granted to the effect (used to scale particle count)
     */
    public static void spawnPotionFlame(ServerPlayer player, int durationTicks, int durationGrantedTicks) {
        Vector3f origin = player.position().toVector3f().add(0, 0.1f, 0);

        int numParticles = Math.min(40, 10 + (durationGrantedTicks / 20)); // scale by seconds granted, cap 40

        for (int i = 0; i < numParticles; i++) {
            float offsetX = (float) (Math.random() - 0.5) * 0.8f;
            float offsetZ = (float) (Math.random() - 0.5) * 0.8f;
            Vector3f startPos = new Vector3f(offsetX, 0, offsetZ);

            float riseHeight = 0.6f + (float) Math.random() * 1.4f; // up to ~2 blocks (player hitbox top)
            int riseTime = 10 + (int) (Math.random() * 15);

            Quaternionf randomRot = GeometrySource.randomRot();
            Vector3f randomScale = GeometrySource.randomScaleEqual(0.15f, 0.4f);

            BlockState block = PINK_PALETTE[(int) (Math.random() * PINK_PALETTE.length)];

            Vector3f endPos = new Vector3f(
                    startPos.x * 0.3f,
                    riseHeight,
                    startPos.z * 0.3f);

            // More transparent toward top: scale down as we go up (simulates fade)
            float heightProgress = riseHeight / 2.0f;
            Vector3f endScale = new Vector3f(
                    randomScale.x * (1.0f - heightProgress * 0.7f),
                    randomScale.y * (1.0f - heightProgress * 0.7f),
                    randomScale.z * (1.0f - heightProgress * 0.7f));

            Quaternionf endRot = new Quaternionf().rotateXYZ(
                    (float) Math.toRadians((float) (Math.random() * 360)),
                    (float) Math.toRadians((float) (Math.random() * 360)),
                    (float) Math.toRadians((float) (Math.random() * 360)));

            new VFXBuilder(
                    player.level(),
                    origin,
                    block,
                    VFXBuilder.instant(startPos, randomRot, randomScale))
                    .addKeyframe(endPos, endRot, endScale, riseTime)
                    .addKeyframe(endPos, endRot, new Vector3f(0.0f), 5);
        }
    }
}
