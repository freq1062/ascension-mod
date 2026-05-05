package freq.ascension.test;

import freq.ascension.managers.AscensionData;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.player.Player;

/**
 * Shared helper utilities for Ascension integration tests.
 *
 * <p>
 * All equip/bind/activate helpers dispatch real in-game commands so tests
 * replicate exact player behaviour rather than calling internal Java APIs.
 * Admin-level commands (/set, /setrank, /setinfluence) are dispatched via the
 * server command source (permission level 4). Player-level commands (/bind,
 * /activatespell) are dispatched via the mock player's command source.
 *
 * <p>
 * <b>NOTE:</b> Helper method bodies are stubs — they will be filled in when
 * test bodies are implemented.
 */
public class TestHelper {

    public static void runCommandAsServer(GameTestHelper context, String cmd) {
        context.getLevel().getServer().getCommands()
                .performPrefixedCommand(context.getLevel().getServer().createCommandSourceStack(), cmd);
    }

    public static void runCommandAsPlayer(GameTestHelper context, ServerPlayer executor, String cmd) {
        context.getLevel().getServer().getCommands()
                .performPrefixedCommand(executor.createCommandSourceStack(), cmd);
    }
}
