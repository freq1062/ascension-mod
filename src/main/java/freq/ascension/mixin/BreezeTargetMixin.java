package freq.ascension.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import freq.ascension.managers.AbilityManager;

@Mixin(Breeze.class)
public abstract class BreezeTargetMixin {
    @Inject(method = "customServerAiStep", at = @At("HEAD"))
    private void onCustomServerAiStep(net.minecraft.server.level.ServerLevel serverLevel, CallbackInfo ci) {
        Breeze breeze = (Breeze) (Object) this;
        Brain<?> brain = breeze.getBrain();
        LivingEntity target = (LivingEntity) brain.getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
        if (target instanceof ServerPlayer player) {
            if (AbilityManager.anyMatch(player, (order) -> order.isIgnoredBy(player, breeze))) {
                brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);
            }
        }
    }
}
