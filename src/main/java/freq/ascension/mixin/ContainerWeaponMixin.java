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
 * into another container (chest, hopper, ender chest, etc.) via any inventory click.
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
        boolean isPlayerInventory = menu instanceof net.minecraft.world.inventory.InventoryMenu;

        // Block if the clicked slot contains a mythical weapon in a non-player container
        if (slotId >= 0 && slotId < menu.slots.size()) {
            ItemStack clicked = menu.slots.get(slotId).getItem();
            if (WeaponRegistry.isMythicalWeapon(clicked)) {
                if (!isPlayerInventory) {
                    // In any external container (chest, etc.) block ALL interactions with the weapon slot
                    sp.sendSystemMessage(Component.literal("§cYou cannot move your mythical weapon with a container open!"));
                    ci.cancel();
                    return;
                }
                // In own inventory: block shift-click that would move it elsewhere
                if (clickType == ClickType.QUICK_MOVE) {
                    sp.sendSystemMessage(Component.literal("§cYou cannot move your mythical weapon with a container open!"));
                    ci.cancel();
                    return;
                }
            }
        }

        // Block placing a cursor-held weapon into any slot in a non-player container
        ItemStack cursor = ((AbstractContainerMenu) (Object) this).getCarried();
        if (WeaponRegistry.isMythicalWeapon(cursor)) {
            if (!isPlayerInventory) {
                sp.sendSystemMessage(Component.literal("§cYou cannot move your mythical weapon with a container open!"));
                ci.cancel();
                return;
            }
        }

        // Block THROW (drop outside window, slotId == -999 with PICKUP click type)
        if (slotId == -999 && clickType == ClickType.PICKUP) {
            if (WeaponRegistry.isMythicalWeapon(((AbstractContainerMenu) (Object) this).getCarried())) {
                ci.cancel();
                return;
            }
        }

        // Block QUICK_CRAFT (drag-split across slots) — covers dragging a weapon into a chest
        if (clickType == ClickType.QUICK_CRAFT && !isPlayerInventory) {
            if (WeaponRegistry.isMythicalWeapon(((AbstractContainerMenu) (Object) this).getCarried())) {
                sp.sendSystemMessage(Component.literal("§cYou cannot move your mythical weapon with a container open!"));
                ci.cancel();
                return;
            }
        }

        // Block SWAP via hotbar number keys — prevents using 1–9 hotkeys to swap weapon into chest slots
        if (clickType == ClickType.SWAP && !isPlayerInventory) {
            int hotbarSlot = button; // for SWAP, button is the hotbar slot index (0–8)
            if (hotbarSlot >= 0 && hotbarSlot <= 8) {
                ItemStack hotbarItem = sp.getInventory().getItem(hotbarSlot);
                if (WeaponRegistry.isMythicalWeapon(hotbarItem)) {
                    ci.cancel();
                    return;
                }
            }
        }
    }
}
