package freq.ascension.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import freq.ascension.managers.AbilityManager;
import freq.ascension.weapons.MythicWeapon;
import freq.ascension.weapons.PrismWand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Intercepts {@code BowItem.releaseUsing} on the server side.
 *
 * <p>When a player who holds the Prism Wand releases a fully-charged bow shot (≥ 20 ticks
 * of draw time), this mixin sets {@link PrismWand#PENDING_AIMBOT} to that player.  The
 * vanilla method continues normally and spawns an {@code Arrow} entity; the
 * {@link PrismWand#register()} hook (via {@code ServerEntityEvents.ENTITY_LOAD}) intercepts
 * that arrow, discards it, and triggers the aimbot logic instead.
 *
 * <p>Why not cancel at HEAD? Cancelling {@code releaseUsing} would suppress arrow-inventory
 * consumption and bow-use statistics. Letting vanilla run and discarding the spawned entity
 * is less invasive and preserves all normal side-effects (arrow count, stats, sounds).
 *
 * <p><b>Mixin rationale:</b> no Fabric event fires at the moment a bow shot is released;
 * {@code UseItemCallback} fires on the start of item use, not on release.
 * {@code ServerEntityEvents.ENTITY_LOAD} alone cannot determine draw time.
 * The combination of this mixin (draw-time gate) and the ENTITY_LOAD hook (intercept) is
 * the minimal-footprint approach.
 */
@Mixin(BowItem.class)
public class BowReleaseMixin {

    /**
     * Called at the very start of {@code BowItem.releaseUsing}.
     * If the entity is a server-side player carrying the Prism Wand and the draw time
     * is ≥ 20 ticks (full charge / crit arrow threshold), arms the aimbot flag.
     */
    @Inject(method = "releaseUsing", at = @At("HEAD"))
    private void ascension$onBowRelease(ItemStack stack, Level level, LivingEntity entity,
            int timeCharged, CallbackInfo ci) {
        if (!(entity instanceof ServerPlayer player)) return;
        if (!(level instanceof ServerLevel)) return;

        MythicWeapon weapon = AbilityManager.getActiveWeapon(player);
        if (!(weapon instanceof PrismWand)) return;

        // timeCharged is the remaining use-duration ticks (BowItem.getUseDuration = 72000).
        // Draw ticks = 72000 − timeCharged.  Full charge threshold = 20 ticks.
        int drawTicks = 72000 - timeCharged;
        if (drawTicks < 20) return;

        // Arm the ENTITY_LOAD interception for the arrow that vanilla is about to spawn.
        PrismWand.PENDING_AIMBOT.set(player);
    }
}
