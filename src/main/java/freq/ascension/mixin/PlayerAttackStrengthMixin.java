package freq.ascension.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import freq.ascension.weapons.RuinousScythe;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Captures the attack strength scale at the earliest possible point — the HEAD of
 * {@code Player.attack(Entity)} — before vanilla resets the attack ticker.
 * This value is stored in {@link RuinousScythe#CAPTURED_ATTACK_STRENGTH} and consumed
 * by {@link RuinousScythe#onAttack} to reliably detect weak/spam hits.
 *
 * <p>A Mixin is necessary here because no Fabric API hook fires before
 * {@code Player.attack()} internally resets the attack strength ticker.
 */
@Mixin(Player.class)
public abstract class PlayerAttackStrengthMixin {

    @Inject(method = "attack", at = @At("HEAD"))
    private void ascension$captureAttackStrength(Entity target, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer sp) {
            float scale = sp.getAttackStrengthScale(0.5f);
            RuinousScythe.CAPTURED_ATTACK_STRENGTH.put(sp.getUUID(), scale);
        }
    }
}
