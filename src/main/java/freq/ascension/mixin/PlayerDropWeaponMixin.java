package freq.ascension.mixin;

import freq.ascension.Ascension;
import freq.ascension.registry.WeaponRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Intercepts the 2-argument {@code Player.drop(ItemStack, boolean)} overload, which is the
 * upstream entry-point for the Q-key drop flow on dedicated servers in Minecraft 1.21.10.
 *
 * <p>The 3-argument {@code ServerPlayer.drop(ItemStack, boolean, boolean)} overload is also
 * covered by {@link WeaponInventoryMixin}, but the packet handling on dedicated servers can
 * invoke the 2-argument variant on {@link Player} directly before delegation occurs.
 * This mixin closes that gap with a minimal additional injection.
 *
 * <p>Rationale for Mixin: no Fabric API event exists for per-item drop cancellation.
 */
@Mixin(Player.class)
public class PlayerDropWeaponMixin {

    @Inject(
            method = "drop(Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At("HEAD"),
            cancellable = true)
    private void ascension$preventWeaponDrop2arg(ItemStack stack, boolean throwRandomly,
            CallbackInfoReturnable<ItemEntity> cir) {
        if (stack == null || stack.isEmpty()) return;
        if (!WeaponRegistry.isMythicalWeapon(stack)) return;
        if (!(((Object) this) instanceof ServerPlayer sp)) return;
        Ascension.LOGGER.info("[WeaponDrop-2arg] called for {} item={} hasPermission2={}",
                sp.getName().getString(), stack.getDisplayName().getString(), sp.hasPermissions(2));
        if (sp.hasPermissions(2)) return;
        sp.sendSystemMessage(Component.literal("§cYou cannot drop your mythical weapon!"));
        cir.setReturnValue(null);
    }
}
