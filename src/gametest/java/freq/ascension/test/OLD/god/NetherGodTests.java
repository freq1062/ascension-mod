package freq.ascension.test.god;

import freq.ascension.Config;
import freq.ascension.managers.LavaFlightManager;
import freq.ascension.registry.SpellRegistry;
import freq.ascension.test.TestHelper;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.animal.HappyGhast;
import net.minecraft.world.level.block.Blocks;

/**
 * Integration tests for the Nether God order.
 */
public class NetherGodTests {

    private static void promoteToNetherGod(GameTestHelper helper, ServerPlayer player) {
        TestHelper.setGodDirect(helper, player, "nether");
    }

    private static void submergeInLava(GameTestHelper helper, ServerPlayer player) {
        var feet = player.blockPosition();
        helper.getLevel().setBlockAndUpdate(feet, Blocks.LAVA.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(feet.above(), Blocks.LAVA.defaultBlockState());
    }

    private static HappyGhast findNearbyGhast(GameTestHelper helper, ServerPlayer player) {
        return helper.getLevel().getEntitiesOfClass(HappyGhast.class, player.getBoundingBox().inflate(8.0)).stream()
                .findFirst()
                .orElse(null);
    }

    @GameTest(maxTicks = 60)
    public void fireResistanceInheritedFromNetherGodPassive(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        promoteToNetherGod(helper, player);

        helper.runAfterDelay(41, () -> {
            TestHelper.assertEffect(helper, player, MobEffects.FIRE_RESISTANCE,
                    "Expected fire resistance from nether god passive");
            helper.succeed();
        });
    }

    @GameTest
    public void lavaGlideActivatesWhenSprintingSubmergedInLava(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        promoteToNetherGod(helper, player);

        helper.runAfterDelay(2, () -> {
            player.setSprinting(true);
            submergeInLava(helper, player);
            helper.runAfterDelay(2, () -> {
                if (LavaFlightManager.isActive(player.getUUID())) {
                    helper.succeed();
                } else {
                    helper.fail("Expected lava glide to activate while submerged in lava");
                }
            });
        });
    }

    @GameTest
    public void soulRageDurationLongerThanDemigod(GameTestHelper helper) {
        if (Config.netherSoulRageDurationGod > Config.netherSoulRageDuration) {
            helper.succeed();
        } else {
            helper.fail("Expected nether god soul rage duration > demigod duration");
        }
    }

    @GameTest
    public void ghastCarryDoubleHealthForNetherGod(GameTestHelper helper) {
        ServerPlayer godPlayer = helper.makeMockServerPlayerInLevel();
        ServerPlayer demigodPlayer = helper.makeMockServerPlayerInLevel();
        promoteToNetherGod(helper, godPlayer);
        TestHelper.equip(helper, demigodPlayer, "utility", "nether");
        godPlayer.setPos(godPlayer.getX() - 12.0, godPlayer.getY(), godPlayer.getZ());
        demigodPlayer.setPos(demigodPlayer.getX() + 12.0, demigodPlayer.getY(), demigodPlayer.getZ());

        helper.runAfterDelay(2, () -> {
            TestHelper.bind(helper, godPlayer, 1, "ghast_carry");
            TestHelper.bind(helper, demigodPlayer, 1, "ghast_carry");
            TestHelper.activateSpell(helper, godPlayer);
            TestHelper.activateSpell(helper, demigodPlayer);
            helper.runAfterDelay(3, () -> {
                HappyGhast godGhast = findNearbyGhast(helper, godPlayer);
                HappyGhast demigodGhast = findNearbyGhast(helper, demigodPlayer);
                if (godGhast == null || demigodGhast == null) {
                    helper.fail("Expected both ghast_carry activations to summon ghasts");
                } else if (godGhast.getMaxHealth() == demigodGhast.getMaxHealth() * 2.0F) {
                    helper.succeed();
                } else {
                    helper.fail("Expected nether god ghast health to double demigod ghast health, got "
                            + godGhast.getMaxHealth() + " vs " + demigodGhast.getMaxHealth());
                }
            });
        });
    }
}
