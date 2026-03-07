package freq.ascension.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import freq.ascension.managers.AbilityManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.EnchantmentMenu;

@Mixin(EnchantmentMenu.class)
public abstract class EnchantmentMixin {

    @Unique
    private Player ascension$player;

    /**
     * The three enchantment slot costs backed by DataSlot.shared() wrappers —
     * modifying the array values directly will be picked up by broadcastChanges()
     * on the next server tick (no explicit call needed here).
     */
    @Shadow public int[] costs;

    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V", at = @At("RETURN"))
    private void ascension$capturePlayer(int syncId, Inventory inventory,
            net.minecraft.world.inventory.ContainerLevelAccess access, CallbackInfo ci) {
        this.ascension$player = inventory.player;
    }

    /**
     * After vanilla populates the three slot costs, apply each equipped order's
     * {@code modifyEnchantmentCost()} modifier. The DataSlot.shared() wrappers
     * read directly from the {@code costs[]} array, so mutations are picked up
     * automatically when {@code broadcastChanges()} runs on the next server tick.
     *
     * <p>Rationale: the old @ModifyVariable(ordinal=0) approach targeted a local
     * loop-counter variable rather than the actual slot cost, so the reduction was
     * never applied.
     */
    @Inject(method = "slotsChanged", at = @At("TAIL"))
    private void ascension$halveEnchantmentCosts(Container container, CallbackInfo ci) {
        if (!(this.ascension$player instanceof ServerPlayer serverPlayer)) return;

        for (int i = 0; i < this.costs.length; i++) {
            if (this.costs[i] > 0) {
                int original = this.costs[i];
                int[] ref = { original };
                AbilityManager.broadcast(serverPlayer, order -> {
                    ref[0] = order.modifyEnchantmentCost(ref[0]);
                });
                this.costs[i] = ref[0];
            }
        }
    }
}