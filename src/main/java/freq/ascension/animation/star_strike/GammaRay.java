package freq.ascension.animation.star_strike;

import java.util.concurrent.ThreadLocalRandom;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.math.Transformation;

import freq.ascension.Ascension;
import freq.ascension.api.Task;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Display.BlockDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class GammaRay {

    private static final double BASE_HEIGHT = 50.0;
    private static final double MAX_TILT_DEGREES = 30.0;
    private static final double MAX_BEAM_LENGTH = 64.0;
    private static final int DEBRIS_MIN = 6;
    private static final int DEBRIS_MAX = 10;

    private GammaRay() {
    }

    public static void strike(Level level, Vec3 target, double thickness, int growTicks, int holdTicks, int fadeTicks) {
        strike(level, target, thickness, growTicks, holdTicks, fadeTicks, (Vector3f) null);
    }

    public static void strike(Level level, Vec3 target, double thickness, int growTicks, int holdTicks, int fadeTicks,
            Vector3f haloGlowColor) {
        if (level == null || target == null)
            return;

        double safeThickness = Math.max(0.1, thickness);
        int grow = Math.max(1, growTicks);
        int hold = Math.max(0, holdTicks);
        int fade = Math.max(1, fadeTicks);

        Vec3 start = pickOrigin(target, level);
        Vec3 direction = target.subtract(start);
        double length = direction.length();
        if (length < 0.5)
            return;
        direction = direction.normalize();

        if (length > MAX_BEAM_LENGTH) {
            start = target.subtract(direction.scale(MAX_BEAM_LENGTH));
            direction = target.subtract(start).normalize();
            length = target.subtract(start).length();
        }

        new BeamSegment(level, start, direction, length, safeThickness, Blocks.SEA_LANTERN.defaultBlockState(), false,
                null,
                grow, hold, fade);
        new BeamSegment(level, start, direction, length, safeThickness * 1.35,
                Blocks.WHITE_STAINED_GLASS.defaultBlockState(), true, haloGlowColor, grow, hold, fade);

        scheduleGroundBlast(level, target, safeThickness, grow);
    }

    private static Vec3 pickOrigin(Vec3 target, Level level) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double angle = Math.toRadians(random.nextDouble(0, MAX_TILT_DEGREES));
        double horizontal = Math.sin(angle) * BASE_HEIGHT;
        double vertical = Math.cos(angle) * BASE_HEIGHT;
        double yaw = random.nextDouble(Math.PI * 2.0);

        double x = Math.cos(yaw) * horizontal;
        double z = Math.sin(yaw) * horizontal;
        Vec3 start = new Vec3(target.x + x, target.y + vertical, target.z + z);

        int maxHeight = Math.max(1, level.getMaxY() - 2);
        if (start.y > maxHeight) {
            start = new Vec3(start.x, maxHeight, start.z);
        }
        if (start.y <= target.y) {
            start = new Vec3(start.x, target.y + 10, start.z);
        }
        return start;
    }

    private static void spawnGroundBlast(Level level, Vec3 target, double thickness) {
        level.addParticle(ParticleTypes.END_ROD, target.x, target.y, target.z, 0, 0, 0);
        for (int i = 0; i < 25; i++) {
            level.addParticle(ParticleTypes.END_ROD, target.x, target.y, target.z, (randomDouble() - 0.5) * thickness,
                    0.4 * randomDouble(), (randomDouble() - 0.5) * thickness);
        }
        for (int i = 0; i < 20; i++) {
            level.addParticle(ParticleTypes.CRIT, target.x, target.y, target.z,
                    (randomDouble() - 0.5) * thickness * 0.5,
                    0.2 * randomDouble(), (randomDouble() - 0.5) * thickness * 0.5);
        }

        level.playSound(null, target.x, target.y, target.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.6f,
                1.4f);
        level.playSound(null, target.x, target.y, target.z, SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0f, 0.5f);
        level.playSound(null, target.x, target.y, target.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS,
                1.0f,
                1.0f);
        level.playSound(null, target.x, target.y, target.z, SoundEvents.TRIDENT_THUNDER, SoundSource.PLAYERS, 1.0f,
                1.0f);

        ThreadLocalRandom random = ThreadLocalRandom.current();
        int shards = random.nextInt(DEBRIS_MIN, DEBRIS_MAX + 1);
        for (int i = 0; i < shards; i++) {
            double yaw = random.nextDouble(Math.PI * 2.0);
            double speed = 0.2 + random.nextDouble() * 0.25;
            Vec3 velocity = new Vec3(Math.cos(yaw) * speed, 0.35 + random.nextDouble() * 0.25,
                    Math.sin(yaw) * speed);
            BlockState material = (i % 2 == 0) ? Blocks.CALCITE.defaultBlockState()
                    : Blocks.QUARTZ_BLOCK.defaultBlockState();
            new GroundShard(level, target, velocity, material, 0.25 + random.nextDouble() * 0.15);
        }
    }

    private static void scheduleGroundBlast(Level level, Vec3 target, double thickness, int delayTicks) {
        if (delayTicks <= 0) {
            spawnGroundBlast(level, target, thickness);
            return;
        }

        Ascension.scheduler.schedule(new Task() {
            private int ticks = delayTicks;

            @Override
            public boolean shouldRun(long currentTick) {
                return ticks >= 0;
            }

            @Override
            public void run() {
                ticks--;
                if (ticks <= 0) {
                    spawnGroundBlast(level, target, thickness);
                    ticks = -1; // finish
                }
            }

            @Override
            public boolean isFinished() {
                return ticks < 0;
            }
        });
    }

    private static double randomDouble() {
        return ThreadLocalRandom.current().nextDouble();
    }

    private static final class BeamSegment implements Task {
        private final Level level;
        private final Vec3 anchor;
        private final double length;
        private final double thickness;
        private final boolean halo;
        private final float yaw;
        private final float pitch;
        private final Quaternionf orientation;
        private final int growTicks;
        private final int holdTicks;
        private final int fadeTicks;
        private final int totalTicks;
        private final double pulseOffset;
        private int age;
        private final BlockDisplay entity;
        private boolean finished = false;

        private BeamSegment(Level level, Vec3 start, Vec3 direction, double length, double thickness,
                BlockState material, boolean halo, Vector3f haloGlowColor, int growTicks, int holdTicks,
                int fadeTicks) {
            this.level = level;
            this.anchor = start;
            this.length = length;
            this.thickness = thickness;
            this.halo = halo;

                double dx = direction.x;
                double dy = direction.y;
                double dz = direction.z;
                this.yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                this.pitch = (float) Math.toDegrees(Math.atan2(-dy, Math.sqrt(dx * dx + dz * dz)));
                this.orientation = new Quaternionf().rotateY((float) Math.toRadians(this.yaw))
                    .rotateX((float) Math.toRadians(this.pitch));

            this.growTicks = Math.max(1, growTicks);
            this.holdTicks = Math.max(0, holdTicks);
            this.fadeTicks = Math.max(1, fadeTicks);
            this.totalTicks = this.growTicks + this.holdTicks + this.fadeTicks;
            this.pulseOffset = ThreadLocalRandom.current().nextDouble(Math.PI * 2.0);
            this.age = 0;

            this.entity = EntityType.BLOCK_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
            if (this.entity == null) {
                this.finished = true;
                return;
            }

            entity.setBlockState(material);
            entity.setPos(start);
            entity.setBrightnessOverride(Brightness.FULL_BRIGHT);
            entity.setTransformationInterpolationDuration(1);
            entity.setPosRotInterpolationDuration(1);
            entity.setTransformationInterpolationDelay(0);
            entity.setViewRange(64f);
            level.addFreshEntity(entity);

            Ascension.scheduler.schedule(this);
        }

        @Override
        public boolean shouldRun(long currentTick) {
            return !finished;
        }

        @Override
        public void run() {
            age++;
            if (entity == null || level == null) {
                finished = true;
                return;
            }
            if (age >= totalTicks) {
                entity.discard();
                finished = true;
                return;
            }

            int clampedTick = Math.max(0, Math.min(age, totalTicks));

            double currentLength;
            boolean fading;
            if (clampedTick <= growTicks) {
                double progress = (double) clampedTick / growTicks;
                currentLength = Math.max(0.2, length * progress);
                fading = false;
            } else if (clampedTick <= growTicks + holdTicks) {
                currentLength = length;
                fading = false;
            } else {
                double fadeTick = clampedTick - growTicks - holdTicks;
                double fadeProgress = Math.min(1.0, fadeTick / (double) fadeTicks);
                currentLength = Math.max(0.2, length * (1.0 - fadeProgress));
                fading = true;
            }

            double widthFactor;
            if (clampedTick <= growTicks) {
                widthFactor = 0.35 + 0.65 * ((double) clampedTick / growTicks);
            } else if (clampedTick <= growTicks + holdTicks) {
                widthFactor = 1.0;
            } else {
                double fadeTick = clampedTick - growTicks - holdTicks;
                widthFactor = Math.max(0.15, 1.0 - 0.85 * (fadeTick / (double) fadeTicks));
            }

            double width = thickness * widthFactor;
            if (halo) {
                width *= 1.15;
            } else {
                if (!fading) {
                    double pulse = 0.85 + 0.15 * Math.sin((clampedTick + pulseOffset) * 0.4);
                    width *= pulse;
                }
            }

            width = Math.max(0.06, width);

            double shiftZ = fading ? (length - currentLength) : 0.0;
            double halfW = width * 0.5;
            Vector3f translation = new Vector3f((float) -halfW, (float) -halfW, (float) shiftZ);
            Vector3f scale = new Vector3f((float) width, (float) width, (float) Math.max(0.2, currentLength));
            entity.setTransformation(new Transformation(translation, orientation, scale, new Quaternionf()));
            entity.teleportTo(anchor.x, anchor.y, anchor.z);
        }

        @Override
        public boolean isFinished() {
            return finished;
        }
    }

    private static final class GroundShard implements Task {
        private static final double GRAVITY = 0.05;

        private final Level level;
        private Vec3 location;
        private Vec3 velocity;
        private final Vector3f baseScale;
        private final int lifetime;
        private int age;
        private final BlockDisplay entity;
        private boolean finished = false;

        private GroundShard(Level level, Vec3 start, Vec3 velocity, BlockState material, double baseSize) {
            this.level = level;
            this.location = start;
            this.velocity = velocity;
            this.baseScale = new Vector3f((float) baseSize, (float) (baseSize * 0.8), (float) baseSize);
            this.lifetime = 12 + ThreadLocalRandom.current().nextInt(8);

            this.entity = EntityType.BLOCK_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
            if (this.entity == null) {
                this.finished = true;
                return;
            }

            entity.setBlockState(material);
            entity.setPos(location);
            entity.setBrightnessOverride(new Brightness(12, 12));
            entity.setTransformationInterpolationDuration(1);
            entity.setViewRange(32f);
            level.addFreshEntity(entity);

            Ascension.scheduler.schedule(this);
        }

        @Override
        public boolean shouldRun(long currentTick) {
            return !finished;
        }

        @Override
        public void run() {
            age++;
            if (entity == null || level == null) {
                finished = true;
                return;
            }
            if (age > lifetime) {
                entity.discard();
                finished = true;
                return;
            }

            velocity = velocity.scale(0.92);
            velocity = velocity.add(0, -GRAVITY, 0);
            location = location.add(velocity);

            if (location.y <= level.getMinY()) {
                entity.discard();
                finished = true;
                return;
            }

            float fade = Math.max(0f, 1f - (float) age / lifetime);
            entity.teleportTo(location.x, location.y, location.z);
            Vector3f scale = new Vector3f(baseScale).mul(Math.max(0.2f, fade));
            entity.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), scale, new Quaternionf()));
        }

        @Override
        public boolean isFinished() {
            return finished;
        }
    }
}
