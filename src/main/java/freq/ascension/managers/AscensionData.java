package freq.ascension.managers;

import java.util.List;
import java.util.Map;

import freq.ascension.orders.Order;

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

    String getGodOrder();

    void setGodOrder(String order);

    List<Order> getEquippedOrders();
}