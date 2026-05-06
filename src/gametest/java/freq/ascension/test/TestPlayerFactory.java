package freq.ascension.test;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

public class TestPlayerFactory {
    public static ServerPlayer create(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();

        FakePlayer player = new FakePlayer(server, level);

        player.setPos(helper.absoluteVec(Vec3.atCenterOf(BlockPos.ZERO)));
        setupDummyConnection(server, player);

        // Add to level and ensure the server knows they are in survival
        level.addFreshEntity(player);
        player.setGameMode(GameType.SURVIVAL);

        // Register with player list
        server.getPlayerList().getPlayers().add(player);

        return player;
    }

    private static void setupDummyConnection(MinecraftServer server, ServerPlayer player) {
        // In 1.21, we need a Connection object and a ServerGamePacketListenerImpl
        // We use a "dummy" connection that isn't actually connected to a socket.
        net.minecraft.network.Connection dummyConn = new net.minecraft.network.Connection(
                net.minecraft.network.protocol.PacketFlow.CLIENTBOUND);

        player.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(
                server,
                dummyConn,
                player,
                net.minecraft.server.network.CommonListenerCookie.createInitial(player.getGameProfile(), false));
    }

    public static void remove(GameTestHelper helper, ServerPlayer player) {
        helper.getLevel().getServer()
                .getPlayerList().getPlayers().remove(player);
        player.remove(Entity.RemovalReason.DISCARDED);
    }
}