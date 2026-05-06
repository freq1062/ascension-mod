package freq.ascension.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.block.DropExperienceBlock;

// In your mixins package
@Mixin(DropExperienceBlock.class)
public interface DropExperienceBlockMixin {
    @Accessor("xpRange")
    IntProvider getXpRange();
}