package freq.ascension.animation.dash;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.math.Transformation;

import freq.ascension.managers.TickingEffect;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Display.BlockDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class SmokeShard implements TickingEffect {
    private final BlockDisplay entity;
    private final int lifetime;
    private int age = 0;

    public SmokeShard(Level level, Vec3 start, Vec3 velocity, int lifetime, BlockState block, Vector3f baseScale,
            Quaternionf rotation) {
        this.lifetime = lifetime;
        this.entity = EntityType.BLOCK_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
        if (entity == null)
            return;

        entity.setBlockState(block);
        entity.setPos(start);
        entity.setBrightnessOverride(Brightness.FULL_BRIGHT);

        // Initial state
        Vector3f startScale = new Vector3f(baseScale).mul(0.8f);
        Transformation startTransform = new Transformation(
                new Vector3f(0, 0, 0),
                rotation,
                startScale,
                new Quaternionf());

        entity.setTransformation(startTransform);

        // Client-side interpolation setup
        entity.setTransformationInterpolationDuration(lifetime);
        entity.setPosRotInterpolationDuration(lifetime);
        entity.setTransformationInterpolationDelay(0);

        level.addFreshEntity(entity);

        // Final state
        Vec3 endPos = start.add(velocity.scale(lifetime));
        Vector3f endScale = new Vector3f(baseScale).mul(1.2f);
        Transformation endTransform = new Transformation(
                new Vector3f(0, 0, 0),
                rotation,
                endScale,
                new Quaternionf());

        entity.setTransformation(endTransform);
        entity.teleportTo(endPos.x, endPos.y, endPos.z);
    }

    @Override
    public void tick() {
        age++;
    }

    @Override
    public boolean isExpired() {
        return age >= lifetime;
    }

    @Override
    public void discard() {
        entity.discard();
    }
}