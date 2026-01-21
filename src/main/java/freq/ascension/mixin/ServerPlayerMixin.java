package freq.ascension.mixin;

import freq.ascension.managers.AscensionData;
import freq.ascension.orders.Order;
import freq.ascension.registry.OrderRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mixin(ServerPlayer.class)
public class ServerPlayerMixin implements AscensionData {
    @Unique
    private final Map<Integer, String> spell_bindings = new HashMap<>();
    @Unique
    private int influence = 0;
    @Unique
    private String rank = "demigod";
    @Unique
    private String passive = null;
    @Unique
    private String utility = null;
    @Unique
    private String combat = null;
    @Unique
    private String godOrder = null;
    @Unique
    private final Map<String, OrderUnlock> unlocked_orders = new HashMap<>();

    // Item drop broadcasting
    // @Inject(method =
    // "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;",
    // at = @At("HEAD"), cancellable = true)
    // private void onDrop(ItemStack stack, boolean throwRandomly, boolean
    // retainOwnership,
    // CallbackInfoReturnable<ItemEntity> cir) {
    // ServerPlayer player = (ServerPlayer) (Object) this;
    // AbilityManager.broadcast(player, (order) -> order.onItemDrop(player, stack));
    // // goes to mythic weapons later
    // }

    // SAVING DATA
    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void saveAscensionData(CompoundTag nbt, CallbackInfo ci) {
        CompoundTag ascensionTag = new CompoundTag();

        ascensionTag.putInt("influence", this.influence);
        ascensionTag.putString("rank", this.rank != null ? this.rank : "demigod");

        if (this.passive != null)
            ascensionTag.putString("passive", this.passive);
        if (this.utility != null)
            ascensionTag.putString("utility", this.utility);
        if (this.combat != null)
            ascensionTag.putString("combat", this.combat);
        if (this.godOrder != null)
            ascensionTag.putString("godOrder", this.godOrder);

        // Save the Map (Spell Bindings)
        CompoundTag spellsTag = new CompoundTag();
        this.spell_bindings.forEach((slot, spellId) -> {
            spellsTag.putString(slot.toString(), spellId);
        });
        ascensionTag.put("spell_bindings", spellsTag);

        CompoundTag ordersTag = new CompoundTag();
        this.unlocked_orders.forEach((name, unlock) -> {
            CompoundTag detail = new CompoundTag();
            detail.putBoolean("passive", unlock.passive());
            detail.putBoolean("utility", unlock.utility());
            detail.putBoolean("combat", unlock.combat());
            ordersTag.put(name, detail);
        });
        nbt.put("UnlockedOrders", ordersTag);

        nbt.put("ascension_data", ascensionTag);
    }

    // LOADING DATA
    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void readAscensionData(CompoundTag nbt, CallbackInfo ci) {
        if (nbt.contains("ascension_data")) { // 10 is the type ID for CompoundTag
            CompoundTag ascensionTag = nbt.getCompoundOrEmpty("ascension_data");

            this.influence = ascensionTag.getInt("influence").orElse(0);
            this.rank = ascensionTag.getString("rank").orElse("demigod");

            this.passive = ascensionTag.getString("passive").orElse(null);
            this.utility = ascensionTag.getString("utility").orElse(null);
            this.combat = ascensionTag.getString("combat").orElse(null);
            this.godOrder = ascensionTag.getString("godOrder").orElse(null);

            // Load the Map
            this.spell_bindings.clear();
            CompoundTag spellsTag = ascensionTag.getCompound("spell_bindings").orElse(new CompoundTag());
            spellsTag.forEach((String s, Tag t) -> {
                this.spell_bindings.put(Integer.parseInt(s), t.asString().orElse(""));
            });

            CompoundTag ordersTag = nbt.getCompound("UnlockedOrders").orElse(new CompoundTag());

            ordersTag.forEach((String s, Tag t) -> {
                CompoundTag detail = (CompoundTag) t;
                this.unlocked_orders.put(s, new OrderUnlock(
                        detail.getBoolean("passive").orElse(false),
                        detail.getBoolean("utility").orElse(false),
                        detail.getBoolean("combat").orElse(false)));
            });
        }
    }

    @Override
    public String getRank() {
        return this.rank;
    }

    @Override
    public void setRank(String rank) {
        this.rank = rank;
    }

    @Override
    public int getInfluence() {
        return this.influence;
    }

    @Override
    public void setInfluence(int amount) {
        this.influence = amount;
    }

    @Override
    public void addInfluence(int amount) {
        this.influence += amount;
    }

    @Override
    public Order getPassive() {
        return OrderRegistry.get(this.passive);
    }

    @Override
    public void setPassive(String order) {
        this.passive = order;
    }

    @Override
    public Order getUtility() {
        return OrderRegistry.get(this.utility);
    }

    @Override
    public void setUtility(String order) {
        this.utility = order;
    }

    @Override
    public Order getCombat() {
        return OrderRegistry.get(this.combat);
    }

    @Override
    public void setCombat(String order) {
        this.combat = order;
    }

    @Override
    public Map<Integer, String> getSpellBindings() {
        return this.spell_bindings;
    }

    @Override
    public String getGodOrder() {
        return this.godOrder;
    }

    @Override
    public void setGodOrder(String order) {
        this.godOrder = order;
    }

    @Override
    public Map<String, OrderUnlock> getUnlocked() {
        return this.unlocked_orders;
    }

    @Override
    public OrderUnlock getUnlockedOrder(String order) {
        return this.unlocked_orders.get(order);
    }

    @Override
    public void unlock(String order, String type) {
        // 1. Get the current state or a blank one if they've never touched this order
        OrderUnlock current = this.unlocked_orders.getOrDefault(order, OrderUnlock.EMPTY);

        // 2. Determine the new state
        OrderUnlock updated = switch (type.toLowerCase()) {
            case "passive" -> new OrderUnlock(true, current.utility(), current.combat());
            case "utility" -> new OrderUnlock(current.passive(), true, current.combat());
            case "combat" -> new OrderUnlock(current.passive(), current.utility(), true);
            default -> current; // No change if type is invalid
        };

        // 3. Put it back in the map (replaces the old record)
        this.unlocked_orders.put(order, updated);
    }

    // Helper methods
    /**
     * Returns the player's equipped orders in passive/utility/combat order.
     * The returned list is cached and only recomputed when abilities (or rank)
     * change. Duplicates are removed (if the same order is in multiple slots).
     */
    @Override
    public List<Order> getEquippedOrders() {
        return List.of(getPassive(), getUtility(), getCombat());
    }
}

/*
 * Root NBT Tag (player.dat file)
 * ├── ... (other player data)
 * └── ascension_data: CompoundTag
 * ├── influence: Int (e.g., 0)
 * ├── rank: String (e.g., "demigod")
 * ├── passive: String (e.g., "some_passive_spell") [optional]
 * ├── utility: String (e.g., "some_utility_spell") [optional]
 * ├── combat: String (e.g., "some_combat_spell") [optional]
 * ├── godOrder: String (e.g., "zeus") [optional]
 * └── spell_bindings: CompoundTag
 * -├── "1": String (e.g., "fireball")
 * -├── "2": String (e.g., "heal")
 * -└── "9": String (e.g., "teleport")
 */