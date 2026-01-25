package freq.ascension.mixin;

import freq.ascension.managers.AscensionData;
import freq.ascension.orders.Order;
import freq.ascension.registry.OrderRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

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
    private void saveAscensionData(ValueOutput output, CallbackInfo ci) {
        output.putInt("influence", this.influence);
        output.putString("rank", this.rank != null ? this.rank : "demigod");

        if (this.passive != null)
            output.putString("passive", this.passive);
        if (this.utility != null)
            output.putString("utility", this.utility);
        if (this.combat != null)
            output.putString("combat", this.combat);
        if (this.godOrder != null)
            output.putString("godOrder", this.godOrder);

        // Save the Map (Spell Bindings)
        StringBuilder bindings_sb = new StringBuilder();
        this.spell_bindings.forEach((slot, spellId) -> {
            bindings_sb.append(slot).append(":").append(spellId);
            // 0:id;2:id;
            if (bindings_sb.length() > 0)
                bindings_sb.append(";");
        });
        output.putString("spell_bindings", bindings_sb.toString());

        CompoundTag ordersTag = new CompoundTag();
        this.unlocked_orders.forEach((name, unlock) -> {
            CompoundTag detail = new CompoundTag();
            detail.putBoolean("passive", unlock.passive());
            detail.putBoolean("utility", unlock.utility());
            detail.putBoolean("combat", unlock.combat());
            ordersTag.put(name, detail);
        });

        StringBuilder orders_sb = new StringBuilder();
        this.unlocked_orders.forEach((name, unlock) -> {
            orders_sb.append(name + ":" + (unlock.hasPassive() ? "1" : "0") + "," + (unlock.hasUtility() ? "1" : "0")
                    + "," + (unlock.hasCombat() ? "1" : "0"));
            // order1:1,1,1;order2:0,1,1; <passive, utility, combat>
            if (orders_sb.length() > 0)
                orders_sb.append(";");
        });

        output.putString("unlocked_orders", orders_sb.toString());
    }

    // LOADING DATA
    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void readAscensionData(ValueInput input, CallbackInfo ci) {
        if (input.contains("ascension_data")) { // 10 is the type ID for CompoundTag
            this.influence = input.getInt("influence").orElse(0);
            this.rank = input.getString("rank").orElse("demigod");

            this.passive = input.getStringOr("passive", null);
            this.utility = input.getStringOr("utility", null);
            this.combat = input.getStringOr("combat", null);
            this.godOrder = input.getStringOr("godOrder", null);

            this.spell_bindings.clear();
            String encodedBindings = input.getStringOr("spell_bindings", "");
            if (!encodedBindings.isEmpty()) {
                String[] bindingsRaw = encodedBindings.split(";");
                for (String raw : bindingsRaw) {
                    String[] parts = raw.split(":");
                    if (parts.length == 2) {
                        try {
                            this.spell_bindings.put(Integer.parseInt(parts[0]), parts[1]);
                        } catch (NumberFormatException ignored) {
                            // Ignore invalid slots
                        }
                    }
                }
            }

            this.unlocked_orders.clear();
            String encodedOrders = input.getStringOr("unlocked_orders", "");
            if (!encodedOrders.isEmpty()) {
                String[] ordersRaw = encodedOrders.split(";");
                for (String e : ordersRaw) {
                    String[] parts = e.split(":");
                    if (parts.length == 2) {
                        String name = parts[0];
                        String[] flags = parts[1].split(",");
                        if (flags.length == 3) {
                            this.unlocked_orders.put(name, new OrderUnlock(
                                    "1".equals(flags[0]),
                                    "1".equals(flags[1]),
                                    "1".equals(flags[2])));
                        }
                    }
                }
            }
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
    public void bind(int slot, String spellId) {
        this.spell_bindings.put(slot, spellId);
    }

    @Override
    public void unbind(String spellId) {
        this.spell_bindings.values().remove(spellId);
    }

    @Override
    public void unbindAll() {
        this.spell_bindings.clear();
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
        return this.unlocked_orders.getOrDefault(order, OrderUnlock.EMPTY);
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
        java.util.List<Order> list = new java.util.ArrayList<>();
        if (getPassive() != null)
            list.add(getPassive());
        if (getUtility() != null)
            list.add(getUtility());
        if (getCombat() != null)
            list.add(getCombat());
        return list;
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