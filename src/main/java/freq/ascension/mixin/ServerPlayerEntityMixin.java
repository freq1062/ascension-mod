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
public class ServerPlayerEntityMixin implements AscensionData {
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
        }
    }

    @Override
    public String getRank() {
        return this.rank;
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
    public String getPassive() {
        return this.passive;
    }

    @Override
    public void setPassive(String order) {
        this.passive = order;
    }

    @Override
    public String getUtility() {
        return this.utility;
    }

    @Override
    public void setUtility(String order) {
        this.utility = order;
    }

    @Override
    public String getCombat() {
        return this.combat;
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

    // Helper methods
    /**
     * Returns the player's equipped orders in passive/utility/combat order.
     * The returned list is cached and only recomputed when abilities (or rank)
     * change. Duplicates are removed (if the same order is in multiple slots).
     */
    @Override
    public List<Order> getEquippedOrders() {
        return List.of(OrderRegistry.get(getPassive()), OrderRegistry.get(getUtility()),
                OrderRegistry.get(getCombat()));
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