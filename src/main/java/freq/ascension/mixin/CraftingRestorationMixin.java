package freq.ascension.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import freq.ascension.items.InfluenceItem;
import freq.ascension.managers.AscensionData;

import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.gen.Accessor;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Unique;

@Mixin(AbstractContainerMenu.class)
public abstract class CraftingRestorationMixin {

    @Mixin(CraftingMenu.class)
    public interface CraftingMenuAccessor {
        @Accessor("craftSlots")
        CraftingContainer getRepairInventory();
    }

    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void onCraftingClick(int slotIndex, int button, ClickType clickType, Player player, CallbackInfo ci) {
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;

        // 1. Only process if it's a CraftingMenu and the Result Slot (Slot 0)
        if (!(menu instanceof CraftingMenu craftingMenu))
            return;
        if (slotIndex != 0)
            return;

        // 2. Get the result item
        Slot resultSlot = menu.getSlot(0);
        ItemStack resultStack = resultSlot.getItem();

        // 3. Verify it's our Influence Item
        if (!InfluenceItem.isInfluenceItem(resultStack))
            return;

        // 4. Server-side only logic
        if (player instanceof ServerPlayer serverPlayer) {
            AscensionData data = (AscensionData) serverPlayer;
            int currentInfluence = data.getInfluence();

            // Eligibility check
            if (currentInfluence >= 0) {
                serverPlayer.sendSystemMessage(
                        Component.literal("Your Influence is not negative.").withStyle(ChatFormatting.RED));
                ci.cancel(); // Stop the click entirely
                return;
            }

            // Calculate how many we can craft
            CraftingContainer grid = ((CraftingMenuAccessor) craftingMenu).getRepairInventory(); // Access via Accessor
            int maxPossible = calculateMaxCrafts(grid);
            if (maxPossible <= 0)
                return;

            // Handle Shift Click vs Single Click
            int needed = -currentInfluence;
            int toRestore = (clickType == ClickType.QUICK_MOVE) ? Math.min(maxPossible, needed) : 1;

            // 5. Execute Restoration
            consumeIngredients(grid, toRestore);
            data.addInfluence(toRestore);

            serverPlayer.sendSystemMessage(Component.literal("Your Influence has been restored by " + toRestore + "!")
                    .withStyle(ChatFormatting.GOLD));

            // 6. Update the menu and CANCEL the standard item pickup
            menu.broadcastChanges();
            ci.cancel();
        }
    }

    @Unique
    private int calculateMaxCrafts(CraftingContainer inv) {
        // Implementation of your maxCraftsPossible logic using Fabric slots
        // Slot mapping in CraftingContainer: 0-8 (top-left to bottom-right)
        // Your ECHO_SLOT 1, GOLD 3/5, TOTEM 4, AMETHYST 7 matches exactly.
        int echo = inv.getItem(1).getCount();
        int gold = inv.getItem(3).getCount() + inv.getItem(5).getCount();
        int totem = inv.getItem(4).getCount();
        int amethyst = inv.getItem(7).getCount();

        return Math.min(Math.min(gold / 2, totem), Math.min(echo, amethyst));
    }

    @Unique
    private void consumeIngredients(CraftingContainer inv, int amount) {
        inv.removeItem(1, amount); // Echo
        inv.removeItem(4, amount); // Totem
        inv.removeItem(7, amount); // Amethyst

        // Split gold consumption
        int remainingGold = amount * 2;
        remainingGold = takeFromSlot(inv, 3, remainingGold);
        takeFromSlot(inv, 5, remainingGold);
    }

    @Unique
    private int takeFromSlot(CraftingContainer inv, int slot, int amount) {
        ItemStack stack = inv.getItem(slot);
        int canTake = Math.min(stack.getCount(), amount);
        inv.removeItem(slot, canTake);
        return amount - canTake;
    }
}