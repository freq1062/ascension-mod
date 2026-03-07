package freq.ascension.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import freq.ascension.managers.AbilityManager;
import freq.ascension.util.FoodDataContext;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

@Mixin(LivingEntity.class)
public abstract class FoodMixin {

    @Unique
    private ItemStack ascension$consumedItem = null;

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
            ascension$consumedItem = entity.getUseItem().copy();
        }
    }

    // After eating completes, broadcast to orders so they can react (e.g. FloraGod golden apple)
    @Inject(method = "completeUsingItem", at = @At("TAIL"))
    private void afterCompleteUsingItem(CallbackInfo ci) {
        LivingEntity entity = (LivingEntity) (Object) this;
        ItemStack eaten = ascension$consumedItem;
        ascension$consumedItem = null;

        if (eaten == null || eaten.isEmpty()) return;
        if (!(entity instanceof ServerPlayer serverPlayer)) return;

        AbilityManager.onItemEaten(serverPlayer, eaten);
    }
}
