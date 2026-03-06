package freq.ascension.mixin;

import net.minecraft.server.level.ServerPlayer;

/**
 * Context holder for passing player information between FoodMixin and
 * FoodDataMixin.
 * This is necessary because mixins cannot have public static methods.
 */
public class FoodDataContext {
    private static final ThreadLocal<ServerPlayer> currentPlayer = ThreadLocal.withInitial(() -> null);

    public static void setCurrentPlayer(ServerPlayer player) {
        currentPlayer.set(player);
    }

    public static ServerPlayer getCurrentPlayer() {
        return currentPlayer.get();
    }

    public static void clear() {
        currentPlayer.remove();
    }
}
