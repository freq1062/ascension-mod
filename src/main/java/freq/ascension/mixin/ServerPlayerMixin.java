package freq.ascension.mixin;

import freq.ascension.Ascension;
import freq.ascension.managers.AscensionData;
import freq.ascension.managers.Spell;
import freq.ascension.managers.SpellCooldownManager;
import freq.ascension.orders.Order;
import freq.ascension.registry.OrderRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.network.chat.Component;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

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
    private String previousPassive = "";
    @Unique
    private String previousUtility = "";
    @Unique
    private String previousCombat = "";
    @Unique
    private final Map<String, OrderUnlock> unlocked_orders = new HashMap<>();
    @Unique
    private final List<String> shapeshift_history = new ArrayList<>();
    @Unique
    private static final int SHAPESHIFT_HISTORY_MAX = 5;

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

    // Guard against NPE when vanilla PlayerTrigger fires during ServerPlayer.tick()
    // for GameTest fake players that have connection == null (no network link).
    // No Fabric API hook exists to intercept advancement reward packet sending.
    @Inject(method = "awardRecipes", at = @At("HEAD"), cancellable = true)
    private void ascension$guardNullConnectionOnAward(Collection<?> recipes, CallbackInfoReturnable<?> ci) {
        if (((ServerPlayer) (Object) this).connection == null) {
            ci.cancel();
        }
    }

    // SAVING DATA
    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void saveAscensionData(ValueOutput output, CallbackInfo ci) {
        ValueOutput ascensionData = output.child("ascension_data");

        ascensionData.putInt("influence", this.influence);
        ascensionData.putString("rank", this.rank != null ? this.rank : "demigod");

        if (this.passive != null)
            ascensionData.putString("passive", this.passive);
        if (this.utility != null)
            ascensionData.putString("utility", this.utility);
        if (this.combat != null)
            ascensionData.putString("combat", this.combat);
        if (this.godOrder != null)
            ascensionData.putString("godOrder", this.godOrder);
        ascensionData.putString("previousPassive", this.previousPassive != null ? this.previousPassive : "");
        ascensionData.putString("previousUtility", this.previousUtility != null ? this.previousUtility : "");
        ascensionData.putString("previousCombat", this.previousCombat != null ? this.previousCombat : "");

        // Save the Map (Spell Bindings)
        StringBuilder bindings_sb = new StringBuilder();
        this.spell_bindings.forEach((slot, spellId) -> {
            bindings_sb.append(slot).append(":").append(spellId);
            // 0:id;2:id;
            if (bindings_sb.length() > 0)
                bindings_sb.append(";");
        });
        ascensionData.putString("spell_bindings", bindings_sb.toString());

        StringBuilder orders_sb = new StringBuilder();
        this.unlocked_orders.forEach((name, unlock) -> {
            orders_sb.append(name + ":" + (unlock.hasPassive() ? "1" : "0") + "," + (unlock.hasUtility() ? "1" : "0")
                    + "," + (unlock.hasCombat() ? "1" : "0"));
            // order1:1,1,1;order2:0,1,1; <passive, utility, combat>
            if (orders_sb.length() > 0)
                orders_sb.append(";");
        });

        ascensionData.putString("unlocked_orders", orders_sb.toString());

        // Shapeshift history (stack of entity type ids, semicolon-separated)
        if (!this.shapeshift_history.isEmpty()) {
            ascensionData.putString("shapeshift_history", String.join(";", this.shapeshift_history));
        }
    }

    @Unique
    private void parseSpellBindings(String encoded) {
        this.spell_bindings.clear();
        if (encoded != null && !encoded.isEmpty()) {
            String[] bindingsRaw = encoded.split(";");
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
    }

    @Unique
    private void parseUnlockedOrders(String encoded) {
        this.unlocked_orders.clear();
        if (encoded != null && !encoded.isEmpty()) {
            String[] ordersRaw = encoded.split(";");
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

    // LOADING DATA
    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void readAscensionData(ValueInput input, CallbackInfo ci) {
        // Try to read from the new nested tag first
        ValueInput ascensionData = input.childOrEmpty("ascension_data");

        // Use "influence" existence as a check for whether the new data format exists.
        // It is always saved, so if it's there, we have a valid child tag.
        var childInfluenceOpt = ascensionData.getInt("influence");

        if (childInfluenceOpt.isPresent()) {
            // LOAD FROM CHILD (New Interpretation)
            this.influence = childInfluenceOpt.get();
            this.rank = ascensionData.getString("rank").orElse("demigod");

            this.passive = ascensionData.getStringOr("passive", null);
            this.utility = ascensionData.getStringOr("utility", null);
            this.combat = ascensionData.getStringOr("combat", null);
            this.godOrder = ascensionData.getStringOr("godOrder", null);
            this.previousPassive = ascensionData.getStringOr("previousPassive", "");
            this.previousUtility = ascensionData.getStringOr("previousUtility", "");
            this.previousCombat = ascensionData.getStringOr("previousCombat", "");

            parseSpellBindings(ascensionData.getStringOr("spell_bindings", ""));
            parseUnlockedOrders(ascensionData.getStringOr("unlocked_orders", ""));
            parseShapeshiftHistory(ascensionData.getStringOr("shapeshift_history", ""));
        } else {
            // LOAD FROM ROOT (Legacy Fallback)
            this.influence = input.getInt("influence").orElse(0);
            this.rank = input.getString("rank").orElse("demigod");

            this.passive = input.getStringOr("passive", null);
            this.utility = input.getStringOr("utility", null);
            this.combat = input.getStringOr("combat", null);
            this.godOrder = input.getStringOr("godOrder", null);
            this.previousPassive = input.getStringOr("previousPassive", "");
            this.previousUtility = input.getStringOr("previousUtility", "");
            this.previousCombat = input.getStringOr("previousCombat", "");

            parseSpellBindings(input.getStringOr("spell_bindings", ""));
            parseUnlockedOrders(input.getStringOr("unlocked_orders", ""));
            parseShapeshiftHistory(input.getStringOr("shapeshift_history", ""));
        }
    }

    @Unique
    private void parseShapeshiftHistory(String encoded) {
        this.shapeshift_history.clear();
        if (encoded != null && !encoded.isEmpty()) {
            for (String id : encoded.split(";")) {
                if (!id.isEmpty() && this.shapeshift_history.size() < SHAPESHIFT_HISTORY_MAX) {
                    this.shapeshift_history.add(id);
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
        Order o = OrderRegistry.get(this.passive);
        return (o == null) ? null : o.getVersion(this.rank);
    }

    @Override
    public void setPassive(String order) {
        // If changing away from a previously equipped order, unbind spells tied to it
        if (this.passive != null && !this.passive.equals(order)) {
            unbindSpellsFromOrderAndType(this.passive, "passive");
        }
        this.passive = order;
    }

    @Override
    public Order getUtility() {
        Order o = OrderRegistry.get(this.utility);
        return (o == null) ? null : o.getVersion(this.rank);
    }

    @Override
    public void setUtility(String order) {
        if (this.utility != null && !this.utility.equals(order)) {
            unbindSpellsFromOrderAndType(this.utility, "utility");
        }
        this.utility = order;
    }

    @Override
    public Order getCombat() {
        Order o = OrderRegistry.get(this.combat);
        return (o == null) ? null : o.getVersion(this.rank);
    }

    @Override
    public void setCombat(String order) {
        if (this.combat != null && !this.combat.equals(order)) {
            unbindSpellsFromOrderAndType(this.combat, "combat");
        }
        this.combat = order;
    }

    @Unique
    private void unbindSpellsFromOrderAndType(String previousOrder, String type) {
        if (previousOrder == null || previousOrder.isEmpty())
            return;

        var remove = new java.util.ArrayList<String>();

        for (var e : this.spell_bindings.entrySet()) {
            String spellId = e.getValue();
            Spell spell = SpellCooldownManager.get(spellId);
            if (spell == null)
                continue;
            String orderName = spell.getOrder() == null ? "" : spell.getOrder().getOrderName();
            if (orderName.equalsIgnoreCase(previousOrder) && type.equalsIgnoreCase(spell.getType())) {
                Ascension.LOGGER.info("Removing " + spellId);
                remove.add(spellId);
            }
        }

        for (String s : remove) {
            this.unbind(s);
        }

        if (!remove.isEmpty()) {
            ServerPlayer player = (ServerPlayer) (Object) this;
            String msg = "Unbound spell" + (remove.size() == 1 ? " " : "s ") + "from " + previousOrder + ": "
                    + String.join(", ", remove);
            player.sendSystemMessage(Component.literal(msg));
        }
    }

    @Override
    public Map<Integer, String> getSpellBindings() {
        return this.spell_bindings;
    }

    @Override
    public void bind(int slot, String spellId) {
        if (spellId == null) {
            this.spell_bindings.remove(slot);
            return;
        }

        var it = this.spell_bindings.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            if (spellId.equals(e.getValue()) && e.getKey() != slot) {
                it.remove();
                break;
            }
        }

        this.spell_bindings.put(slot, spellId);
    }

    @Override
    public void unbind(String spellId) {
        var it = this.spell_bindings.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            if (spellId == null) {
                if (e.getValue() == null)
                    it.remove();
            } else if (spellId.equals(e.getValue())) {
                it.remove();
            }
        }
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

    @Override
    public List<String> getShapeshiftHistory() {
        return Collections.unmodifiableList(new ArrayList<>(this.shapeshift_history));
    }

    @Override
    public void pushShapeshiftKill(EntityType<?> entityType) {
        if (entityType == null || this.shapeshift_history.size() >= SHAPESHIFT_HISTORY_MAX)
            return;
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(entityType).toString();
        this.shapeshift_history.add(id);
    }

    @Override
    public EntityType<?> popShapeshiftForm() {
        if (this.shapeshift_history.isEmpty())
            return null;
        String id = this.shapeshift_history.remove(this.shapeshift_history.size() - 1);
        return BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(id)).map(Holder.Reference::value).orElse(null);
    }

    @Override
    public String getPreviousPassive() {
        return this.previousPassive != null ? this.previousPassive : "";
    }

    @Override
    public void setPreviousPassive(String order) {
        this.previousPassive = order != null ? order : "";
    }

    @Override
    public String getPreviousUtility() {
        return this.previousUtility != null ? this.previousUtility : "";
    }

    @Override
    public void setPreviousUtility(String order) {
        this.previousUtility = order != null ? order : "";
    }

    @Override
    public String getPreviousCombat() {
        return this.previousCombat != null ? this.previousCombat : "";
    }

    @Override
    public void setPreviousCombat(String order) {
        this.previousCombat = order != null ? order : "";
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