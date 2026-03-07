package freq.ascension.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.world.entity.Entity;

/**
 * Exposes the protected {@code setSharedFlag(int, boolean)} method on {@link Entity}
 * so that non-subclass Mixin injectors can set entity data flags (e.g. isFallFlying).
 */
@Mixin(Entity.class)
public interface EntitySharedFlagInvoker {

    @Invoker("setSharedFlag")
    void invokeSetSharedFlag(int flag, boolean value);
}
