package freq.ascension.managers;

import java.util.List;
import java.util.Map;

import freq.ascension.orders.Order;
import net.minecraft.world.entity.EntityType;

public interface AscensionData {

    public record OrderUnlock(boolean passive, boolean utility, boolean combat) {

        public static final OrderUnlock EMPTY = new OrderUnlock(false, false, false);

        public boolean isFullyUnlocked() {
            return passive && utility && combat;
        }

        public boolean hasPassive() {
            return passive;
        }

        public boolean hasUtility() {
            return utility;
        }

        public boolean hasCombat() {
            return combat;
        }
    }

    String getRank();

    void setRank(String rank);

    int getInfluence();

    void setInfluence(int amount);

    void addInfluence(int amount);

    Map<String, OrderUnlock> getUnlocked();

    OrderUnlock getUnlockedOrder(String order);

    void unlock(String order, String type);

    Order getPassive();

    void setPassive(String order);

    Order getUtility();

    void setUtility(String order);

    Order getCombat();

    void setCombat(String order);

    Map<Integer, String> getSpellBindings();

    void bind(int slot, String spellId);

    void unbind(String spellId);

    void unbindAll();

    String getGodOrder();

    void setGodOrder(String order);

    List<Order> getEquippedOrders();

    /** Shapeshift history: stack of entity type registry IDs (max 5). Last element = next form. */
    List<String> getShapeshiftHistory();

    void pushShapeshiftKill(EntityType<?> entityType);

    /** Removes and returns the next form to transform into, or null if empty. */
    EntityType<?> popShapeshiftForm();
}