package freq.ascension.test;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public class FakePlayer extends ServerPlayer {
    public FakePlayer(MinecraftServer server, ServerLevel level) {
        super(server, level, new GameProfile(UUID.randomUUID(), "TestPlayer"),
                ClientInformation.createDefault());
    }

    // Override connection to avoid NPEs
    @Override
    public void displayClientMessage(Component message, boolean overlay) {
    }
}