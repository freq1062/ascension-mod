package freq.ascension.test.god;

import java.util.List;

import freq.ascension.orders.Earth;
import freq.ascension.orders.Sky;
import freq.ascension.orders.Order;
import freq.ascension.registry.WeaponRegistry;
import freq.ascension.weapons.MythicWeapon;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.util.Unit;

/**
 * GameTest suite for the {@link MythicWeapon} interface contract and {@link WeaponRegistry}
 * scaffold behaviour.
 *
 * <p>All tests use an inner {@code MockMythicWeapon} implementation so no concrete weapon class
 * needs to exist at this stage of development.
 *
 * <p><b>Invariants verified:</b>
 * <ul>
 *   <li>{@link MythicWeapon#getWeaponId()} is non-null and non-empty.</li>
 *   <li>{@link MythicWeapon#getBaseItem()} is non-null.</li>
 *   <li>{@link MythicWeapon#getParentOrder()} is non-null.</li>
 *   <li>{@link MythicWeapon#createItem()} produces a stack with the correct custom model data.</li>
 *   <li>{@link MythicWeapon#buildBaseItem()} produces an unbreakable stack with the weapon id
 *       in the custom model data strings list.</li>
 *   <li>{@link MythicWeapon#isItem(ItemStack)} identifies the weapon's own stack correctly
 *       and rejects unrelated items.</li>
 *   <li>{@link MythicWeapon#formatWeaponName(String)} converts snake_case IDs to Title Case.</li>
 *   <li>{@link WeaponRegistry} returns {@code null} for unregistered orders and accepts
 *       registration without crashing.</li>
 * </ul>
 */
public class GodMythicWeaponTests {

    // ─────────────────────────────────────────────────────────────────────────
    // Mock weapon — implements MythicWeapon with a diamond sword base
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Minimal in-test implementation of {@link MythicWeapon} for contract verification.
     * Uses Earth as the parent order and a diamond sword as the base item.
     */
    private static final class MockMythicWeapon implements MythicWeapon {

        static final MockMythicWeapon INSTANCE = new MockMythicWeapon();

        @Override
        public String getWeaponId() {
            return "test_blade";
        }

        @Override
        public Item getBaseItem() {
            return Items.DIAMOND_SWORD;
        }

        @Override
        public Order getParentOrder() {
            return Earth.INSTANCE;
        }

        @Override
        public ItemStack createItem() {
            return buildBaseItem(); // delegates to default — sufficient for testing
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Identity contract
    // ─────────────────────────────────────────────────────────────────────────

    /** {@link MythicWeapon#getWeaponId()} must never return null or an empty string. */
    @GameTest
    public void weaponIdIsNotNullOrEmpty(GameTestHelper helper) {
        String id = MockMythicWeapon.INSTANCE.getWeaponId();
        if (id == null || id.isEmpty()) {
            helper.fail("getWeaponId() must return a non-null, non-empty string, got: " + id);
        }
        helper.succeed();
    }

    /** {@link MythicWeapon#getBaseItem()} must never return null. */
    @GameTest
    public void baseItemIsNotNull(GameTestHelper helper) {
        if (MockMythicWeapon.INSTANCE.getBaseItem() == null) {
            helper.fail("getBaseItem() must not return null");
        }
        helper.succeed();
    }

    /** {@link MythicWeapon#getParentOrder()} must never return null. */
    @GameTest
    public void parentOrderIsNotNull(GameTestHelper helper) {
        if (MockMythicWeapon.INSTANCE.getParentOrder() == null) {
            helper.fail("getParentOrder() must not return null");
        }
        helper.succeed();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // createItem / buildBaseItem contract
    // ─────────────────────────────────────────────────────────────────────────

    /** {@link MythicWeapon#createItem()} must return a non-null, non-empty stack. */
    @GameTest
    public void createItemReturnsNonNullStack(GameTestHelper helper) {
        ItemStack stack = MockMythicWeapon.INSTANCE.createItem();
        if (stack == null || stack.isEmpty()) {
            helper.fail("createItem() must return a non-empty ItemStack");
        }
        helper.succeed();
    }

    /**
     * The stack from {@link MythicWeapon#createItem()} must use the weapon's base item type.
     */
    @GameTest
    public void createItemUsesBaseItemType(GameTestHelper helper) {
        ItemStack stack = MockMythicWeapon.INSTANCE.createItem();
        if (stack.getItem() != MockMythicWeapon.INSTANCE.getBaseItem()) {
            helper.fail("createItem() must use getBaseItem() as the item type, got "
                    + stack.getItem());
        }
        helper.succeed();
    }

    /**
     * The stack from {@link MythicWeapon#buildBaseItem()} must have a
     * {@code CustomModelData} component whose strings list contains the weapon id.
     */
    @GameTest
    public void buildBaseItemHasCustomModelDataString(GameTestHelper helper) {
        ItemStack stack = MockMythicWeapon.INSTANCE.buildBaseItem();
        CustomModelData cmd = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        if (cmd == null) {
            helper.fail("buildBaseItem() must set DataComponents.CUSTOM_MODEL_DATA");
        }
        String weaponId = MockMythicWeapon.INSTANCE.getWeaponId();
        if (!cmd.strings().contains(weaponId)) {
            helper.fail("CustomModelData strings must contain \"" + weaponId
                    + "\" but got: " + cmd.strings());
        }
        helper.succeed();
    }

    /**
     * The stack from {@link MythicWeapon#buildBaseItem()} must have the
     * {@code Unbreakable} component set.
     */
    @GameTest
    public void buildBaseItemIsUnbreakable(GameTestHelper helper) {
        ItemStack stack = MockMythicWeapon.INSTANCE.buildBaseItem();
        Unit unbreakable = stack.get(DataComponents.UNBREAKABLE);
        if (unbreakable == null) {
            helper.fail("buildBaseItem() must set DataComponents.UNBREAKABLE");
        }
        helper.succeed();
    }

    /**
     * The {@code CustomModelData} component's strings list must have exactly one entry.
     * Multiple IDs would allow item confusion attacks.
     */
    @GameTest
    public void buildBaseItemHasExactlyOneModelDataString(GameTestHelper helper) {
        ItemStack stack = MockMythicWeapon.INSTANCE.buildBaseItem();
        CustomModelData cmd = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        if (cmd == null) {
            helper.fail("buildBaseItem() must set DataComponents.CUSTOM_MODEL_DATA");
        }
        List<String> strings = cmd.strings();
        if (strings.size() != 1) {
            helper.fail("CustomModelData strings list must have exactly 1 entry but had "
                    + strings.size() + ": " + strings);
        }
        helper.succeed();
    }

    /**
     * The stack from {@link MythicWeapon#buildBaseItem()} must have a custom name component set.
     */
    @GameTest
    public void buildBaseItemHasCustomName(GameTestHelper helper) {
        ItemStack stack = MockMythicWeapon.INSTANCE.buildBaseItem();
        if (stack.get(DataComponents.CUSTOM_NAME) == null) {
            helper.fail("buildBaseItem() must set DataComponents.CUSTOM_NAME");
        }
        helper.succeed();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // isItem contract
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * {@link MythicWeapon#isItem(ItemStack)} must return {@code true} when passed the
     * weapon's own stack produced by {@link MythicWeapon#createItem()}.
     */
    @GameTest
    public void isItemReturnsTrueForOwnStack(GameTestHelper helper) {
        ItemStack own = MockMythicWeapon.INSTANCE.createItem();
        if (!MockMythicWeapon.INSTANCE.isItem(own)) {
            helper.fail("isItem() must return true for the weapon's own createItem() stack");
        }
        helper.succeed();
    }

    /**
     * {@link MythicWeapon#isItem(ItemStack)} must return {@code false} for a plain stick.
     */
    @GameTest
    public void isItemReturnsFalseForUnrelatedItem(GameTestHelper helper) {
        ItemStack stick = new ItemStack(Items.STICK);
        if (MockMythicWeapon.INSTANCE.isItem(stick)) {
            helper.fail("isItem() must return false for an unrelated ItemStack");
        }
        helper.succeed();
    }

    /**
     * {@link MythicWeapon#isItem(ItemStack)} must return {@code false} for an empty stack.
     */
    @GameTest
    public void isItemReturnsFalseForEmptyStack(GameTestHelper helper) {
        if (MockMythicWeapon.INSTANCE.isItem(ItemStack.EMPTY)) {
            helper.fail("isItem() must return false for ItemStack.EMPTY");
        }
        helper.succeed();
    }

    /**
     * {@link MythicWeapon#isItem(ItemStack)} must return {@code false} for {@code null}.
     */
    @GameTest
    public void isItemReturnsFalseForNull(GameTestHelper helper) {
        if (MockMythicWeapon.INSTANCE.isItem(null)) {
            helper.fail("isItem() must return false for null");
        }
        helper.succeed();
    }

    /**
     * Two distinct weapon instances with different IDs must not consider each other's items as
     * their own.
     */
    @GameTest
    public void isItemReturnsFalseForOtherMythicWeapon(GameTestHelper helper) {
        // Build a stack with a different weapon id
        ItemStack other = new ItemStack(Items.TRIDENT);
        other.set(DataComponents.CUSTOM_MODEL_DATA,
                new CustomModelData(List.of(), List.of(), List.of("other_weapon"), List.of()));

        if (MockMythicWeapon.INSTANCE.isItem(other)) {
            helper.fail("isItem() must return false for a stack with a different weapon id");
        }
        helper.succeed();
    }

    /**
     * A stack that has the correct item type but no {@code CustomModelData} component must
     * not be accepted as the mythical weapon.
     */
    @GameTest
    public void isItemReturnsFalseForSameTypeWithoutModelData(GameTestHelper helper) {
        // Same item type as MockMythicWeapon but no model data
        ItemStack plain = new ItemStack(Items.DIAMOND_SWORD);
        if (MockMythicWeapon.INSTANCE.isItem(plain)) {
            helper.fail("isItem() must return false if CustomModelData is absent");
        }
        helper.succeed();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // formatWeaponName helper
    // ─────────────────────────────────────────────────────────────────────────

    /** Snake_case with two words becomes two-word Title Case. */
    @GameTest
    public void formatWeaponNameTwoWords(GameTestHelper helper) {
        String result = MythicWeapon.formatWeaponName("tempest_trident");
        if (!"Tempest Trident".equals(result)) {
            helper.fail("Expected \"Tempest Trident\" but got \"" + result + "\"");
        }
        helper.succeed();
    }

    /** A single-word id is capitalised correctly. */
    @GameTest
    public void formatWeaponNameSingleWord(GameTestHelper helper) {
        String result = MythicWeapon.formatWeaponName("excalibur");
        if (!"Excalibur".equals(result)) {
            helper.fail("Expected \"Excalibur\" but got \"" + result + "\"");
        }
        helper.succeed();
    }

    /** An empty string input produces an empty string (no exception). */
    @GameTest
    public void formatWeaponNameEmptyString(GameTestHelper helper) {
        String result = MythicWeapon.formatWeaponName("");
        if (result == null || !result.isEmpty()) {
            helper.fail("Expected empty string but got \"" + result + "\"");
        }
        helper.succeed();
    }

    /** A three-word id is fully converted. */
    @GameTest
    public void formatWeaponNameThreeWords(GameTestHelper helper) {
        String result = MythicWeapon.formatWeaponName("staff_of_chaos");
        if (!"Staff Of Chaos".equals(result)) {
            helper.fail("Expected \"Staff Of Chaos\" but got \"" + result + "\"");
        }
        helper.succeed();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // WeaponRegistry contract — pre-registration state
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * {@link WeaponRegistry#getForOrder(String)} must return {@code null} for the End order,
     * as no weapon is registered (and none should ever be, since there is no god of the End).
     */
    @GameTest
    public void weaponRegistryGetForOrderEndIsNull(GameTestHelper helper) {
        MythicWeapon weapon = WeaponRegistry.getForOrder("end");
        if (weapon != null) {
            helper.fail("WeaponRegistry.getForOrder(\"end\") must be null (End has no god tier)");
        }
        helper.succeed();
    }

    /**
     * {@link WeaponRegistry#getForOrder(String)} must return {@code null} for an order name
     * that has never been registered.
     */
    @GameTest
    public void weaponRegistryGetForUnregisteredOrderIsNull(GameTestHelper helper) {
        MythicWeapon weapon = WeaponRegistry.getForOrder("__definitely_not_an_order__");
        if (weapon != null) {
            helper.fail("WeaponRegistry.getForOrder() must return null for unknown order names");
        }
        helper.succeed();
    }

    /**
     * {@link WeaponRegistry#isMythicalWeapon(ItemStack)} must return {@code false} for a plain
     * item when no weapons are registered (or the item is plain).
     */
    @GameTest
    public void weaponRegistryIsMythicalWeaponFalseForPlainItem(GameTestHelper helper) {
        ItemStack plain = new ItemStack(Items.STICK);
        if (WeaponRegistry.isMythicalWeapon(plain)) {
            helper.fail("isMythicalWeapon(stick) must return false");
        }
        helper.succeed();
    }

    /**
     * {@link WeaponRegistry#isMythicalWeapon(ItemStack)} must return {@code false} for
     * {@code ItemStack.EMPTY}.
     */
    @GameTest
    public void weaponRegistryIsMythicalWeaponFalseForEmpty(GameTestHelper helper) {
        if (WeaponRegistry.isMythicalWeapon(ItemStack.EMPTY)) {
            helper.fail("isMythicalWeapon(ItemStack.EMPTY) must return false");
        }
        helper.succeed();
    }

    /**
     * After registering a weapon, {@link WeaponRegistry#get(String)} with the weapon's id must
     * return that weapon. After the test, the registration state is left as-is (acceptable because
     * game tests run in isolated JVM contexts per suite).
     */
    @GameTest
    public void weaponRegistryGetByIdAfterRegistration(GameTestHelper helper) {
        // Use a unique id to avoid collision with other tests in the same JVM run
        final class UniqueMockWeapon implements MythicWeapon {
            @Override public String getWeaponId()  { return "unique_test_dagger_xyz"; }
            @Override public Item   getBaseItem()  { return Items.GOLDEN_SWORD; }
            @Override public Order  getParentOrder(){ return Earth.INSTANCE; }
            @Override public ItemStack createItem(){ return buildBaseItem(); }
        }

        UniqueMockWeapon mock = new UniqueMockWeapon();
        try {
            WeaponRegistry.register(mock);
        } catch (IllegalArgumentException e) {
            // Already registered from a previous run — acceptable
        }

        MythicWeapon found = WeaponRegistry.get("unique_test_dagger_xyz");
        if (found == null) {
            helper.fail("WeaponRegistry.get(\"unique_test_dagger_xyz\") returned null after registration");
        }
        helper.succeed();
    }

    /**
     * After registering a weapon, {@link WeaponRegistry#isMythicalWeapon(ItemStack)} must
     * return {@code true} for the weapon's own stack.
     */
    @GameTest
    public void weaponRegistryIsMythicalWeaponTrueAfterRegistration(GameTestHelper helper) {
        // Use a unique id registered under Sky order to avoid colliding with the Earth
        // weapon registered by the previous test (WeaponRegistry uses a static map).
        final String WEAPON_ID = "sky_test_blade_xyz";
        final class SkyMockWeapon implements MythicWeapon {
            @Override public String getWeaponId()  { return WEAPON_ID; }
            @Override public Item   getBaseItem()  { return Items.IRON_SWORD; }
            @Override public Order  getParentOrder(){ return Sky.INSTANCE; }
            @Override public ItemStack createItem(){ return buildBaseItem(); }
        }

        try {
            WeaponRegistry.register(new SkyMockWeapon());
        } catch (IllegalArgumentException e) {
            // Already registered from a prior run in the same JVM — acceptable
        }

        // Build a stack with the correct model data string and verify isMythicalWeapon recognises it
        ItemStack stack = new ItemStack(Items.IRON_SWORD);
        stack.set(DataComponents.CUSTOM_MODEL_DATA,
                new CustomModelData(List.of(), List.of(), List.of(WEAPON_ID), List.of()));

        if (!WeaponRegistry.isMythicalWeapon(stack)) {
            helper.fail("isMythicalWeapon() must return true for a stack matching a registered weapon id (\"" + WEAPON_ID + "\")");
        }
        helper.succeed();
    }
}
