package freq.ascension.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import freq.ascension.managers.AbilityManager;
import freq.ascension.weapons.HellfireCrossbow;
import freq.ascension.weapons.MythicWeapon;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * Intercepts {@link CrossbowItem#performShooting} to detect when a Nether god fires a firework
 * from the Hellfire Crossbow and routes the event to {@link HellfireCrossbow#onFireworkShot}.
 *
 * <p>A Mixin is used here because no Fabric API event fires specifically when a loaded crossbow
 * projectile is launched — {@code UseItemCallback} fires at use-start, and
 * {@code ServerEntityEvents.ENTITY_LOAD} does not cover freshly spawned entities.
 */
@Mixin(CrossbowItem.class)
public class CrossbowMixin {

    @Inject(method = "performShooting", at = @At("HEAD"))
    private void ascension$onPerformShooting(
            Level level,
            LivingEntity entity,
            InteractionHand hand,
            ItemStack crossbow,
            float velocity,
            float inaccuracy,
            LivingEntity target,
            CallbackInfo ci) {

        if (!(entity instanceof ServerPlayer player)) return;

        // Verify this is a Nether god carrying the Hellfire Crossbow.
        MythicWeapon weapon = AbilityManager.getActiveWeapon(player);
        if (!(weapon instanceof HellfireCrossbow hfc)) return;
        if (!hfc.isItem(crossbow)) return;

        // Only intercept if a firework is loaded (not plain arrows).
        ChargedProjectiles charged = crossbow.get(DataComponents.CHARGED_PROJECTILES);
        if (charged == null || !charged.contains(Items.FIREWORK_ROCKET)) return;

        // The beam fires as a BONUS alongside the vanilla firework — never cancel the shot.
        hfc.onFireworkShot(player);
    }
}
