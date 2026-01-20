package freq.ascension.managers;

import java.util.function.BiConsumer;

import freq.ascension.orders.Order;
import freq.ascension.registry.OrderRegistry;
import net.minecraft.server.level.ServerPlayer;

public class Spell {
    private String id;
    private Order order;
    private String type;
    private final BiConsumer<ServerPlayer, SpellStats> executor;

    public Spell(String id, Order order, String type, BiConsumer<ServerPlayer, SpellStats> executor) {
        this.id = id;
        this.order = order;
        this.type = type;
        this.executor = executor;
    }

    public void run(ServerPlayer player, SpellStats stats) {
        executor.accept(player, stats);
    }

    public SpellStats getStats(ServerPlayer player) {
        AscensionData data = (AscensionData) player;
        Order baseOrder = OrderRegistry.get(this.order.getOrderName());
        Order rankedOrder = baseOrder.getVersion(data.getRank());
        return rankedOrder.getSpellStats(this.id);
    }

    public String getId() {
        return this.id;
    }

    public String getType() {
        return this.type;
    }

    public Order getOrder() {
        return this.order;
    }
}