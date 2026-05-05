package freq.ascension.test.god;

import freq.ascension.Config;
import freq.ascension.orders.Sky;
import freq.ascension.orders.SkyGod;
import freq.ascension.test.TestHelper;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public class SkyGodTests {

    @GameTest(maxTicks = 60)
    public void doubleJumpSlamCreatesImpactOnLanding(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        TestHelper.setGodDirect(helper, player, "sky");

        helper.runAfterDelay(41, () -> {
            Sky.getDoubleJumpCooldowns().remove(player.getUUID());
            Sky.INSTANCE.onToggleFlight(player);
            if (player.getDeltaMovement().y > 0.1) {
                helper.succeed();
            } else {
                helper.fail("Double jump did not impart upward velocity");
            }
        });
    }

    @GameTest
    public void projectileShieldReversesArrowVelocity(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        TestHelper.setGodDirect(helper, player, "sky");

        helper.runAfterDelay(2, () -> {
            Arrow arrow = new Arrow(helper.getLevel(),
                    player.getX() + 5.0, player.getY() + 1.0, player.getZ(),
                    new ItemStack(Items.ARROW), null);
            arrow.setDeltaMovement(new Vec3(1.0, 0.0, 0.0));
            helper.getLevel().addFreshEntity(arrow);
            SkyGod.INSTANCE.applyProjectileShield(player, arrow);
            if (arrow.getDeltaMovement().x < 0 && arrow.getTags().contains("sky_slowed")) {
                helper.succeed();
            } else {
                helper.fail("Arrow velocity was not reversed");
            }
        });
    }

    @GameTest
    public void starStrikeAugmentedRangeIs64Blocks(GameTestHelper helper) {
        if (SkyGod.INSTANCE.getSpellStats("star_strike").getBool(0)) {
            helper.succeed();
        } else {
            helper.fail("Expected augmented star strike for sky god");
        }
    }

    @GameTest
    public void dashMovesPlayer12BlocksForward(GameTestHelper helper) {
        int dashDistance = SkyGod.INSTANCE.getSpellStats("dash").getInt(0);
        if (dashDistance == Config.skyGodDashDistance && dashDistance == 12) {
            helper.succeed();
        } else {
            helper.fail("Expected dash distance 12, got " + dashDistance);
        }
    }

    @GameTest(maxTicks = 60)
    public void skyGodDoesNotGrantFreeFlight(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        TestHelper.setGodDirect(helper, player, "sky");

        helper.runAfterDelay(41, () -> {
            if (player.getAbilities().flying) {
                helper.fail("Sky God should not grant flying");
            } else {
                helper.succeed();
            }
        });
    }
}
