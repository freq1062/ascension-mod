package freq.ascension.animation;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public class StarStrike {
        public static void spawnGammaRay(Player player, Vector3f targetPos, float blockThickness, Vector3f colorVec) {
                Vector3f origin = GeometrySource.circle(new Vector3f(targetPos).add(0, 60, 0), new Vector3f(0, 1, 0),
                                10.0f, false);
                Vector3f toTarget = new Vector3f(targetPos).sub(origin);
                float distance = toTarget.length();
                Vector3f dir = new Vector3f(toTarget).normalize();
                Quaternionf rotation = GeometrySource.faceVector(dir);
                int packedColor = packColor(colorVec);

                float T = blockThickness;
                Vector3f coreCenter = rotation.transform(new Vector3f(-T * 0.5f, 0, -T * 0.5f), new Vector3f());

                new VFXBuilder(player.level(), origin, Blocks.SEA_LANTERN.defaultBlockState(),
                                VFXBuilder.instant(coreCenter, rotation, new Vector3f(T, 0, T)))

                                // Blast Down (Stay centered at top)
                                .addKeyframeS(coreCenter, null, new Vector3f(T, distance, T), 5)
                                .withAction(() -> {
                                        Level level = player.level();
                                        level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.GENERIC_EXPLODE,
                                                        SoundSource.PLAYERS, 0.6f,
                                                        1.4f);
                                        level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.TOTEM_USE,
                                                        SoundSource.PLAYERS, 1.0f, 0.5f);
                                        level.playSound(null, origin.x, origin.y, origin.z,
                                                        SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS,
                                                        1.0f,
                                                        1.0f);
                                        level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.TRIDENT_THUNDER,
                                                        SoundSource.PLAYERS, 1.0f,
                                                        1.0f);
                                        applyScreenShake(level, targetPos, 0.4f, 15.0f);
                                })

                                // Shrink Down (Move translation to target while scaling Y to 0)
                                // Translation = coreCenter + (direction * distance)
                                .addKeyframeS(new Vector3f(coreCenter).add(new Vector3f(dir).mul(distance)),
                                                null, new Vector3f(T, 0, T), 10, 20);

                float G = blockThickness + 0.5f;
                Vector3f glowCenter = rotation.transform(new Vector3f(-G * 0.5f, 0, -G * 0.5f), new Vector3f());

                VFXBuilder glow = new VFXBuilder(player.level(), origin, Blocks.WHITE_STAINED_GLASS.defaultBlockState(),
                                VFXBuilder.instant(glowCenter, rotation, new Vector3f(G, 0, G)));

                glow.getEntity().setGlowColorOverride(packedColor);
                glow.getEntity().setBrightnessOverride(new Brightness(15, 15));
                glow.getEntity().setGlowingTag(true);
                glow.getEntity().setViewRange(5.0f);

                // Pulse width
                int pulseSteps = 4;
                for (int i = 0; i < pulseSteps; i++) {
                        float added = (i % 2 == 0) ? 0.6f : 0.2f;
                        float currentG = T + added;

                        // Recalculate the rotated center for the new thickness
                        Vector3f pulseCenter = rotation.transform(new Vector3f(-currentG * 0.5f, 0, -currentG * 0.5f),
                                        new Vector3f());
                        glow.addKeyframeS(pulseCenter, null, new Vector3f(currentG, distance, currentG), 5)
                                        .withAction(() -> {
                                                applyScreenShake(player.level(), targetPos, 0.15f, 10.0f);
                                                if (player.level() instanceof ServerLevel sL) {
                                                        float half = currentG * 0.5f;
                                                        int samples = 20;
                                                        for (int j = 0; j < samples; j++) {
                                                                double a = 2 * Math.PI * j / samples;
                                                                Vector3f local = new Vector3f(
                                                                                (float) Math.cos(a) * half, 0f,
                                                                                (float) Math.sin(a) * half);
                                                                Vector3f world = rotation
                                                                                .transform(local, new Vector3f())
                                                                                .add(origin);
                                                                sL.sendParticles(ParticleTypes.END_ROD, world.x,
                                                                                world.y + 0.5, world.z, 2, 0.04, 0.2,
                                                                                0.04, 0.01);
                                                                if (j % 5 == 0) {
                                                                        sL.sendParticles(ParticleTypes.EXPLOSION,
                                                                                        world.x, world.y + 0.5, world.z,
                                                                                        1, 0.0, 0.0, 0.0, 0.0);
                                                                }
                                                        }
                                                }
                                        });
                }

                // Shrink glow
                float finalG = T + 0.4f;
                Vector3f finalGlowCenter = rotation.transform(new Vector3f(-finalG * 0.5f, 0, -finalG * 0.5f),
                                new Vector3f());
                Vector3f finalGlowPos = new Vector3f(finalGlowCenter).add(new Vector3f(dir).mul(distance));

                glow.addKeyframeS(finalGlowPos, null, new Vector3f(G, 0, G), 15);
        }

        // HELPER: Calculates the local translation needed to center a block of 'thick'
        // size
        public static Vector3f center(float thick, float yOffset) {
                return new Vector3f(-thick * 0.5f, yOffset, -thick * 0.5f);
        }

        public static int packColor(Vector3f color) {

                int r = (int) (color.x * 255);

                int g = (int) (color.y * 255);

                int b = (int) (color.z * 255);

                return (r << 16) | (g << 8) | b;

        }

        public static void applyScreenShake(Level level, Vector3f impactPos, float intensity, float radius) {
                for (Player p : level.players()) {
                        double dist = p.position().distanceTo(new Vec3(impactPos.x, impactPos.y, impactPos.z));

                        if (dist < radius) {
                                // Shake intensity scales down with distance
                                float falloff = 1.0f - (float) (dist / radius);
                                float currentPower = intensity * falloff;

                                // Random jitter on X and Z
                                float shakeX = (float) (Math.random() - 0.5) * currentPower;
                                float shakeZ = (float) (Math.random() - 0.5) * currentPower;

                                p.push(shakeX, 0, shakeZ);
                                p.hurtMarked = true; // Forces the client to update velocity immediately
                        }
                }
        }
}
