package freq.ascension.mixin;

import freq.ascension.registry.WeaponRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevents mythical weapons from being moved out of the player's inventory
 * into another container (chest, hopper, ender chest, etc.) via inventory click.
 *
 * No Fabric API event exists for per-slot inventory manipulation; this Mixin targets
 * AbstractContainerMenu.doClick which is the unified click handler.
 */
@Mixin(AbstractContainerMenu.class)
public class ContainerWeaponMixin {

    @Inject(method = "doClick", at = @At("HEAD"), cancellable = true)
    private void ascension$preventWeaponMove(int slotId, int button, ClickType clickType,
            net.minecraft.world.entity.player.Player player, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer sp)) return;
        if (sp.hasPermissions(2)) return;

        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;

        // Check the slot being clicked
        if (slotId >= 0 && slotId < menu.slots.size()) {
            ItemStack clicked = menu.slots.get(slotId).getItem();
            if (WeaponRegistry.isMythicalWeapon(clicked)) {
                if (clickType == ClickType.QUICK_MOVE) {
                    // Shift-click: block moving weapon out of player inventory
                    if (!(menu instanceof net.minecraft.world.inventory.InventoryMenu)) {
                        sp.sendSystemMessage(Component.literal("§cYou cannot move your mythical weapon!"));
                        ci.cancel();
                        return;
                    }
                }
            }
        }

        // Also check the cursor item being placed into a non-player inventory
        ItemStack cursor = player.containerMenu.getCarried();
        if (WeaponRegistry.isMythicalWeapon(cursor) && slotId >= 0 && slotId < menu.slots.size()) {
            if (!(menu instanceof net.minecraft.world.inventory.InventoryMenu)) {
                sp.sendSystemMessage(Component.literal("§cYou cannot move your mythical weapon!"));
                ci.cancel();
            }
        }
    }
}
