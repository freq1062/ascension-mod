package freq.ascension.test.misc;

import freq.ascension.commands.SetInfluenceCommand;
import freq.ascension.items.ChallengerSigil;
import freq.ascension.managers.AscensionData;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;

/**
 * Unit-level tests for {@link SetInfluenceCommand} logic and integration with
 * {@link AscensionData#setInfluence(int)}.
 */
public class SetInfluenceCommandTests {

    // ─────────────────────────────────────────────────────────────────────────
    // ChallengerSigil identity (co-located here as a recipe-adjacent concern)
    // ─────────────────────────────────────────────────────────────────────────

    @GameTest
    public void challengerSigilIsSigilReturnsTrue(GameTestHelper helper) {
        ItemStack sigil = ChallengerSigil.createSigil();
        if (!ChallengerSigil.isSigil(sigil)) {
            helper.fail("ChallengerSigil.createSigil() must be recognised by isSigil()");
        }
        helper.succeed();
    }

    @GameTest
    public void challengerSigilPlainHeartIsNotSigil(GameTestHelper helper) {
        ItemStack plain = new ItemStack(net.minecraft.world.item.Items.HEART_OF_THE_SEA);
        if (ChallengerSigil.isSigil(plain)) {
            helper.fail("A plain heart_of_the_sea without CustomModelData must not be recognised as a sigil");
        }
        helper.succeed();
    }

    @GameTest
    public void challengerSigilIsNonStackable(GameTestHelper helper) {
        ItemStack sigil = ChallengerSigil.createSigil();
        if (sigil.getMaxStackSize() != 1) {
            helper.fail("Challenger's Sigil must have max stack size of 1, got: " + sigil.getMaxStackSize());
        }
        helper.succeed();
    }

    @GameTest
    public void challengerSigilHasEnchantmentGlint(GameTestHelper helper) {
        ItemStack sigil = ChallengerSigil.createSigil();
        Boolean glint = sigil.get(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
        if (!Boolean.TRUE.equals(glint)) {
            helper.fail("Challenger's Sigil must have ENCHANTMENT_GLINT_OVERRIDE set to true");
        }
        helper.succeed();
    }

    @GameTest
    public void challengerSigilHasCustomModelData(GameTestHelper helper) {
        ItemStack sigil = ChallengerSigil.createSigil();
        var cmd = sigil.get(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA);
        if (cmd == null || !cmd.strings().contains("challengers_sigil")) {
            helper.fail("Challenger's Sigil must carry CustomModelData string \"challengers_sigil\"");
        }
        helper.succeed();
    }
}
