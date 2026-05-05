package freq.ascension.test.god;

import freq.ascension.Config;
import freq.ascension.orders.Ocean;
import freq.ascension.orders.OceanGod;
import freq.ascension.managers.SpellCooldownManager;
import freq.ascension.test.TestHelper;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;

/**
 * Integration tests for the Ocean God order.
 */
public class OceanGodTests {

    private static void promoteToOceanGod(GameTestHelper helper, ServerPlayer player) {
        TestHelper.setGodDirect(helper, player, "ocean");
    }

    private static void submergePlayer(GameTestHelper helper, ServerPlayer player) {
        BlockPos feet = player.blockPosition();
        helper.getLevel().setBlockAndUpdate(feet, Blocks.WATER.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(feet.above(), Blocks.WATER.defaultBlockState());
    }

    @GameTest(maxTicks = 60)
    public void conduitPowerAppliedOnPassiveEquip(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        promoteToOceanGod(helper, player);

        helper.runAfterDelay(41, () -> {
            TestHelper.assertEffect(helper, player, MobEffects.CONDUIT_POWER,
                    "Expected Conduit Power from ocean god passive");
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 100)
    public void conduitPowerRefreshesAfter40Ticks(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        promoteToOceanGod(helper, player);

        helper.runAfterDelay(85, () -> {
            TestHelper.assertEffect(helper, player, MobEffects.CONDUIT_POWER,
                    "Conduit Power was not refreshed for ocean god");
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 100)
    public void hasteAppliedOnOceanGodPassive(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        promoteToOceanGod(helper, player);

        helper.runAfterDelay(2, () -> {
            submergePlayer(helper, player);
            helper.runAfterDelay(41, () -> {
                var haste = player.getEffect(MobEffects.HASTE);
                if (haste != null && haste.getAmplifier() == 1) {
                    helper.succeed();
                } else {
                    helper.fail("Expected Haste II while submerged as ocean god");
                }
            });
        });
    }

    @GameTest
    public void dolphinsGraceThreeLevelCycleOnActivate(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        promoteToOceanGod(helper, player);

        helper.runAfterDelay(2, () -> {
            OceanGod.INSTANCE.executeActiveSpell("dolphins_grace", player);
            var first = player.getEffect(MobEffects.DOLPHINS_GRACE);
            if (first == null || first.getAmplifier() != 0) {
                helper.fail("First dolphins_grace activation should apply amplifier 0");
                return;
            }

            OceanGod.INSTANCE.executeActiveSpell("dolphins_grace", player);
            var second = player.getEffect(MobEffects.DOLPHINS_GRACE);
            if (second == null || second.getAmplifier() != 1) {
                helper.fail("Second dolphins_grace activation should apply amplifier 1");
                return;
            }

            OceanGod.INSTANCE.executeActiveSpell("dolphins_grace", player);
            TestHelper.assertNoEffect(helper, player, MobEffects.DOLPHINS_GRACE,
                    "Third dolphins_grace activation should clear the effect");
            helper.succeed();
        });
    }

    @GameTest
    public void molecularFluxDurationLongerThanDemigod(GameTestHelper helper) {
        int godDuration = OceanGod.INSTANCE.getSpellStats("molecular_flux").getInt(1);
        int demigodDuration = Ocean.INSTANCE.getSpellStats("molecular_flux").getInt(1);
        if (godDuration > demigodDuration) {
            helper.succeed();
        } else {
            helper.fail("Expected ocean god molecular flux duration > demigod duration, got "
                    + godDuration + " vs " + demigodDuration);
        }
    }

    @GameTest
    public void drownRadiusGreaterThanDemigodRadius(GameTestHelper helper) {
        int godRadius = OceanGod.INSTANCE.getSpellStats("drown").getInt(1);
        int demigodRadius = Ocean.INSTANCE.getSpellStats("drown").getInt(1);
        if (godRadius > demigodRadius) {
            helper.succeed();
        } else {
            helper.fail("Expected ocean god drown radius > demigod radius, got "
                    + godRadius + " vs " + demigodRadius);
        }
    }
}
