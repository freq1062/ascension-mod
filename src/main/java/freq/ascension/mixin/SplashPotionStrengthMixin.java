package freq.ascension.mixin;

import java.util.List;
import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import freq.ascension.Ascension;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.AbstractThrownPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.phys.HitResult;

@Mixin(AbstractThrownPotion.class)
public abstract class SplashPotionStrengthMixin {

    @Inject(method = "onHit", at = @At("HEAD"))
    private void ascension$downgradeStrengthPotions(HitResult hitResult, CallbackInfo ci) {
        AbstractThrownPotion thrownPotion = (AbstractThrownPotion) (Object) this;
        ItemStack stack = thrownPotion.getItem();
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);

        if (contents == null || contents.equals(PotionContents.EMPTY)) {
            return;
        }

        Optional<Holder<Potion>> downgradedPotion = contents.potion();
        boolean downgradedBasePotion = downgradedPotion.filter(Potions.STRONG_STRENGTH::equals).isPresent();
        if (downgradedBasePotion) {
            downgradedPotion = Optional.of(Potions.STRENGTH);
        }

        List<MobEffectInstance> originalCustomEffects = contents.customEffects();
        List<MobEffectInstance> downgradedCustomEffects = originalCustomEffects.stream()
                .map(SplashPotionStrengthMixin::ascension$downgradeStrengthEffect)
                .toList();

        if (!downgradedBasePotion && downgradedCustomEffects.equals(originalCustomEffects)) {
            return;
        }

        stack.set(DataComponents.POTION_CONTENTS, new PotionContents(
                downgradedPotion,
                contents.customColor(),
                downgradedCustomEffects,
                contents.customName()));

        String potionType = thrownPotion.getType() == EntityType.LINGERING_POTION ? "Lingering potion" : "Splash potion";
        Ascension.LOGGER.info("[PotionHook] {} downgraded Strength II to Strength I", potionType);
    }

    private static MobEffectInstance ascension$downgradeStrengthEffect(MobEffectInstance effectInstance) {
        if (effectInstance.getEffect() != MobEffects.STRENGTH || effectInstance.getAmplifier() != 1) {
            return effectInstance;
        }

        return new MobEffectInstance(
                effectInstance.getEffect(),
                effectInstance.getDuration(),
                0,
                effectInstance.isAmbient(),
                effectInstance.isVisible(),
                effectInstance.showIcon());
    }
}
