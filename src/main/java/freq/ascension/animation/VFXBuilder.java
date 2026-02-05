package freq.ascension.animation;

import java.util.LinkedList;
import java.util.Queue;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.math.Transformation;

import freq.ascension.Ascension;
import freq.ascension.api.Task;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Display.BlockDisplay;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class VFXBuilder implements Task {
    private final BlockDisplay entity;
    private final Queue<Keyframe> queue = new LinkedList<>();
    private int currentTicksRemaining = 0;
    private boolean finished = false;
    private Vector3f lastTranslation = new Vector3f(0);
    private Quaternionf lastLeftRotation = new Quaternionf();
    private Vector3f lastScale = new Vector3f(1);
    private Quaternionf lastRightRotation = new Quaternionf();

    public record Keyframe(Transformation target, int duration, int delay, Runnable onStart) {
    }

    public VFXBuilder(Level level, Vector3f pos, BlockState block, Transformation initial) {
        this.entity = EntityType.BLOCK_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);

        // 1. Set the visual state BEFORE spawning
        entity.setBlockState(block);
        entity.setPos(pos.x, pos.y, pos.z);
        entity.setTransformation(initial);
        entity.setBrightnessOverride(Brightness.FULL_BRIGHT);

        // 2. Initialize sticky state
        this.lastTranslation = new Vector3f(initial.getTranslation());
        this.lastLeftRotation = new Quaternionf(initial.getLeftRotation());
        this.lastRightRotation = new Quaternionf(initial.getRightRotation());
        this.lastScale = new Vector3f(initial.getScale());

        this.currentTicksRemaining = 1;
        // 3. NOW add to level - the spawn packet will contain the 'initial' transform
        level.addFreshEntity(entity);

        Ascension.scheduler.schedule(this);
    }

    public BlockDisplay getEntity() {
        return this.entity;
    }

    // Helper to make an instant state
    public static Transformation instant(Vector3f t, Quaternionf r, Vector3f s) {
        return new Transformation(t, r, s, null);
    }

    public VFXBuilder withAction(Runnable action) {
        // This is a bit of a hack: we need to replace the last keyframe
        // in the queue with one that has the action.
        if (queue instanceof LinkedList<Keyframe> list && !list.isEmpty()) {
            Keyframe last = list.removeLast();
            list.add(new Keyframe(last.target(), last.duration(), last.delay(), action));
        }
        return this;
    }

    public VFXBuilder addKeyframe(Vector3f pos, Quaternionf rot, Vector3f scale, int duration, int delayTicks) {
        // If any component is null, fall back to the last set value to avoid NPEs
        Vector3f usePos = pos != null ? pos : lastTranslation;
        Quaternionf useRot = rot != null ? rot : lastLeftRotation;
        Vector3f useScale = scale != null ? scale : lastScale;

        Transformation combined = new Transformation(
                new Vector3f(usePos),
                new Quaternionf(useRot),
                new Vector3f(useScale),
                new Quaternionf(lastRightRotation));
        queue.add(new Keyframe(combined, duration, delayTicks, () -> {
        }));
        return this;
    }

    public VFXBuilder addKeyframe(Vector3f pos, Quaternionf rot, Vector3f scale, int duration) {
        return addKeyframe(pos, rot, scale, duration, 0);
    }

    // If null, uses last set values
    public VFXBuilder addKeyframeS(Vector3f pos, Quaternionf rot, Vector3f scale, int duration, int delayTicks) {
        // Only update the fields that aren't null
        if (pos != null)
            lastTranslation = pos;
        if (rot != null)
            lastLeftRotation = rot;
        if (scale != null)
            lastScale = scale;

        Transformation combined = new Transformation(
                new Vector3f(lastTranslation),
                new Quaternionf(lastLeftRotation),
                new Vector3f(lastScale),
                new Quaternionf(lastRightRotation));

        queue.add(new Keyframe(combined, duration, delayTicks, () -> {
        }));
        return this;
    }

    public VFXBuilder addKeyframeS(Vector3f pos, Quaternionf rot, Vector3f scale, int duration) {
        return addKeyframeS(pos, rot, scale, duration, 0);
    }

    @Override
    public void run() {
        if (currentTicksRemaining > 0) {
            currentTicksRemaining--;
            return;
        }

        if (queue.isEmpty()) {
            entity.discard();
            this.finished = true;
            return;
        }

        Keyframe next = queue.poll();

        // TRIGGER THE CALLBACK HERE
        if (next.onStart() != null) {
            next.onStart().run();
        }

        entity.setTransformationInterpolationDuration(next.duration());
        entity.setTransformationInterpolationDelay(next.delay());
        entity.setTransformation(next.target());

        currentTicksRemaining = next.duration() + next.delay();
    }

    @Override
    public boolean shouldRun(long currentTick) {
        return !this.finished;
    }

    @Override
    public boolean isFinished() {
        return this.finished;
    }
}