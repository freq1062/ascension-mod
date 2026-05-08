package freq.ascension;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import freq.ascension.api.TaskScheduler;
import freq.ascension.commands.*;
import freq.ascension.registry.*;
import freq.ascension.managers.*;
import freq.ascension.orders.Nether;
import freq.ascension.config.Config;

public class Ascension implements ModInitializer {
	public static final String MOD_ID = "ascension";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final TaskScheduler scheduler = new TaskScheduler();
	private static MinecraftServer server;

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		Config.load();

		// Register a server tick event to process scheduled tasks
		ServerTickEvents.END_SERVER_TICK.register((MinecraftServer s) -> {
			server = s;
			scheduler.tick(s.getTickCount());
			PromotionHandler.cleanExpired(s.getTickCount());

		});

		// Temporary
		EnchantmentScanManager.init();

		AbilityManager.init();
		InfluenceManager.init();
		OrderRegistry.registerAllSpells();
		SpellCooldownManager.updateActiveSpells();
		/*
		 * TODO: Bug 5/12B: ChallengerTrialManager AFTER_DEATH must be registered BEFORE
		 * AbilityManager so it fires first on god death, promoting the challenger and
		 * demoting the god before AbilityManager sees gm.isGod(sp) — avoiding
		 * double-demotion.
		 */
		// ChallengerTrialManager.get().registerEventListeners();
		PoiManager.init();

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			AscensionMenuOpenCommand.register(dispatcher);
			ActivateSpellCommand.register(dispatcher);
			BindCommand.register(dispatcher);
			GetInfluenceCommand.register(dispatcher);
			GiveInfluenceCommand.register(dispatcher);
			SetInfluenceCommand.register(dispatcher);
			GiveSigilCommand.register(dispatcher);
			UnbindCommand.register(dispatcher);
			WithdrawCommand.register(dispatcher);
			SetOrderCommand.register(dispatcher);
			SetRankCommand.register(dispatcher);
			SetPoiCommand.register(dispatcher);
			LossCounterCommand.register(dispatcher);
			AscendConfirmCommand.register(dispatcher);
			AscensionActionCommand.register(dispatcher);
			ShapelistCommand.register(dispatcher);
			ReloadConfigCommand.register(dispatcher);
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer joining = handler.getPlayer();

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

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ServerPlayer disconnecting = handler.getPlayer();
			java.util.UUID disconnectedUUID = disconnecting.getUUID();
			Utils.clearPlayerModifications(disconnecting);
			// Clean up lava flight state so the next login doesn't inherit stale ability
			// flags
			LavaFlightManager.cleanup(disconnectedUUID);
			// Clean up fire-contact tracking for autocrit
			Nether.clearFireTracking(disconnectedUUID);
			// Clean up soul rage active state
			SpellRegistry.clearSoulRage(disconnectedUUID);
			// Clear thorns targeting mode
			SpellRegistry.clearThorns(disconnectedUUID);
		});

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

			stoppingServer.getPlayerList().getPlayers().forEach(player -> {
				// Try catch blocks inside the function
				Utils.clearPlayerModifications(player);
			});
		});

		LOGGER.info("Ascension SMP Mod Loaded!");
	}

	public static MinecraftServer getServer() {
		return server;
	}
}
