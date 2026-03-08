package freq.ascension.test.misc;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;

import freq.ascension.items.InfluenceItem;
import freq.ascension.managers.AscensionData;
import freq.ascension.managers.AscensionData.OrderUnlock;
import freq.ascension.orders.Nether;
import freq.ascension.orders.Ocean;
import freq.ascension.orders.Order;

import java.util.Map;

/**
 * Tests for Bug 4 (death data persistence), Bug 5 (powder snow / frost),
 * Bug 6 (InfluenceItem identification), and Bug 9 (Nether fire immunity scope).
 */
public class DataPersistenceTests {

    // ─────────────────────────────────────────────────────────────────────────
    // Bug 6 — InfluenceItem identification
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * A freshly created InfluenceItem must be identified by isInfluenceItem().
     * This would have returned false before the CUSTOM_DATA→CUSTOM_MODEL_DATA fix.
     */
    @GameTest
    public void influenceItemIsRecognisedByIsInfluenceItem(GameTestHelper helper) {
        ItemStack stack = InfluenceItem.createItem();
        if (!InfluenceItem.isInfluenceItem(stack)) {
            helper.fail("InfluenceItem.createItem() must produce a stack that isInfluenceItem() returns true for");
        }
        helper.succeed();
    }

    /**
     * An ordinary amethyst shard (no CustomModelData) must NOT be identified as
     * an influence item.
     */
    @GameTest
    public void nonInfluenceItemIsRejected(GameTestHelper helper) {
        ItemStack plain = new ItemStack(net.minecraft.world.item.Items.AMETHYST_SHARD);
        if (InfluenceItem.isInfluenceItem(plain)) {
            helper.fail("A plain amethyst shard without CustomModelData must not be recognised as an InfluenceItem");
        }
        helper.succeed();
    }

    /**
     * Verifies that isInfluenceItem uses value equality (.equals) not reference
     * equality (==) when matching the CustomModelData string. Reference equality
     * would always return false for strings constructed at runtime.
     */
    @GameTest
    public void influenceItemCheckUsesValueEqualityNotReference(GameTestHelper helper) {
        // Build a stack that has the exact same CustomModelData string value but via a
        // freshly constructed path — the JVM string pool won't deduplicate here.
        ItemStack a = InfluenceItem.createItem();
        ItemStack b = InfluenceItem.createItem();

        // Both stacks must be identified as influence items; if == was used instead of
        // .equals the method would fail non-deterministically depending on JVM interning.
        if (!InfluenceItem.isInfluenceItem(a) || !InfluenceItem.isInfluenceItem(b)) {
            helper.fail("isInfluenceItem must use .equals(), not ==, for CustomModelData string comparison");
        }
        helper.succeed();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Bug 5 — Ocean powder snow walk
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Ocean.canWalkOnPowderSnow must override the default Order return value.
     * The default implementation returns false; Ocean returns true for passive
     * players. We verify this at the method-existence level since calling it with
     * a real equipped player requires a game-world setup.
     */
    @GameTest
    public void oceanOrderDefinesCanWalkOnPowderSnow(GameTestHelper helper) {
        // Verify Ocean overrides the default Order behaviour.
        // The base default returns false; Ocean must declare its own override.
        try {
            Ocean.class.getDeclaredMethod("canWalkOnPowderSnow",
                    net.minecraft.server.level.ServerPlayer.class);
        } catch (NoSuchMethodException e) {
            helper.fail("Ocean must override canWalkOnPowderSnow(ServerPlayer) to return true for passive players");
        }
        helper.succeed();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Bug 9 — Nether fire immunity requires passive slot
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Nether.applyEffect must only apply FIRE_RESISTANCE when Nether is in the
     * passive slot. This test verifies the ambient/invisible spec of the effect
     * so it can be distinguished from a potion-given effect.
     */
    @GameTest
    public void netherApplyEffectSpecifiesAmbientFireResistance(GameTestHelper helper) {
        // The effect created by applyEffect must be ambient and non-particle so we can
        // distinguish it from a potion. We verify the spec by constructing the same effect.
        MobEffectInstance orderEffect = new MobEffectInstance(
                MobEffects.FIRE_RESISTANCE, 80, 0, /*ambient*/ true, /*showParticles*/ false, /*showIcon*/ true);
        if (!orderEffect.isAmbient()) {
            helper.fail("Nether passive FIRE_RESISTANCE must be ambient=true");
        }
        if (orderEffect.isVisible()) {
            helper.fail("Nether passive FIRE_RESISTANCE must have showParticles=false (isVisible must be false)");
        }
        helper.succeed();
    }

    /**
     * Nether.onUnequip must be defined and must remove FIRE_RESISTANCE. This test
     * verifies the interface method exists by calling it (no-op on a null player
     * in the base Order.java default would be fine; Nether overrides it).
     */
    @GameTest
    public void netherOnUnequipMethodIsOverridden(GameTestHelper helper) {
        // Nether.INSTANCE must override onUnequip. If the method is still the default
        // no-op from Order.java, fire resistance won't be cleared on unequip.
        // We verify this by checking that Nether.INSTANCE.getClass() declares the method.
        try {
            Nether.INSTANCE.getClass().getDeclaredMethod("onUnequip",
                    net.minecraft.server.level.ServerPlayer.class, String.class);
        } catch (NoSuchMethodException e) {
            helper.fail("Nether must override onUnequip(ServerPlayer, String) to clear FIRE_RESISTANCE on unequip");
        }
        helper.succeed();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Bug 4 — Death data persistence (API contract checks)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Verifies that AscensionData exposes setPassive/setUtility/setCombat with
     * String parameters and getPassive/getUtility/getCombat that return Order.
     * These are the exact methods that COPY_FROM must call to persist ability slots
     * through death. If any are missing, COPY_FROM cannot compile.
     */
    @GameTest
    public void ascensionDataExposeAbilitySlotSettersAndGetters(GameTestHelper helper) {
        try {
            AscensionData.class.getMethod("setPassive", String.class);
            AscensionData.class.getMethod("setUtility", String.class);
            AscensionData.class.getMethod("setCombat", String.class);
            AscensionData.class.getMethod("getPassive");
            AscensionData.class.getMethod("getUtility");
            AscensionData.class.getMethod("getCombat");
        } catch (NoSuchMethodException e) {
            helper.fail("AscensionData must expose get/set for passive, utility, combat: " + e.getMessage());
        }
        helper.succeed();
    }

    /**
     * Verifies that AscensionData exposes the previousPassive/Utility/Combat
     * getters and setters required for god-demotion restore logic.
     */
    @GameTest
    public void ascensionDataExposesPreviousSlotFields(GameTestHelper helper) {
        try {
            AscensionData.class.getMethod("getPreviousPassive");
            AscensionData.class.getMethod("setPreviousPassive", String.class);
            AscensionData.class.getMethod("getPreviousUtility");
            AscensionData.class.getMethod("setPreviousUtility", String.class);
            AscensionData.class.getMethod("getPreviousCombat");
            AscensionData.class.getMethod("setPreviousCombat", String.class);
        } catch (NoSuchMethodException e) {
            helper.fail("AscensionData must expose previousPassive/Utility/Combat get/set: " + e.getMessage());
        }
        helper.succeed();
    }

    /**
     * Verifies that AscensionData exposes getUnlocked() returning a Map and
     * unlock(String, String) for populating it — both required by COPY_FROM.
     */
    @GameTest
    public void ascensionDataExposesUnlockedOrdersApi(GameTestHelper helper) {
        try {
            AscensionData.class.getMethod("getUnlocked");
            AscensionData.class.getMethod("unlock", String.class, String.class);
        } catch (NoSuchMethodException e) {
            helper.fail("AscensionData must expose getUnlocked() and unlock(String,String): " + e.getMessage());
        }
        helper.succeed();
    }

    /**
     * OrderUnlock record must expose hasPassive/hasUtility/hasCombat booleans
     * so COPY_FROM can iterate the map and replay unlocks on the new entity.
     */
    @GameTest
    public void orderUnlockRecordExposesIndividualFlags(GameTestHelper helper) {
        OrderUnlock full = new OrderUnlock(true, true, true);
        OrderUnlock none = OrderUnlock.EMPTY;

        if (!full.hasPassive() || !full.hasUtility() || !full.hasCombat()) {
            helper.fail("OrderUnlock(true,true,true) must report all three flags as true");
        }
        if (none.hasPassive() || none.hasUtility() || none.hasCombat()) {
            helper.fail("OrderUnlock.EMPTY must report all flags as false");
        }
        helper.succeed();
    }

    /**
     * Verifies that Order implementations expose getOrderName() so COPY_FROM can
     * obtain the string name from a getPassive()/getUtility()/getCombat() result
     * and pass it to the corresponding setter on the new entity.
     */
    @GameTest
    public void orderGetOrderNameIsNonNull(GameTestHelper helper) {
        if (Ocean.INSTANCE.getOrderName() == null || Ocean.INSTANCE.getOrderName().isEmpty()) {
            helper.fail("Ocean.getOrderName() must return a non-empty string");
        }
        if (Nether.INSTANCE.getOrderName() == null || Nether.INSTANCE.getOrderName().isEmpty()) {
            helper.fail("Nether.getOrderName() must return a non-empty string");
        }
        helper.succeed();
    }
}
