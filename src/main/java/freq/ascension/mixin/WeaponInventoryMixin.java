package freq.ascension.mixin;

import freq.ascension.registry.WeaponRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Prevents mythical weapons from being dropped (Q key) by non-admins.
 * No Fabric API event exists for item drop; this Mixin is the minimal-footprint approach.
 */
@Mixin(ServerPlayer.class)
public class WeaponInventoryMixin {

    @Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At("HEAD"), cancellable = true)
    private void ascension$preventWeaponDrop(ItemStack stack, boolean throwRandomly, boolean retainOwnership,
            CallbackInfoReturnable<ItemEntity> cir) {
        if (stack == null || stack.isEmpty()) return;
        if (!WeaponRegistry.isMythicalWeapon(stack)) return;
        ServerPlayer player = (ServerPlayer) (Object) this;
        // Allow admins (permission level 2+) to drop
        if (player.hasPermissions(2)) return;
        player.sendSystemMessage(Component.literal("§cYou cannot drop your mythical weapon!"));
        cir.setReturnValue(null);
    }
}
