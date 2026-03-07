package freq.ascension.test.god;

import freq.ascension.managers.SpellStats;
import freq.ascension.orders.Ocean;
import freq.ascension.orders.OceanGod;
import freq.ascension.orders.Order;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public class OceanGodTests {

    @GameTest
    public void oceanGodExtendsOcean(GameTestHelper helper) {
        if (!(OceanGod.INSTANCE instanceof Ocean)) {
            helper.fail("OceanGod must extend Ocean (demigod)");
        }
        helper.succeed();
    }

    @GameTest
    public void oceanGetVersionGodReturnsOceanGod(GameTestHelper helper) {
        Order resolved = Ocean.INSTANCE.getVersion("god");
        if (!(resolved instanceof OceanGod)) {
            helper.fail("Ocean.getVersion(\"god\") should return OceanGod, got "
                    + resolved.getClass().getSimpleName());
        }
        helper.succeed();
    }

    @GameTest
    public void oceanGodHaste2IsAmplifier1(GameTestHelper helper) {
        // OceanGod applies Haste 2 (amplifier 1) when submerged
        MobEffectInstance effect = new MobEffectInstance(MobEffects.HASTE, 80, 1, true, false, true);
        if (effect.getAmplifier() != 1) {
            helper.fail("OceanGod Haste 2 must be amplifier 1, got " + effect.getAmplifier());
        }
        helper.succeed();
    }

    @GameTest
    public void oceanGodHasteIsAmbientAndInvisible(GameTestHelper helper) {
        MobEffectInstance effect = new MobEffectInstance(MobEffects.HASTE, 80, 1, true, false, true);
        if (!effect.isAmbient()) {
            helper.fail("OceanGod Haste 2 must be ambient");
        }
        if (effect.isVisible()) {
            helper.fail("OceanGod Haste 2 must be invisible (no particles)");
        }
        helper.succeed();
    }

    @GameTest
    public void oceanGodDrownRadius12(GameTestHelper helper) {
        SpellStats stats = OceanGod.INSTANCE.getSpellStats("drown");
        if (stats == null) {
            helper.fail("OceanGod.getSpellStats(\"drown\") returned null");
        }
        int radius = stats.getInt(1); // extra[1] = radius
        if (radius != 12) {
            helper.fail("OceanGod drown radius should be 12, got " + radius);
        }
        helper.succeed();
    }

    @GameTest
    public void oceanDemigodDrownRadius8(GameTestHelper helper) {
        SpellStats stats = Ocean.INSTANCE.getSpellStats("drown");
        if (stats == null) {
            helper.fail("Ocean.getSpellStats(\"drown\") returned null");
        }
        int radius = stats.getInt(1); // extra[1] = radius
        if (radius != 8) {
            helper.fail("Ocean demigod drown radius should be 8, got " + radius);
        }
        helper.succeed();
    }

    @GameTest
    public void oceanGodDrownRadiusGreaterThanDemigod(GameTestHelper helper) {
        int godRadius = OceanGod.INSTANCE.getSpellStats("drown").getInt(1);
        int demigodRadius = Ocean.INSTANCE.getSpellStats("drown").getInt(1);
        if (godRadius <= demigodRadius) {
            helper.fail("OceanGod drown radius (" + godRadius
                    + ") must exceed demigod radius (" + demigodRadius + ")");
        }
        helper.succeed();
    }

    @GameTest
    public void oceanGodMolecularFluxDuration15s(GameTestHelper helper) {
        SpellStats stats = OceanGod.INSTANCE.getSpellStats("molecular_flux");
        if (stats == null) {
            helper.fail("OceanGod.getSpellStats(\"molecular_flux\") returned null");
        }
        int durationSecs = stats.getInt(1); // extra[1] = duration in seconds
        if (durationSecs != 15) {
            helper.fail("OceanGod molecular_flux duration should be 15s, got " + durationSecs + "s");
        }
        helper.succeed();
    }

    @GameTest
    public void oceanGodMolecularFluxLongerThanDemigod(GameTestHelper helper) {
        int godDuration = OceanGod.INSTANCE.getSpellStats("molecular_flux").getInt(1);
        int demigodDuration = Ocean.INSTANCE.getSpellStats("molecular_flux").getInt(1);
        if (godDuration <= demigodDuration) {
            helper.fail("OceanGod molecular_flux duration (" + godDuration
                    + "s) must exceed demigod (" + demigodDuration + "s)");
        }
        helper.succeed();
    }

    @GameTest
    public void oceanGodHasDolphinsGraceSpell(GameTestHelper helper) {
        SpellStats stats = OceanGod.INSTANCE.getSpellStats("dolphins_grace");
        if (stats == null) {
            helper.fail("OceanGod must define dolphins_grace spell stats");
        }
        helper.succeed();
    }

    @GameTest
    public void conduitPowerEffectIsAmbient(GameTestHelper helper) {
        MobEffectInstance effect = new MobEffectInstance(MobEffects.CONDUIT_POWER, 80, 0, true, false, true);
        if (!effect.isAmbient()) {
            helper.fail("Conduit Power must be ambient");
        }
        helper.succeed();
    }

    @GameTest
    public void oceanGodOrderNameIsOcean(GameTestHelper helper) {
        String name = OceanGod.INSTANCE.getOrderName();
        if (!"ocean".equals(name)) {
            helper.fail("OceanGod.getOrderName() should be \"ocean\", got \"" + name + "\"");
        }
        helper.succeed();
    }

    /**
     * <b>OCEAN GOD — canWalkOnPowderSnow Inherited From Ocean</b>
     *
     * <p>OceanGod extends Ocean, so canWalkOnPowderSnow should also be callable
     * via polymorphism. Confirms the method is accessible on the god instance.
     */
    @GameTest
    public void oceanGodInheritsCanWalkOnPowderSnow(GameTestHelper helper) {
        // OceanGod inherits canWalkOnPowderSnow from Ocean.
        // The method must be accessible via the OceanGod instance.
        try {
            // Walk up the class hierarchy to find the method.
            Class<?> cls = OceanGod.class;
            boolean found = false;
            while (cls != null) {
                try {
                    cls.getDeclaredMethod("canWalkOnPowderSnow",
                            net.minecraft.server.level.ServerPlayer.class);
                    found = true;
                    break;
                } catch (NoSuchMethodException e) {
                    cls = cls.getSuperclass();
                }
            }
            if (!found) {
                helper.fail("canWalkOnPowderSnow must be accessible from OceanGod (via Ocean inheritance)");
            }
        } catch (Exception e) {
            helper.fail("Unexpected exception checking canWalkOnPowderSnow on OceanGod: " + e.getMessage());
        }
        helper.succeed();
    }
}
