package freq.ascension.test;

import java.lang.reflect.Method;
import java.util.Set;

import freq.ascension.Ascension;
import freq.ascension.items.InfluenceItem;
import freq.ascension.managers.AscensionData;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.level.GameType;

public class InfluenceTests implements CustomTestMethodInvoker {
    private ServerPlayer player;
    private AscensionData data;

    // Before each
    @Override
    public void invokeTestMethod(GameTestHelper context, Method method)
            throws ReflectiveOperationException {
        player = context.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        data = (AscensionData) player;
        data.setInfluence(0);
        method.invoke(this, context);
    }

    @GameTest
    public void testInfluenceBasicFunctionality(GameTestHelper context) {
        ServerPlayer victim = context.makeMockServerPlayerInLevel();
        victim.setGameMode(GameType.SURVIVAL);
        AscensionData victimData = (AscensionData) victim;
        ServerLevel level = context.getLevel();
        Ascension.LOGGER.info("TEST INFLUENCE BASIC FUNCTIONALITY");

        context.startSequence()
                .thenExecute(() -> {
                    victim.invulnerableTime = 0;
                    victim.setHealth(0.0f);
                    victim.die(level.damageSources().playerAttack(player));
                    Ascension.LOGGER.info("After setHealth(0) - victim health: " + victim.getHealth());
                })
                .thenExecute(() -> {
                    Ascension.LOGGER.info("Victim is dead: " + victim.isDeadOrDying());
                    int killerInfluence = data.getInfluence();
                    int victimInfluence = victimData.getInfluence();

                    if (killerInfluence != 1) {
                        context.fail("Killer influence expected 1, got " + killerInfluence);
                    }
                    if (victimInfluence != -1) {
                        context.fail("Victim influence expected -1, got " + victimInfluence);
                    }
                })
                .thenSucceed();
    }

    @GameTest
    public void testPlayerBanBelowFive(GameTestHelper context) {
        Ascension.LOGGER.info("TEST PLAYER BAN ON -5 INFLUENCE");
        context.startSequence()
                .thenExecute(() -> {
                    data.setInfluence(-5);
                    Ascension.LOGGER.info("Before death, influence: " +
                            data.getInfluence());
                })

                .thenExecute(() -> {
                    player.die(context.getLevel().damageSources().drown());
                })
                .thenExecute(() -> {
                    Ascension.LOGGER.info("After death, influence: " +
                            data.getInfluence());

                    // Check if banned
                    NameAndId nameandid = new NameAndId(player.getGameProfile());
                    if (!context.getLevel().getServer().getPlayerList().getBans().isBanned(nameandid)) {
                        context.fail("Player should be banned for influence <= -5");
                    }
                })
                .thenSucceed();
    }

    @GameTest
    public void testWithdrawOnlyPositiveInfluence(GameTestHelper context) {
        Ascension.LOGGER.info("TEST WITHDRAWING ONLY WORKS ON POSITIVE INFLUENCE");
        data.setInfluence(1);

        context.startSequence()
                // 1. Run the first command
                .thenExecute(() -> TestHelper.runCommandAsPlayer(context, player, "withdraw 1"))

                // 2. Wait a tick and verify (gives the server a tick to process if needed)
                .thenExecute(() -> {
                    if (data.getInfluence() != 0) {
                        context.fail("Influence should be 0, has " + data.getInfluence());
                    }
                    if (!player.getInventory().hasAnyOf(Set.of(InfluenceItem.createItem().getItem()))) {
                        context.fail("Player should have influence item");
                    }
                })

                // 3. Try to withdraw again
                .thenExecute(() -> TestHelper.runCommandAsPlayer(context, player, "withdraw 1"))

                // 4. Final verification
                .thenExecute(() -> {
                    if (data.getInfluence() != 0) {
                        context.fail("Influence should still be 0");
                    }

                    int slot = player.getInventory().findSlotMatchingItem(InfluenceItem.createItem());
                    if (slot == -1 || player.getInventory().getItem(slot).getCount() != 1) {
                        context.fail("Should still have exactly 1 influence item");
                    }
                })

                // 5. Mark as passed
                .thenSucceed();
    }
}
