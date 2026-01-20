package freq.ascension.managers;

import java.util.List;
import java.util.Map;

import freq.ascension.orders.Order;

public interface AscensionData {
    String getRank();

    int getInfluence();

    void setInfluence(int amount);

    void addInfluence(int amount);

    String getPassive();

    void setPassive(String order);

    String getUtility();

    void setUtility(String order);

    String getCombat();

    void setCombat(String order);

    Map<Integer, String> getSpellBindings();

    String getGodOrder();

    void setGodOrder(String order);

    List<Order> getEquippedOrders();
}