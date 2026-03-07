package freq.ascension.mixin;

import freq.ascension.managers.ChallengerTrialManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Caps incoming damage for a god player during an active Challenger Trial so that their HP cannot
 * drop below 50 % of their maximum health in a single hit.
 *
 * <p>Targets {@link LivingEntity#hurtServer} with an {@code instanceof ServerPlayer} guard so
 * the protection only applies to player entities. Using {@code LivingEntity} as the Mixin target
 * ensures the injection runs in the same class as {@link DamageMixin}, so ability-modified
 * damage (from {@link DamageMixin}) is processed before this cap is applied.
 *
 * <p>No Fabric API alternative exists for per-entity damage modification at this level.
 */
@Mixin(LivingEntity.class)
public abstract class GodProtectionMixin {

    /**
     * Reduces the damage parameter so the god cannot be brought below 50 % HP in one hit while
     * an active trial is running at their POI.
     */
    @ModifyVariable(method = "hurtServer", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private float ascension$capGodTrialDamage(float amount,
            ServerLevel level, DamageSource source) {
        if (!((Object) this instanceof ServerPlayer player)) return amount;
        if (!(player.level() instanceof ServerLevel playerLevel)) return amount;
        MinecraftServer server = playerLevel.getServer();

        ChallengerTrialManager ctm = ChallengerTrialManager.get();
        if (!ctm.isActiveTrialInRadius(player, server)) return amount;

        float maxHp = player.getMaxHealth();
        float currentHp = player.getHealth();
        // Only protect if currently above 50 % max HP
        if (currentHp <= maxHp * 0.5f) return amount;

        float maxAllowedDamage = currentHp - (maxHp * 0.5f) - 0.01f;
        return Math.min(amount, maxAllowedDamage);
    }
}
