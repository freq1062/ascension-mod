package freq.ascension.test;

import java.lang.reflect.Method;
import java.util.Set;

import freq.ascension.Ascension;
import freq.ascension.items.InfluenceItem;
import freq.ascension.managers.AscensionData;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
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

        context.startSequence()
                .thenExecute(() -> {

                    Zombie zombie = context.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
                    zombie.hurt(level.damageSources().playerAttack(player), 1000f);
                    Ascension.LOGGER.info("Zombie health after: " + zombie.getHealth());

                    Ascension.LOGGER.info("Game mode: " + victim.gameMode.getGameModeForPlayer());
                    Ascension.LOGGER.info("Invulnerable: " + victim.isInvulnerable());
                    Ascension.LOGGER.info("Invulnerable time: " + victim.invulnerableTime);
                    Ascension.LOGGER.info("Has abilities: passive={}, utility={}, combat={}",
                            data.getPassive(), data.getUtility(), data.getCombat());

                    Ascension.LOGGER.info("Before damage - victim health: " + victim.getHealth());
                    // Damage the victim with the attacker as the source so AFTER_DEATH event has the killer
                    victim.hurt(level.damageSources().playerAttack(player), 1000f);
                    Ascension.LOGGER.info("After hurt() - victim health: " + victim.getHealth());
                    
                    // Mock players are invulnerable; force death by directly setting health and killing
                    victim.invulnerableTime = 0;
                    victim.setHealth(0.0f);
                    victim.die(level.damageSources().playerAttack(player));
                    Ascension.LOGGER.info("After setHealth(0) - victim health: " + victim.getHealth());
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    Ascension.LOGGER.info("After idle - victim health: " + victim.getHealth());
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

    // @GameTest
    // public void testPlayerBanBelowFive(GameTestHelper context) {
    // context.startSequence()
    // .thenExecute(() -> {
    // playerData.setInfluence(-5);
    // Ascension.LOGGER.info("Before death, influence: " +
    // playerData.getInfluence());
    // })

    // .thenExecute(() -> {
    // player.setHealth(0f); // Direct death
    // Ascension.LOGGER.info("Set health to 0");
    // })
    // .thenExecuteAfter(2, () -> {
    // Ascension.LOGGER.info("After death, influence: " +
    // playerData.getInfluence());

    // // Check if banned
    // NameAndId nameandid = new NameAndId(player.getGameProfile());
    // if
    // (!context.getLevel().getServer().getPlayerList().getBans().isBanned(nameandid))
    // {
    // context.fail("Player should be banned for influence <= -5");
    // }
    // })
    // .thenSucceed();
    // }

    // @GameTest
    // public void testWithdrawOnlyPositiveInfluence(GameTestHelper context) {
    // playerData.setInfluence(1);

    // context.startSequence()
    // // 1. Run the first command
    // .thenExecute(() -> TestHelper.runCommandAsPlayer(context, player, "withdraw
    // 1"))

    // // 2. Wait a tick and verify (gives the server a tick to process if needed)
    // .thenExecuteAfter(1, () -> {
    // if (playerData.getInfluence() != 0) {
    // context.fail("Influence should be 0, has " + playerData.getInfluence());
    // }
    // if
    // (!player.getInventory().hasAnyOf(Set.of(InfluenceItem.createItem().getItem())))
    // {
    // context.fail("Player should have influence item");
    // }
    // })

    // // 3. Try to withdraw again
    // .thenExecute(() -> TestHelper.runCommandAsPlayer(context, player, "withdraw
    // 1"))

    // // 4. Final verification
    // .thenExecuteAfter(1, () -> {
    // if (playerData.getInfluence() != 0) {
    // context.fail("Influence should still be 0");
    // }

    // int slot =
    // player.getInventory().findSlotMatchingItem(InfluenceItem.createItem());
    // if (slot == -1 || player.getInventory().getItem(slot).getCount() != 1) {
    // context.fail("Should still have exactly 1 influence item");
    // }
    // })

    // // 5. Mark as passed
    // .thenSucceed();
    // }
}
