package freq.ascension.animation;

import org.joml.Vector3f;

import freq.ascension.Ascension;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class Dash {

    private static BlockState[] palette = {
            Blocks.WHITE_WOOL.defaultBlockState(),
            Blocks.WHITE_STAINED_GLASS.defaultBlockState(),
            Blocks.WHITE_CONCRETE_POWDER.defaultBlockState()
    };

    public static void spawnDashCone(Player player, Vector3f velocityDir, int numParticles, float startRadius,
            float endRadius, float length) {

        // 1. SAFETY CHECK: If the player isn't moving, use their look vector
        if (velocityDir.lengthSquared() < 1e-6) {
            velocityDir = player.getLookAngle().toVector3f();
        }

        // 2. DOUBLE SAFETY: If still zero (unlikely), use a default "Up"
        if (velocityDir.lengthSquared() < 1e-6) {
            velocityDir = new Vector3f(0, 1, 0);
        }

        for (int i = 0; i < numParticles; i++) {
            Vector3f axis = new Vector3f(velocityDir).normalize();

            Vector3f tangent = new Vector3f();
            Vector3f arbitrary = Math.abs(axis.x) < 0.9f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0);
            axis.cross(arbitrary, tangent);
            if (tangent.lengthSquared() < 1e-8f) {
                arbitrary.set(0, 0, 1);
                axis.cross(arbitrary, tangent);
            }
            tangent.normalize();
            Vector3f bitangent = new Vector3f();
            axis.cross(tangent, bitangent).normalize();

            float theta = (float) (Math.random() * 2 * Math.PI);

            Vector3f radial = new Vector3f(tangent)
                    .mul((float) Math.cos(theta))
                    .add(new Vector3f(bitangent).mul((float) Math.sin(theta)));

            Vector3f relativeStart = new Vector3f(radial).mul(startRadius);
            Vector3f relativeEnd = new Vector3f(axis).mul(-length) // Move backwards relative to dash
                    .add(new Vector3f(radial).mul(endRadius));

            // Guard against NaN vectors
            if (Float.isNaN(relativeStart.x) || Float.isNaN(relativeStart.y) || Float.isNaN(relativeStart.z)
                    || Float.isNaN(relativeEnd.x) || Float.isNaN(relativeEnd.y) || Float.isNaN(relativeEnd.z)) {
                Ascension.LOGGER.warn("Dash produced NaN vectors: axis={} tangent={} radial={} relativeEnd={}", axis,
                        tangent, radial, relativeEnd);
                continue;
            }

            // Init on smaller circle
            new VFXBuilder(
                    player.level(),
                    player.position().toVector3f(),
                    palette[(int) (Math.random() * palette.length)],
                    VFXBuilder.instant(relativeStart, GeometrySource.randomRot(),
                            GeometrySource.randomScaleEqual(0.1f, 0.3f)))
                    // Move to larger circle
                    .addKeyframe(relativeEnd, GeometrySource.randomRot(), null, 15)
                    // Shrink to scale 0
                    .addKeyframe(relativeEnd, null, new Vector3f(0.0f), 5);
            ;
        }
    }
}
