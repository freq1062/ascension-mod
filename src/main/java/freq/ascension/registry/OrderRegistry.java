package freq.ascension.registry;

import java.util.HashMap;
import java.util.Map;

import freq.ascension.orders.*;
import freq.ascension.orders.Order;

public class OrderRegistry {
    private static final Map<String, Order> ORDERS = new HashMap<>();

    static {
        // register(Ocean.INSTANCE);
        register(Earth.INSTANCE);
        register(Sky.INSTANCE);
        register(Ocean.INSTANCE);
        register(Magic.INSTANCE);
        register(Flora.INSTANCE);
        register(Nether.INSTANCE);
    }

    private static void register(Order order) {
        ORDERS.put(order.getOrderName().toLowerCase(), order);
    }

    public static Order get(String name) {
        if (name == null)
            return null;
        return ORDERS.get(name.toLowerCase());
    }

    public static Iterable<Order> iterable() {
        return java.util.Collections.unmodifiableCollection(ORDERS.values());
    }

    public static void registerAllSpells() {
        for (Order order : ORDERS.values()) {
            order.registerSpells();
        }
    }
}