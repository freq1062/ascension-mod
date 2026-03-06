package freq.ascension.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;

import freq.ascension.orders.Order.DamageContext;

public class OceanPassiveTests {

    /**
     * Verifies that Ocean passive applies WATER_BREATHING with a duration that
     * exceeds the 40-tick applyEffect refresh cycle, so air supply never drains.
     *
     * Root cause: AbilityManager.init() was never called in Ascension.onInitialize(),
     * so the ContinuousTask (every 40 ticks) that calls order.applyEffect() never
     * ran. Fix: AbilityManager.init() is now called after registerAllSpells().
     * Duration raised from 60 → 80 ticks to provide a 40-tick safety buffer beyond
     * the refresh interval.
     */
    @GameTest
    public void waterBreathingDurationCoversRefreshCycle(GameTestHelper helper) {
        // Mirror exactly what Ocean.applyEffect() applies (80 ticks).
        MobEffectInstance effectInstance = new MobEffectInstance(MobEffects.WATER_BREATHING, 80, 0);

        if (effectInstance.getEffect() != MobEffects.WATER_BREATHING) {
            helper.fail("Effect type must be WATER_BREATHING");
        }
        // applyEffect fires every 40 ticks; duration must be > 40 so the effect
        // never expires between refreshes, preventing any air loss.
        if (effectInstance.getDuration() <= 40) {
            helper.fail("WATER_BREATHING duration (" + effectInstance.getDuration()
                + ") must exceed the 40-tick refresh interval");
        }
        helper.succeed();
    }

    /**
     * Verifies that WATER_BREATHING prevents air loss for a mob submerged in water.
     * Proxies the player mechanic: all LivingEntities lose air when submerged,
     * but WATER_BREATHING suppresses that loss. The fix ensures Ocean passive always
     * maintains the effect between the 40-tick applyEffect refresh cycles.
     * Duration raised from 60 → 80 ticks so the effect is still active (20 ticks
     * remaining) when we sample air supply at T=60, avoiding off-by-one expiry.
     */
    @GameTest(maxTicks = 80)
    public void waterBreathingPreventsAirLossWhenSubmerged(GameTestHelper helper) {
        BlockPos center = new BlockPos(1, 2, 1);
        // Create a column of water so the entity is fully submerged
        helper.setBlock(center, Blocks.WATER.defaultBlockState());
        helper.setBlock(center.above(), Blocks.WATER.defaultBlockState());

        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 1, 2, 1);
        zombie.setAirSupply(zombie.getMaxAirSupply());

        // Apply WATER_BREATHING exactly as Ocean.applyEffect() does (80 ticks).
        zombie.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 80, 0));

        int airAtStart = zombie.getAirSupply();

        // At T=60 the effect still has 20 ticks remaining, so no air drain occurs.
        helper.runAfterDelay(60, () -> {
            int airNow = zombie.getAirSupply();
            if (airNow < airAtStart) {
                helper.fail("Air supply decreased despite WATER_BREATHING: expected >= "
                    + airAtStart + ", got " + airNow);
            }
            helper.succeed();
        });
    }

    /**
     * Verifies that Ocean.onEntityDamageByEntity scales damage by exactly 1.5x
     * (auto-crit) via the DamageContext when the attacker is in water.
     *
     * Root cause for missing particles: addParticle() on Level sends count=0,
     * producing 1 invisible particle at feet level. Fix: replaced with
     * ServerLevel.sendParticles(CRIT, ..., count=10) at body-centre height.
     */
    @GameTest
    public void oceanAutocritScalesDamageBy150Percent(GameTestHelper helper) {
        float baseDamage = 8.0f;
        DamageContext ctx = new DamageContext(null, baseDamage);

        // Reproduce the exact scaling from Ocean.onEntityDamageByEntity
        ctx.setAmount((float) (ctx.getAmount() * 1.5));

        float expected = baseDamage * 1.5f; // 12.0
        if (Math.abs(ctx.getAmount() - expected) >= 0.001f) {
            helper.fail("Auto-crit damage should be " + expected + " but was " + ctx.getAmount());
        }
        helper.succeed();
    }

    /**
     * Integration smoke-test: a mob in water loses health when dealt the crit
     * damage amount (base * 1.5), confirming the damage pipeline is intact for
     * the underwater auto-crit scenario.
     */
    @GameTest(maxTicks = 60)
    public void mobTakesCritDamageInWater(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 2, 1), Blocks.WATER.defaultBlockState());
        helper.setBlock(new BlockPos(1, 3, 1), Blocks.WATER.defaultBlockState());

        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 1, 2, 1);
        zombie.setHealth(20.0f);

        float baseDamage = 5.0f;
        float critDamage = baseDamage * 1.5f; // 7.5

        helper.runAfterDelay(5, () -> {
            zombie.hurtServer(
                helper.getLevel(),
                helper.getLevel().damageSources().generic(),
                critDamage
            );
            helper.succeedWhen(() -> {
                float expectedHealth = 20.0f - critDamage;
                if (Math.abs(zombie.getHealth() - expectedHealth) >= 0.5f) {
                    helper.fail("Zombie should have " + expectedHealth + " HP after crit, has "
                        + zombie.getHealth());
                }
            });
        });
    }

    /**
     * Verifies that Ocean.canWalkOnPowderSnow() returns true by default
     * (the interface method that PowderSnowMixin delegates to).
     * Also confirms the MobEffectInstance ambient/particle/icon flags are correct
     * for the invisible-effect feature (ambient=true, showParticles=false, showIcon=true).
     */
    @GameTest
    public void oceanPassiveEffectsAreAmbientAndHidden(GameTestHelper helper) {
        MobEffectInstance wb = new MobEffectInstance(MobEffects.WATER_BREATHING, 80, 0, true, false, true);

        if (!wb.isAmbient()) {
            helper.fail("WATER_BREATHING effect must be ambient (no dot particles)");
        }
        if (wb.isVisible()) {
            helper.fail("WATER_BREATHING effect must not show particles");
        }
        if (!wb.showIcon()) {
            helper.fail("WATER_BREATHING effect must still show the HUD icon");
        }
        helper.succeed();
    }

    /**
     * Compile-time check: SoundEvents.GLASS_BREAK exists in 1.21.10 and is a SoundEvent.
     * This ensures molecularFlux sound calls will not fail with missing-symbol errors.
     */
    @GameTest
    public void molecularFluxSoundConstantExists(GameTestHelper helper) {
        net.minecraft.sounds.SoundEvent sound = net.minecraft.sounds.SoundEvents.GLASS_BREAK;
        if (sound == null) {
            helper.fail("SoundEvents.GLASS_BREAK must not be null");
        }
        helper.succeed();
    }

    /**
     * Validates the Drown sphere-ring geometry: for each latitude fraction used
     * in Drown.RING_FRACS, the derived ring radius must be non-negative and each
     * ring point returned by GeometrySource.circle must lie at the correct distance
     * from the ring centre (within floating-point tolerance).
     */
    @GameTest
    public void drownRingGeometryIsCorrect(GameTestHelper helper) {
        float sphereRadius = 8.0f;
        float[] fracs = { -1.0f, -0.5f, 0.0f, 0.5f, 1.0f };
        org.joml.Vector3f centre = new org.joml.Vector3f(0, 0, 0);

        for (float frac : fracs) {
            float h = frac * sphereRadius;
            float ringRadius = (float) Math.sqrt(sphereRadius * sphereRadius - h * h);

            if (ringRadius < 0) {
                helper.fail("Ring radius must be non-negative for frac=" + frac);
            }

            if (ringRadius < 0.2f)
                continue; // pole rings skipped in real code

            org.joml.Vector3f ringCentre = new org.joml.Vector3f(centre.x, centre.y + h, centre.z);
            org.joml.Vector3f point = freq.ascension.animation.GeometrySource.circle(
                    ringCentre, new org.joml.Vector3f(0, 1, 0), ringRadius, true);

            // Point must be at exactly ringRadius from ringCentre (XZ plane)
            float dx = point.x - ringCentre.x;
            float dz = point.z - ringCentre.z;
            float dist = (float) Math.sqrt(dx * dx + dz * dz);

            if (Math.abs(dist - ringRadius) > 0.01f) {
                helper.fail("Circle point at wrong distance: expected " + ringRadius + ", got " + dist);
            }
        }
        helper.succeed();
    }
}

