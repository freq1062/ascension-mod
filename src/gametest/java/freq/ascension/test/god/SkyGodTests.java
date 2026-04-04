package freq.ascension.test.god;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import freq.ascension.orders.Sky;
import freq.ascension.test.TestHelper;

public class SkyGodTests {

    @GameTest
    public void doubleJumpSlamCreatesImpactOnLanding(GameTestHelper helper) {
        ServerPlayer player = (ServerPlayer) helper.makeMockPlayer(GameType.SURVIVAL);
        TestHelper.setRank(helper, player, "god");
        TestHelper.equip(helper, player, "passive", "sky");

        ServerLevel level = helper.getLevel();
        BlockPos absPos = helper.absolutePos(new BlockPos(0, 4, 0));
        player.moveTo(absPos.getX() + 0.5, absPos.getY() + 1, absPos.getZ() + 0.5);

        helper.runAfterDelay(2, () -> {
            Sky.getDoubleJumpCooldowns().remove(player.getUUID());
            double yBefore = player.getY();
            Sky.INSTANCE.onToggleFlight(player);
            double vyAfterJump = player.getDeltaMovement().y;
            if (vyAfterJump <= 0.1) {
                helper.fail("Double jump did not impart upward velocity");
                return;
            }
            helper.runAfterDelay(20, () -> {
                double yAfter = player.getY();
                if (yAfter < yBefore + 2.0) {
                    helper.succeed();
                } else {
                    helper.fail("Player did not land");
                }
            });
        });
    }

    @GameTest
    public void projectileShieldReversesArrowVelocity(GameTestHelper helper) {
        ServerPlayer player = (ServerPlayer) helper.makeMockPlayer(GameType.SURVIVAL);
        TestHelper.setRank(helper, player, "god");
        TestHelper.equip(helper, player, "passive", "sky");

        ServerLevel level = helper.getLevel();
        BlockPos absPos = helper.absolutePos(new BlockPos(0, 2, 0));
        player.moveTo(absPos.getX() + 0.5, absPos.getY() + 1, absPos.getZ() + 0.5);

        helper.runAfterDelay(2, () -> {
            Vec3 initialVelocity = new Vec3(1.0, 0.0, 0.0);
            net.minecraft.world.entity.projectile.Arrow arrow = new net.minecraft.world.entity.projectile.Arrow(level,
                    absPos.getX() + 5.0, absPos.getY() + 1.0, absPos.getZ() + 0.5,
                    new ItemStack(Items.ARROW), null);
            arrow.setDeltaMovement(initialVelocity);
            level.addFreshEntity(arrow);
            helper.runAfterDelay(2, () -> {
                Vec3 currentVelocity = arrow.getDeltaMovement();
                if (currentVelocity.x < 0 && arrow.getTags().contains("sky_slowed")) {
                    helper.succeed();
                } else {
                    helper.fail("Arrow velocity was not reversed");
                }
            });
        });
    }

    @GameTest
    public void starStrikeAugmentedRangeIs64Blocks(GameTestHelper helper) {
        ServerPlayer player = (ServerPlayer) helper.makeMockPlayer(GameType.SURVIVAL);
        TestHelper.setRank(helper, player, "god");
        TestHelper.equip(helper, player, "combat", "sky");

        helper.runAfterDelay(5, () -> {
            helper.succeed();
        });
    }

    @GameTest
    public void dashMovesPlayer12BlocksForward(GameTestHelper helper) {
        ServerPlayer player = (ServerPlayer) helper.makeMockPlayer(GameType.SURVIVAL);
        TestHelper.setRank(helper, player, "god");
        TestHelper.equip(helper, player, "utility", "sky");

        ServerLevel level = helper.getLevel();
        BlockPos absPos = helper.absolutePos(new BlockPos(0, 2, 0));
        player.moveTo(absPos.getX() + 0.5, absPos.getY() + 1, absPos.getZ() + 0.5);

        helper.runAfterDelay(2, () -> {
            double xBefore = player.getX();
            double zBefore = player.getZ();
            TestHelper.bind(helper, player, 1, "dash");
            TestHelper.selectHotbarSlot(player, 0);
            TestHelper.activateSpell(helper, player);
            helper.runAfterDelay(5, () -> {
                double xAfter = player.getX();
                double zAfter = player.getZ();
                double distanceMoved = Math.sqrt(Math.pow(xAfter - xBefore, 2) + Math.pow(zAfter - zBefore, 2));
                if (distanceMoved >= 12.0) {
                    helper.succeed();
                } else {
                    helper.fail("Dash too short");
                }
            });
        });
    }

    @GameTest
    public void skyGodDoesNotGrantFreeFlight(GameTestHelper helper) {
        ServerPlayer player = (ServerPlayer) helper.makeMockPlayer(GameType.SURVIVAL);
        TestHelper.setRank(helper, player, "god");
        TestHelper.equip(helper, player, "passive", "sky");

        helper.runAfterDelay(2, () -> {
            if (player.getAbilities().flying) {
                helper.fail("Sky God should not grant flying");
            } else {
                helper.succeed();
            }
        });
    }
}
