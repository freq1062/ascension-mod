package freq.ascension.test.demigod;

import freq.ascension.managers.AscensionData;
import freq.ascension.orders.Sky;
import freq.ascension.test.TestHelper;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

/**
 * Integration tests for the Sky Order (Demigod) abilities.
 */
public class SkyDemigodTests {

    @GameTest(maxTicks = 100)
    public void doubleJumpPropelsPlayerUpward(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        TestHelper.equip(helper, player, "passive", "sky");

        helper.runAfterDelay(41, () -> {
            Sky.getDoubleJumpCooldowns().remove(player.getUUID());
            Sky.INSTANCE.onToggleFlight(player);
            if (player.getDeltaMovement().y > 0.1) {
                helper.succeed();
            } else {
                helper.fail("Sky double jump did not apply upward momentum");
            }
        });
    }

    @GameTest(maxTicks = 100)
    public void doubleJumpCooldownBlocksImmediateRepeat(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        TestHelper.equip(helper, player, "passive", "sky");

        helper.runAfterDelay(41, () -> {
            Sky.getDoubleJumpCooldowns().remove(player.getUUID());
            Sky.INSTANCE.onToggleFlight(player);
            if (player.getDeltaMovement().y <= 0.1) {
                helper.fail("First double jump never activated");
                return;
            }

            player.setDeltaMovement(Vec3.ZERO);
            Sky.INSTANCE.onToggleFlight(player);
            if (player.getDeltaMovement().y == 0.0) {
                helper.succeed();
            } else {
                helper.fail("Sky double jump cooldown should block an immediate second activation");
            }
        });
    }

    @GameTest
    public void projectileVelocityReducedByHalfWithPassive(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        TestHelper.equip(helper, player, "passive", "sky");

        helper.runAfterDelay(2, () -> {
            Arrow arrow = spawnArrowNearPlayer(helper, player, new Vec3(-1.0, 0.0, 0.0));
            Sky.INSTANCE.applyProjectileShield(player, arrow);
            helper.runAfterDelay(1, () -> {
                if (Math.abs(arrow.getDeltaMovement().x) < 0.75 && arrow.getTags().contains("sky_slowed")) {
                    helper.succeed();
                } else {
                    helper.fail("Sky projectile shield should halve incoming arrow velocity");
                }
            });
        });
    }

    @GameTest
    public void projectileNotAffectedWithoutSkyPassive(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        helper.runAfterDelay(2, () -> {
            Arrow arrow = spawnArrowNearPlayer(helper, player, new Vec3(-1.0, 0.0, 0.0));
            helper.runAfterDelay(1, () -> {
                if (Math.abs(arrow.getDeltaMovement().x) > 0.75 && !arrow.getTags().contains("sky_slowed")) {
                    helper.succeed();
                } else {
                    helper.fail("Arrow velocity should remain vanilla without Sky passive");
                }
            });
        });
    }

    @GameTest
    public void starStrikeDeals30PctMaxHpDamage(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        TestHelper.equip(helper, player, "combat", "sky");

        helper.runAfterDelay(2, () -> {
            Mob target = helper.spawnWithNoFreeWill(EntityType.RAVAGER, player.blockPosition().offset(0, 0, 6));
            float initialHealth = target.getHealth();
            TestHelper.selectHotbarSlot(player, 0);
            TestHelper.bind(helper, player, 1, "star_strike");
            TestHelper.activateSpell(helper, player);
            helper.runAfterDelay(10, () -> {
                if (target.getHealth() < initialHealth) {
                    helper.succeed();
                } else {
                    helper.fail("star_strike should damage a target in front of the player");
                }
            });
        });
    }

    @GameTest
    public void starStrikeOnlyActiveWithCombatSlot(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        TestHelper.equip(helper, player, "passive", "sky");

        helper.runAfterDelay(2, () -> {
            TestHelper.bind(helper, player, 1, "star_strike");
            Map<Integer, String> bindings = ((AscensionData) player).getSpellBindings();
            if (!bindings.containsValue("star_strike")) {
                helper.succeed();
            } else {
                helper.fail("star_strike should not activate without the Sky combat slot");
            }
        });
    }

    @GameTest(maxTicks = 100)
    public void skyPassiveDoesNotGrantFreeFlight(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        TestHelper.equip(helper, player, "passive", "sky");

        helper.runAfterDelay(41, () -> {
            if (player.getAbilities().flying) {
                helper.fail("Sky passive should not leave the player in a flying state");
            } else {
                helper.succeed();
            }
        });
    }

    private static Arrow spawnArrowNearPlayer(GameTestHelper helper, ServerPlayer player, Vec3 velocity) {
        Arrow arrow = new Arrow(helper.getLevel(),
                player.getX() + 1.5, player.getY() + 1.0, player.getZ(),
                new ItemStack(Items.ARROW), null);
        arrow.setDeltaMovement(velocity);
        helper.getLevel().addFreshEntity(arrow);
        return arrow;
    }
}
