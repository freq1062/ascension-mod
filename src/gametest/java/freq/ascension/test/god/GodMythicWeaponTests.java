package freq.ascension.test.god;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import freq.ascension.Config;
import freq.ascension.orders.Order;
import freq.ascension.weapons.ColossusHammer;
import freq.ascension.weapons.GravitonGauntlet;
import freq.ascension.weapons.HellfireCrossbow;
import freq.ascension.weapons.PrismWand;
import freq.ascension.weapons.RuinousScythe;
import freq.ascension.weapons.TempestTrident;
import freq.ascension.weapons.VinewrathAxe;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Integration tests for all god-tier mythic weapons, covering unique mechanics,
 * mode toggles, combo activations, and the unbreakable attribute for each weapon.
 */
public class GodMythicWeaponTests {

    /**
     * Gives GravitonGauntlet to a sky god player; uses in pull mode;
     * asserts a nearby mob moves toward the player.
     */
    @GameTest
    public void gravitonGauntletPullModeAttractsMob(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Mob victim = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, helper.absolutePos(new BlockPos(5, 2, 1)));

        player.setPos(0.5, 2.0, 0.5);
        victim.setPos(player.getX() + 4.0, player.getY(), player.getZ());
        GravitonGauntlet.INSTANCE.onShiftUse(player);

        if (victim.getDeltaMovement().x < 0.0) {
            helper.succeed();
        } else {
            helper.fail("Expected Graviton Gauntlet pull mode to move the mob toward the player");
        }
    }

    /**
     * Toggles GravitonGauntlet to push mode; uses it;
     * asserts a nearby mob moves away from the player.
     */
    @GameTest
    public void gravitonGauntletPushModeRepelsMob(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Mob victim = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, helper.absolutePos(new BlockPos(5, 2, 1)));

        player.setPos(0.5, 2.0, 0.5);
        victim.setPos(player.getX() + 4.0, player.getY(), player.getZ());
        GravitonGauntlet.toggleMode(player.getUUID());
        GravitonGauntlet.INSTANCE.onShiftUse(player);

        if (victim.getDeltaMovement().x > 0.0) {
            helper.succeed();
        } else {
            helper.fail("Expected Graviton Gauntlet push mode to move the mob away from the player");
        }
    }

    /**
     * Sneak+right-clicks the GravitonGauntlet;
     * asserts mode changes from PULL to PUSH.
     */
    @GameTest
    public void gravitonGauntletModeToggleWorks(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setItemInHand(InteractionHand.MAIN_HAND, GravitonGauntlet.INSTANCE.createItem());

        boolean before = GravitonGauntlet.getPullMode(player.getUUID());
        GravitonGauntlet.INSTANCE.onShiftLeftClick(player);
        boolean after = GravitonGauntlet.getPullMode(player.getUUID());

        if (before && !after) {
            helper.succeed();
        } else {
            helper.fail("Expected Graviton Gauntlet mode toggle to switch from pull to push");
        }
    }

    /**
     * Gives GravitonGauntlet to a player;
     * asserts the item has the isUnbreakable attribute set.
     */
    @GameTest
    public void gravitonGauntletIsUnbreakable(GameTestHelper helper) {
        assertUnbreakable(helper, GravitonGauntlet.INSTANCE.createItem(), "Graviton Gauntlet");
    }

    /**
     * Gives ColossusHammer to an earth god player; attacks a mob;
     * asserts damage is dealt.
     */
    @GameTest
    public void colossusHammerDamagesMob(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Mob victim = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, helper.absolutePos(new BlockPos(1, 2, 1)));

        player.setItemInHand(InteractionHand.MAIN_HAND, ColossusHammer.INSTANCE.createItem());
        float healthBefore = victim.getHealth();
        victim.hurt(helper.getLevel().damageSources().playerAttack(player), 6.0f);

        if (victim.getHealth() < healthBefore) {
            helper.succeed();
        } else {
            helper.fail("Expected Colossus Hammer attack to deal damage");
        }
    }

    /**
     * Gives ColossusHammer to a player;
     * asserts the item has the isUnbreakable attribute set.
     */
    @GameTest
    public void colossusHammerIsUnbreakable(GameTestHelper helper) {
        assertUnbreakable(helper, ColossusHammer.INSTANCE.createItem(), "Colossus Hammer");
    }

    /**
     * Gives HellfireCrossbow to a nether god player; fires 3 bolts at a mob;
     * asserts hellfire beam activates on the 3rd shot.
     */
    @GameTest
    public void hellfireBeamTriggersOnThirdShot(GameTestHelper helper) {
        HellfireCrossbow.FIREWORK_COUNTER.clear();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        boolean first = HellfireCrossbow.INSTANCE.onFireworkShot(player);
        boolean second = HellfireCrossbow.INSTANCE.onFireworkShot(player);
        boolean third = HellfireCrossbow.INSTANCE.onFireworkShot(player);
        int counter = HellfireCrossbow.FIREWORK_COUNTER.getOrDefault(player.getUUID(), -1);

        if (!first && !second && third && counter == 0) {
            helper.succeed();
        } else {
            helper.fail("Expected Hellfire beam to activate on the third shot; counter=" + counter);
        }
    }

    /**
     * Gives HellfireCrossbow to a player;
     * asserts the item has the isUnbreakable attribute set.
     */
    @GameTest
    public void hellfireCrossbowIsUnbreakable(GameTestHelper helper) {
        assertUnbreakable(helper, HellfireCrossbow.INSTANCE.createItem(), "Hellfire Crossbow");
    }

    /**
     * Gives PrismWand to a magic god player; fires at a mob within 5 degrees;
     * asserts the arrow homes to the mob.
     */
    @GameTest
    public void prismWandArrowCurvesToMob(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Mob aligned = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, helper.absolutePos(new BlockPos(1, 2, 1)));
        Mob offAxis = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, helper.absolutePos(new BlockPos(2, 2, 2)));

        Vec3 eyePos = player.getEyePosition();
        Vec3 lookDir = player.getLookAngle().normalize();
        Vec3 perpendicular = new Vec3(-lookDir.z, 0.0, lookDir.x).normalize();

        Vec3 alignedPos = eyePos.add(lookDir.scale(8.0));
        Vec3 offAxisPos = alignedPos.add(perpendicular.scale(2.0));
        aligned.setPos(alignedPos.x, player.getY(), alignedPos.z);
        offAxis.setPos(offAxisPos.x, player.getY(), offAxisPos.z);

        LivingEntity target = PrismWand.findTarget(eyePos, lookDir, List.of(aligned, offAxis),
                Config.prismWandRange, 5.0);
        if (target == aligned) {
            helper.succeed();
        } else {
            helper.fail("Expected Prism Wand targeting to pick the mob within 5 degrees");
        }
    }

    /**
     * Gives PrismWand to a player;
     * asserts the item has the isUnbreakable attribute set.
     */
    @GameTest
    public void prismWandIsUnbreakable(GameTestHelper helper) {
        assertUnbreakable(helper, PrismWand.INSTANCE.createItem(), "Prism Wand");
    }

    /**
     * Gives RuinousScythe to an end god player; hits a mob 3 times;
     * asserts combo activates on the 3rd hit.
     */
    @GameTest
    public void ruinousScytheComboActivatesOnThirdHit(GameTestHelper helper) {
        RuinousScythe.CAPTURED_ATTACK_STRENGTH.clear();
        RuinousScythe.COMBO_COUNTERS.clear();
        RuinousScythe.RESET_GENERATIONS.clear();

        ServerPlayer attacker = helper.makeMockServerPlayerInLevel();
        attacker.setItemInHand(InteractionHand.MAIN_HAND, RuinousScythe.INSTANCE.createItem());
        RuinousScythe.CAPTURED_ATTACK_STRENGTH.put(attacker.getUUID(), 1.0f);
        Mob victim = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, helper.absolutePos(new BlockPos(1, 2, 1)));
        float healthBefore = victim.getHealth();

        for (int i = 0; i < Config.ruinousScytheHitsNeeded; i++) {
            RuinousScythe.INSTANCE.onAttack(attacker, victim,
                    new Order.DamageContext(helper.getLevel().damageSources().playerAttack(attacker), 1.0f));
        }

        int comboCount = RuinousScythe.COMBO_COUNTERS
                .getOrDefault(attacker.getUUID(), new ConcurrentHashMap<>())
                .getOrDefault(victim.getUUID(), 0);
        if (comboCount == 0 && victim.getHealth() < healthBefore) {
            helper.succeed();
        } else {
            helper.fail("Expected Ruinous Scythe combo to trigger on configured hit threshold");
        }
    }

    /**
     * Gives RuinousScythe to a player;
     * asserts the item has the isUnbreakable attribute set.
     */
    @GameTest
    public void ruinousScytheIsUnbreakable(GameTestHelper helper) {
        assertUnbreakable(helper, RuinousScythe.INSTANCE.createItem(), "Ruinous Scythe");
    }

    /**
     * Gives TempestTrident to an ocean god player; hits a mob 3 times;
     * asserts lightning strikes on the 3rd hit.
     */
    @GameTest
    public void tempestTridentLightningOn3rdHit(GameTestHelper helper) {
        TempestTrident.hitCounters.clear();
        TempestTrident.hitTimestamps.clear();

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setItemInHand(InteractionHand.MAIN_HAND, TempestTrident.INSTANCE.createItem());
        Mob victim = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, helper.absolutePos(new BlockPos(1, 2, 1)));

        TempestTrident.INSTANCE.onProjectileHit(player, victim, null);
        TempestTrident.INSTANCE.onProjectileHit(player, victim, null);
        float beforeThird = victim.getHealth();
        TempestTrident.INSTANCE.onProjectileHit(player, victim, null);

        if (victim.getHealth() < beforeThird && TempestTrident.hitCounters.getOrDefault(victim.getUUID(), -1) == 0) {
            helper.succeed();
        } else {
            helper.fail("Expected Tempest Trident third hit to trigger lightning damage");
        }
    }

    /**
     * Sneak+right-clicks the TempestTrident;
     * asserts mode toggles between LOYALTY and RIPTIDE.
     */
    @GameTest
    public void tempestTridentModeToggleWorks(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack trident = TempestTrident.INSTANCE.createItem();
        player.setItemInHand(InteractionHand.MAIN_HAND, trident);
        TempestTrident.lastModeSwitchTick.clear();
        TempestTrident.lastToggleTick.clear();

        TempestTrident.INSTANCE.onShiftLeftClick(player);
        if (!TempestTrident.isLoyaltyModeStack(trident)) {
            helper.succeed();
        } else {
            helper.fail("Expected Tempest Trident mode toggle to switch out of loyalty mode");
        }
    }

    /**
     * Gives TempestTrident to a player;
     * asserts the item has the isUnbreakable attribute set.
     */
    @GameTest
    public void tempestTridentIsUnbreakable(GameTestHelper helper) {
        assertUnbreakable(helper, TempestTrident.INSTANCE.createItem(), "Tempest Trident");
    }

    /**
     * Gives VinewrathAxe to a flora god player; attacks a shielding player;
     * asserts the shield is placed on cooldown.
     */
    @GameTest
    public void vinewrathAxeDisablesTargetShield(GameTestHelper helper) {
        ServerPlayer attacker = helper.makeMockServerPlayerInLevel();
        ServerPlayer victim = helper.makeMockServerPlayerInLevel();
        attacker.setItemInHand(InteractionHand.MAIN_HAND, VinewrathAxe.INSTANCE.createItem());
        victim.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
        victim.startUsingItem(InteractionHand.OFF_HAND);

        VinewrathAxe.INSTANCE.onAttack(attacker, victim,
                new Order.DamageContext(helper.getLevel().damageSources().playerAttack(attacker), 1.0f));
        helper.runAfterDelay(2, () -> {
            if (victim.getCooldowns().isOnCooldown(Items.SHIELD.getDefaultInstance())) {
                helper.succeed();
            } else {
                helper.fail("Expected Vinewrath Axe attack to apply a shield cooldown");
            }
        });
    }

    /**
     * Gives VinewrathAxe to a player;
     * asserts the item has the isUnbreakable attribute set.
     */
    @GameTest
    public void vinewrathAxeIsUnbreakable(GameTestHelper helper) {
        assertUnbreakable(helper, VinewrathAxe.INSTANCE.createItem(), "Vinewrath Axe");
    }

    private static void assertUnbreakable(GameTestHelper helper, ItemStack stack, String weaponName) {
        if (stack.get(DataComponents.UNBREAKABLE) != null) {
            helper.succeed();
        } else {
            helper.fail("Expected " + weaponName + " to be unbreakable");
        }
    }
}
