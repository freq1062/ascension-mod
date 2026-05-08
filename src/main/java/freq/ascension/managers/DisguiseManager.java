package freq.ascension.managers;

import freq.ascension.Ascension;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import xyz.nucleoid.disguiselib.api.EntityDisguise;

public class DisguiseManager {

    // TODO: One of these is not right
    private static final String[] DISGUISE_RUNTIME_FIELDS = {
            "disguiselib$disguiseEntity",
            "disguiselib$disguiseType",
            "disguiselib$profile",
            "disguiselib$trueSight"
    };

    public static void init() {
        // Recover from any stale disguise state that made it into memory during join.
        // This is deferred onto the server task queue so DisguiseLib packet broadcasts
        // only run after the player tracker is fully initialized.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            scheduleDisguiseRecovery(handler.getPlayer(), "join");

            // Re-send fake leather boots on reconnect for Ocean passive players so
            // the client immediately predicts powder-snow walk correctly.
            ServerPlayer joining = handler.getPlayer();
            // PlayerList.remove() (which writes NBT) fires before DISCONNECT, so a
            // protocol kick can save stale flight/no-gravity/effect state before our
            // disconnect cleanup runs. Clear those flags on join and let the normal
            // ability pipeline re-apply anything legitimate on the next tick.
            recoverSavedPlayerState(joining);
            // Defensively clear any stale stun from a previous session (e.g. if the
            // server crashed while the stun DelayedTask was still pending).
            freq.ascension.registry.SpellRegistry.clearStun(joining.getUUID());

            // Grant custom recipe book unlocks on join (deferred 1 tick so the player
            // entity is fully initialized before awardRecipes writes the advancement).
            Ascension.scheduler.schedule(new freq.ascension.api.DelayedTask(1, () -> {
                java.util.List<net.minecraft.world.item.crafting.RecipeHolder<?>> toUnlock = new java.util.ArrayList<>();
                server.getRecipeManager()
                        .byKey(net.minecraft.resources.ResourceKey.create(
                                net.minecraft.core.registries.Registries.RECIPE,
                                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ascension",
                                        "challengers_sigil")))
                        .ifPresent(toUnlock::add);
                server.getRecipeManager()
                        .byKey(net.minecraft.resources.ResourceKey.create(
                                net.minecraft.core.registries.Registries.RECIPE,
                                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ascension",
                                        "influence_restoration")))
                        .ifPresent(toUnlock::add);
                if (!toUnlock.isEmpty()) {
                    joining.awardRecipes(toUnlock);
                }
            }));
        });

        // Clear stale disguise state after death respawns, including carpet-bot
        // respawns
        // that recreate the player entity from saved data.
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
            if (!alive) {
                scheduleDisguiseRecovery(newPlayer, "respawn");
                recoverSavedPlayerState(newPlayer);
                freq.ascension.registry.SpellRegistry.clearStun(newPlayer.getUUID());
            }
        });

        // Clear disguises when players disconnect to prevent DisguiseLib errors on
        // rejoin
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            try {
                EntityDisguise disguise = (EntityDisguise) handler
                        .getPlayer();
                if (handler.getPlayer().connection == null) {
                    clearDisguiseSilently(handler.getPlayer(), "disconnect");
                } else {
                    // removeDisguise() clears all DisguiseLib NBT fields entirely,
                    // preventing the null-serverPlayer NPE in fromTag on next login.
                    disguise.removeDisguise();
                }
            } catch (Exception e) {
                Ascension.LOGGER.warn("Failed to clear disguise on disconnect for "
                        + handler.getPlayer().getName().getString() + ": " + e.getMessage());
            }

            // TODO: Clean up these things
            java.util.UUID disconnectedUUID = handler.getPlayer().getUUID();
            // Clear any active stun so the player doesn't rejoin floating with no gravity
            freq.ascension.registry.SpellRegistry.clearStun(disconnectedUUID);
            // Clean up lava flight state so the next login doesn't inherit stale ability
            // flags
            freq.ascension.managers.LavaFlightManager.cleanup(disconnectedUUID);
            // Clean up fire-contact tracking for autocrit
            freq.ascension.orders.Nether.clearFireTracking(disconnectedUUID);
            // Clean up soul rage active state
            freq.ascension.registry.SpellRegistry.clearSoulRage(disconnectedUUID);
            // Clear thorns targeting mode
            freq.ascension.registry.SpellRegistry.clearThorns(disconnectedUUID);
            // Strip lingering poison from thorns before NBT save
            handler.getPlayer().removeEffect(net.minecraft.world.effect.MobEffects.POISON);
            // Clear flight ability so mayfly/flying flags are not saved to NBT.
            // Sky.applyEffect will re-enable mayfly on the next AbilityManager tick after
            // rejoin.
            {
                ServerPlayer leavingPlayer = handler.getPlayer();
                if (leavingPlayer.gameMode() == net.minecraft.world.level.GameType.SURVIVAL
                        || leavingPlayer.gameMode() == net.minecraft.world.level.GameType.ADVENTURE) {
                    leavingPlayer.getAbilities().mayfly = false;
                    leavingPlayer.getAbilities().flying = false;
                    leavingPlayer.onUpdateAbilities();
                }
            }
            // Clean up POI interact debounce map
            // lastPoiInteractTick.remove(disconnectedUUID);
        });

        // Clear all disguises on server shutdown
        ServerLifecycleEvents.SERVER_STOPPING.register((stoppingServer) -> {
            // Stop all POI rotation tasks and discard their entities across all dimensions
            try {
                PoiManager poi = PoiManager.get(stoppingServer);
                java.util.List<ServerLevel> allLevels = new java.util.ArrayList<>();
                stoppingServer.getAllLevels().forEach(allLevels::add);
                poi.stopAllRotationTasks(allLevels.toArray(new ServerLevel[0]));
            } catch (Exception e) {
                Ascension.LOGGER.warn("Failed to stop POI rotation tasks on shutdown: " + e.getMessage());
            }

            // Stop all trial tasks
            try {
                ChallengerTrialManager.get().stopAllTasks();
            } catch (Exception e) {
                Ascension.LOGGER.warn("Failed to stop trial tasks on shutdown: " + e.getMessage());
            }

            // Flush GodManager persistent state so no god data is lost on graceful shutdown
            try {
                GodManager.get(stoppingServer).setDirty();
            } catch (Exception e) {
                Ascension.LOGGER.warn("Failed to flush GodManager on shutdown: " + e.getMessage());
            }

            try {
                stoppingServer.getPlayerList().getPlayers().forEach(player -> {
                    try {
                        if (player.connection == null) {
                            clearDisguiseSilently(player, "server stop");
                        } else {
                            EntityDisguise disguise = (EntityDisguise) player;
                            disguise.removeDisguise();
                        }
                    } catch (Exception e) {
                        // Silently ignore per-player failures
                    }
                });
            } catch (Exception e) {
                Ascension.LOGGER.debug("Failed to clear disguises on server stop: " + e.getMessage());
            }
        });
    }

    private static void scheduleDisguiseRecovery(ServerPlayer player, String phase) {
        MinecraftServer currentServer = player.level() != null ? player.level().getServer() : null;
        if (currentServer == null) {
            Ascension.LOGGER.warn("Could not schedule stale disguise recovery during " + phase + " for "
                    + player.getName().getString() + ": server was null");
            return;
        }

        currentServer.execute(() -> clearDisguiseRecovery(player, phase));
    }

    private static void recoverSavedPlayerState(ServerPlayer player) {
        // PlayerList.remove() can persist these flags before DISCONNECT cleanup runs.
        // Clear them on login/respawn and let the normal ability systems re-apply any
        // legitimate flight state after the player is fully back in the world.
        if (player.gameMode() == net.minecraft.world.level.GameType.SURVIVAL
                || player.gameMode() == net.minecraft.world.level.GameType.ADVENTURE) {
            if (player.getAbilities().mayfly || player.getAbilities().flying) {
                player.getAbilities().mayfly = false;
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
            }
            if (player.isNoGravity()) {
                player.setNoGravity(false);
            }
        }

        // Thorns poison can also be saved before DISCONNECT cleanup, especially for
        // protocol kicks or carpet-bot respawns reusing a saved player file.
        player.removeEffect(net.minecraft.world.effect.MobEffects.POISON);
    }

    private static void clearDisguiseRecovery(ServerPlayer player, String phase) {
        try {
            EntityDisguise disguise = (EntityDisguise) player;
            if (!disguise.isDisguised()) {
                return;
            }

            if (player.connection == null) {
                clearDisguiseSilently(player, phase + " recovery");
            } else {
                disguise.removeDisguise();
                Ascension.LOGGER.warn("Cleared stale disguise during " + phase + " recovery for "
                        + player.getName().getString());
            }
        } catch (Exception e) {
            Ascension.LOGGER.warn("Failed to clear stale disguise during " + phase + " recovery for "
                    + player.getName().getString() + ": " + e.getMessage());
        }
    }

    public static boolean clearDisguiseSilently(ServerPlayer player, String phase) {
        try {
            EntityDisguise disguise = (EntityDisguise) player;
            if (!disguise.isDisguised()) {
                return false;
            }

            setDisguiseField(player, "disguiselib$disguiseEntity", null);
            setDisguiseField(player, "disguiselib$disguiseType", player.getType());
            setDisguiseField(player, "disguiselib$profile", null);
            setDisguiseField(player, "disguiselib$trueSight", false);
            return true;
        } catch (Exception e) {
            Ascension.LOGGER.warn("Failed to clear disguise silently during " + phase + " for "
                    + player.getName().getString() + ": " + e.getMessage());
            return false;
        }
    }

    private static void setDisguiseField(ServerPlayer player, String fieldName, Object value)
            throws ReflectiveOperationException {
        Class<?> type = player.getClass();
        while (type != null) {
            try {
                java.lang.reflect.Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(player, value);
                return;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }
}
