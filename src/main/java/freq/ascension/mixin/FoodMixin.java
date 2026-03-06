package freq.ascension.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import freq.ascension.managers.AbilityManager;
import freq.ascension.util.FoodDataContext;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

@Mixin(LivingEntity.class)
public abstract class FoodMixin {

    // completeUsingItem() is called when a player finishes using an item (e.g.
    // eating).
    // We store the player context here so FoodDataMixin can modify saturation.
    @Inject(method = "completeUsingItem", at = @At("HEAD"))
    private void beforeCompleteUsingItem(CallbackInfo ci) {
        if (AbilityManager.shouldSkipModification()) {
            return;
        }

        LivingEntity entity = (LivingEntity) (Object) this;

        if (!(entity instanceof ServerPlayer serverPlayer)) {
            return;
        }

        // Only set player context if the item being used is food
        if (entity.getUseItem().has(DataComponents.FOOD)) {
            FoodDataContext.setCurrentPlayer(serverPlayer);
        }
    }
}
