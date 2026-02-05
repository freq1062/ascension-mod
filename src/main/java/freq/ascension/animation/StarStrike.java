package freq.ascension.animation;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.math.Transformation;

import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display.BlockDisplay;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;

public class StarStrike {
    public static void spawnGammaRay(Player player, Vector3f targetPos, float blockThickness, Vector3f colorVec) {
        Vector3f origin = GeometrySource.circle(new Vector3f(targetPos).add(0, 60, 0), new Vector3f(0, 1, 0), 10.0f,
                false);
        Vector3f toTarget = new Vector3f(targetPos).sub(origin);
        float distance = toTarget.length();
        Vector3f dir = new Vector3f(toTarget).normalize();
        int packedColor = packColor(colorVec);

        // 1. THE CORE BEAM
        // Translation: (-thick/2, 0, -thick/2) centers the block on its axis
        new VFXBuilder(player.level(), origin, Blocks.SEA_LANTERN.defaultBlockState(),
                VFXBuilder.instant(center(blockThickness, 0), GeometrySource.faceVector(dir),
                        new Vector3f(blockThickness, 0, blockThickness)))
                .addKeyframeS(center(blockThickness, 0), null, new Vector3f(blockThickness, distance, blockThickness),
                        5)
                .addKeyframeS(center(blockThickness, distance).add(new Vector3f(dir).mul(distance)), null,
                        new Vector3f(blockThickness, 0, blockThickness), 10, 20);

        // 2. THE PULSING GLOW
        VFXBuilder glow = new VFXBuilder(player.level(), origin, Blocks.WHITE_STAINED_GLASS.defaultBlockState(),
                VFXBuilder.instant(center(blockThickness + 0.5f, 0), GeometrySource.faceVector(dir),
                        new Vector3f(blockThickness + 0.5f, 0, blockThickness + 0.5f)));

        glow.getEntity().setGlowColorOverride(packedColor);
        glow.getEntity().setBrightnessOverride(new Brightness(15, 15));

        // Pulse Effect: We add keyframes that jitter the thickness while the beam is
        // active
        int pulseSteps = 4;
        int stepDuration = 5;
        for (int i = 0; i < pulseSteps; i++) {
            float addedThick = (i % 2 == 0) ? 0.6f : 0.2f;
            float currentThick = blockThickness + addedThick;

            // We keep the length at 'distance' during the pulse
            glow.addKeyframeS(center(currentThick, 0), null, new Vector3f(currentThick, distance, currentThick),
                    stepDuration);
        }

        // Final Shrink (Match the core beam)
        float finalThick = blockThickness + 0.2f;
        glow.addKeyframeS(center(finalThick, distance).add(new Vector3f(dir).mul(distance)), null,
                new Vector3f(finalThick, 0, finalThick), 10);
    }

    public static int packColor(Vector3f color) {

        int r = (int) (color.x * 255);

        int g = (int) (color.y * 255);

        int b = (int) (color.z * 255);

        return (r << 16) | (g << 8) | b;

    }

    // HELPER: Calculates the local translation needed to center a block of 'thick'
    // size
    public static Vector3f center(float thick, float yOffset) {
        return new Vector3f(-thick * 0.5f, yOffset, -thick * 0.5f);
    }
}
