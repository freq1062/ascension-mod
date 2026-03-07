package freq.ascension.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import freq.ascension.managers.AbilityManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.PowderSnowBlock;
import net.minecraft.world.level.block.state.BlockState;

@Mixin(PowderSnowBlock.class)
public class PowderSnowMixin {
    /**
     * Redirect the getItemBySlot(FEET) call inside canEntityWalkOnPowderSnow so
     * that Ocean passive players are treated as wearing leather boots. This runs
     * server-side within the static collision check; the server then corrects the
     * client's position onto the snow surface even without a client-side mixin.
     */
    @Redirect(
        method = "canEntityWalkOnPowderSnow",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;getItemBySlot(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;"))
    private static ItemStack ocean$redirectGetFeet(LivingEntity entity, EquipmentSlot slot) {
        ItemStack original = entity.getItemBySlot(slot);
        if (slot == EquipmentSlot.FEET && entity instanceof ServerPlayer player) {
            if (AbilityManager.anyMatch(player, order -> order.canWalkOnPowderSnow(player))) {
                return new ItemStack(Items.LEATHER_BOOTS);
            }
        }
        return original;
    }

    /**
     * Prevent Ocean players from being stuck/slowed by powder snow blocks.
     * This ensures they can move through powder snow as if it's not there when
     * walking on top.
     */
    @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
    private void ocean$preventPowderSnowSlow(BlockState state, net.minecraft.world.level.Level level,
            net.minecraft.core.BlockPos pos, Entity entity,
            net.minecraft.world.entity.InsideBlockEffectApplier effectApplier, boolean hasPowderSnowBoots,
            CallbackInfo ci) {
        if (entity instanceof ServerPlayer player) {
            if (AbilityManager.anyMatch(player, (order) -> order.canWalkOnPowderSnow(player))) {
                // Cancel the entityInside logic which makes entities sink/freeze
                ci.cancel();
            }
        }
    }
}