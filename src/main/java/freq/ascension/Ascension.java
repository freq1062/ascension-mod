package freq.ascension;

import java.util.Map;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// PacketEvents temporarily disabled due to dependency issues
// import com.github.retrooper.packetevents.PacketEvents;
// import com.github.retrooper.packetevents.event.PacketListener;
// import com.github.retrooper.packetevents.event.PacketListenerPriority;
// import com.github.retrooper.packetevents.event.PacketSendEvent;
// import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
// import com.github.retrooper.packetevents.protocol.packettype.PacketType;
// import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;

import xyz.nucleoid.disguiselib.api.EntityDisguise;

import freq.ascension.api.TaskScheduler;
import freq.ascension.commands.AscendConfirmCommand;
import freq.ascension.commands.ActivateSpellCommand;
import freq.ascension.commands.AscensionMenuOpenCommand;
import freq.ascension.commands.BindCommand;
import freq.ascension.commands.GetInfluenceCommand;
import freq.ascension.commands.GiveInfluenceCommand;
import freq.ascension.commands.ReloadConfigCommand;
import freq.ascension.commands.SetInfluenceCommand;
import freq.ascension.commands.LossCounterCommand;
import freq.ascension.commands.SetPoiCommand;
import freq.ascension.commands.SetRankCommand;
import freq.ascension.commands.UnbindCommand;
import freq.ascension.commands.WithdrawCommand;
import freq.ascension.commands.SetOrderCommand;
import freq.ascension.items.ChallengerSigil;
import freq.ascension.managers.AbilityManager;
import freq.ascension.managers.ChallengerTrialManager;
import freq.ascension.managers.GodManager;
import freq.ascension.managers.InfluenceManager;
import freq.ascension.managers.PlantProximityManager;
import freq.ascension.managers.PoiManager;
import freq.ascension.managers.PromotionHandler;
import freq.ascension.managers.SpellCooldownManager;
import freq.ascension.registry.OrderRegistry;
import freq.ascension.weapons.HellfireCrossbow;
import freq.ascension.weapons.PrismWand;
import freq.ascension.weapons.GravitonGauntlet;
import freq.ascension.registry.WeaponRegistry;
import freq.ascension.weapons.ColossusHammer;
import freq.ascension.weapons.RuinousScythe;
import freq.ascension.weapons.TempestTrident;
import freq.ascension.weapons.VinewrathAxe;

// import io.github.retrooper.packetevents.PacketEventsServerMod;
// import io.github.retrooper.packetevents.factory.fabric.FabricPacketEventsAPI;

public class Ascension implements ModInitializer {
	public static final String MOD_ID = "ascension";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final TaskScheduler scheduler = new TaskScheduler();
	// public static FabricPacketEventsAPI api; // PacketEvents temporarily disabled
	private static MinecraftServer server;

	/**
	 * Per-player last POI interact tick — used to debounce UseEntityCallback spam.
	 */
	private static final Map<java.util.UUID, Integer> lastPoiInteractTick = new java.util.HashMap<>();

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		Config.load();

		// NOTE: ChallengerSigil no longer registers a custom item — it uses
		// heart_of_the_sea
		// with CustomModelData "challengers_sigil" (same pattern as InfluenceItem).

		// PacketEvents temporarily disabled due to Adventure API dependency issues
		// TODO: Fix PacketEvents dependencies - requires compatible Adventure API
		// api = PacketEventsServerMod.constructApi(MOD_ID);
		// PacketEvents.setAPI(api);
		// api.load();

		// Register a server tick event to process scheduled tasks
		ServerTickEvents.END_SERVER_TICK.register((MinecraftServer s) -> {
			server = s;
			scheduler.tick(s.getTickCount());
			PromotionHandler.cleanExpired(s.getTickCount());

			// Ocean passive: prevent freeze ticks and provide scaffolding-like ascent in
			// powder snow.
			for (ServerPlayer player : s.getPlayerList().getPlayers()) {
				if (!AbilityManager.anyMatch(player, order -> order.canWalkOnPowderSnow(player)))
					continue;

				// Clear freeze ticks every tick — synced to client via SynchedEntityData,
				// which eliminates the snowflake overlay without needing fake equipment
				// packets.
				if (player.getTicksFrozen() > 0) {
					player.setTicksFrozen(0);
				}

				// When the player is inside powder snow and pressing jump (not crouching),
				// apply upward velocity. This replicates the LivingEntity powder-snow ascent
				// that normally requires wasInPowderSnow=true, which we suppress via the mixin.
				if (!player.isShiftKeyDown()
						&& player.isJumping()
						&& player.getInBlockState().is(Blocks.POWDER_SNOW)) {
					player.setDeltaMovement(
							player.getDeltaMovement().x,
							0.2,
							player.getDeltaMovement().z);
				}
			}
		});

		// PacketEvents listener temporarily disabled
		// ServerLifecycleEvents.SERVER_STARTED.register((MinecraftServer s) -> {
		// api.init();
		// api.getEventManager().registerListener(new PacketListener() {
		// @Override
		// public void onPacketSend(PacketSendEvent event) {
		// if (event.getPacketType() == PacketType.Play.Server.SPAWN_ENTITY) {
		// WrapperPlayServerSpawnEntity wrapper = new
		// WrapperPlayServerSpawnEntity(event);
		// if (wrapper.getEntityType() == EntityTypes.IRON_GOLEM) {
		// wrapper.setEntityType(EntityTypes.PIG);
		// }
		// }
		// }
		// }, PacketListenerPriority.HIGH);
		// });
		SpellCooldownManager.updateActiveSpells();
		OrderRegistry.registerAllSpells();
		WeaponRegistry.register(new ColossusHammer());
		WeaponRegistry.register(new VinewrathAxe());
		// Register GravitonGauntlet and its cleanup/debounce event hooks.
		GravitonGauntlet.register();
		WeaponRegistry.register(GravitonGauntlet.INSTANCE);
		// Register Ruinous Scythe and its cleanup event hooks.
		RuinousScythe.register();
		WeaponRegistry.register(RuinousScythe.INSTANCE);
		// Register Tempest Trident and its cleanup/event hooks.
		TempestTrident.register();
		WeaponRegistry.register(TempestTrident.INSTANCE);
		// Register Hellfire Crossbow and its cleanup/event hooks.
		HellfireCrossbow.register();
		WeaponRegistry.register(HellfireCrossbow.INSTANCE);
		// Register Prism Wand and its bow-intercept/entity-load event hooks.
		PrismWand.register();
		WeaponRegistry.register(PrismWand.INSTANCE);
		// Bug 5/12B: ChallengerTrialManager AFTER_DEATH must be registered BEFORE
		// AbilityManager so it fires first on god death, promoting the challenger and
		// demoting the god before AbilityManager sees gm.isGod(sp) — avoiding
		// double-demotion.
		ChallengerTrialManager.get().registerEventListeners();
		AbilityManager.init();
		InfluenceManager.init();
		PlantProximityManager.init();

		// Spawn POI entities for all saved orders on server start
		ServerLifecycleEvents.SERVER_STARTED.register(startedServer -> {
			PoiManager poi = PoiManager.get(startedServer);

			// Phase 1: force-load every POI chunk so that entities saved in the
			// previous run are accessible to the entity query below.
			for (String orderName : poi.getAllPoiOrders()) {
				net.minecraft.core.BlockPos pos = poi.getPoiPosition(orderName);
				if (pos == null)
					continue;
				String dimKey = poi.getPoiDimension(orderName);
				ResourceKey<Level> levelKey = ResourceKey.create(
						Registries.DIMENSION, ResourceLocation.parse(dimKey));
				ServerLevel level = startedServer.getLevel(levelKey);
				if (level != null) {
					level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
				}
			}

			// Phase 2: discard stale POI entities across all POI dimensions (chunks now
			// loaded).
			PoiManager.cleanupStalePOIs(startedServer.overworld(), poi);

			// Phase 3: spawn fresh POI entities.
			for (String orderName : poi.getAllPoiOrders()) {
				net.minecraft.core.BlockPos pos = poi.getPoiPosition(orderName);
				String dimKey = poi.getPoiDimension(orderName);
				ResourceKey<Level> levelKey = ResourceKey.create(
						Registries.DIMENSION, ResourceLocation.parse(dimKey));
				ServerLevel level = startedServer.getLevel(levelKey);
				if (level != null && pos != null) {
					poi.spawnPoiEntities(orderName, level, pos, scheduler);
				}
			}
			// Wire up server/scheduler references in the trial manager
			ChallengerTrialManager.get().registerEvents(startedServer, scheduler);
		});

		// Discard stale POI entities that might have been saved into chunks
		// (e.g. if the chunk unloaded during gameplay or after a crash)
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (entity instanceof net.minecraft.world.entity.Display.ItemDisplay
					|| entity instanceof net.minecraft.world.entity.Interaction) {
				if (entity.hasCustomName()) {
					String name = entity.getCustomName().getString();
					if (name.startsWith("ascension_poi_")) {
						if (world instanceof ServerLevel sl) {
							PoiManager poi = PoiManager.get(sl.getServer());
							// If this entity's UUID is not the actively managed one in the PoiManager,
							// discard it
							if (!poi.isActivePoiEntity(entity.getUUID())) {
								entity.discard();
							}
						}
					}
				}
			}
		});

		// POI cube right-click → promotion request or challenger trial
		UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (!(world instanceof ServerLevel level))
				return InteractionResult.PASS;
			if (!(player instanceof ServerPlayer sp))
				return InteractionResult.PASS;
			if (!(entity instanceof Interaction))
				return InteractionResult.PASS;
			net.minecraft.network.chat.Component name = entity.getCustomName();
			if (name == null)
				return InteractionResult.PASS;
			String nameStr = name.getString();
			if (!nameStr.startsWith("ascension_poi_"))
				return InteractionResult.PASS;
			String orderName = nameStr.substring("ascension_poi_".length());

			// Debounce: suppress repeated fires while holding right-click (10-tick window)
			int currentTick = level.getServer().getTickCount();
			Integer lastTick = lastPoiInteractTick.get(sp.getUUID());
			if (lastTick != null && currentTick - lastTick < 10) {
				return InteractionResult.SUCCESS;
			}
			lastPoiInteractTick.put(sp.getUUID(), currentTick);

			handlePoiRightClick(sp, orderName, level, hand);

			// White glow flash on the cube for any successful right-click
			PoiManager poi = PoiManager.get(level.getServer());
			net.minecraft.world.entity.Display.ItemDisplay cubeDisplay = poi.getDisplayEntity(orderName,
					level.getServer());
			if (cubeDisplay != null) {
				cubeDisplay.setGlowingTag(true);
				cubeDisplay.setGlowColorOverride(0xFFFFFF);
				scheduler.schedule(new freq.ascension.api.DelayedTask(5, () -> {
					if (!cubeDisplay.isRemoved()) {
						cubeDisplay.setGlowingTag(false);
					}
				}));
			}

			return InteractionResult.SUCCESS;
		});

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
			scheduler.schedule(new freq.ascension.api.DelayedTask(1, () -> {
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
				// removeDisguise() clears all DisguiseLib NBT fields entirely,
				// preventing the null-serverPlayer NPE in fromTag on next login.
				disguise.removeDisguise();
			} catch (Exception e) {
				LOGGER.warn("Failed to clear disguise on disconnect for "
						+ handler.getPlayer().getName().getString() + ": " + e.getMessage());
			}

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
			lastPoiInteractTick.remove(disconnectedUUID);
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
				LOGGER.warn("Failed to stop POI rotation tasks on shutdown: " + e.getMessage());
			}

			// Stop all trial tasks
			try {
				ChallengerTrialManager.get().stopAllTasks();
			} catch (Exception e) {
				LOGGER.warn("Failed to stop trial tasks on shutdown: " + e.getMessage());
			}

			// Flush GodManager persistent state so no god data is lost on graceful shutdown
			try {
				GodManager.get(stoppingServer).setDirty();
			} catch (Exception e) {
				LOGGER.warn("Failed to flush GodManager on shutdown: " + e.getMessage());
			}

			try {
				stoppingServer.getPlayerList().getPlayers().forEach(player -> {
					try {
						EntityDisguise disguise = (EntityDisguise) player;
						disguise.removeDisguise();
					} catch (Exception e) {
						// Silently ignore per-player failures
					}
				});
			} catch (Exception e) {
				LOGGER.debug("Failed to clear disguises on server stop: " + e.getMessage());
			}
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			AscensionMenuOpenCommand.register(dispatcher);
			ActivateSpellCommand.register(dispatcher);
			BindCommand.register(dispatcher);
			GetInfluenceCommand.register(dispatcher);
			GiveInfluenceCommand.register(dispatcher);
			SetInfluenceCommand.register(dispatcher);
			freq.ascension.commands.GiveSigilCommand.register(dispatcher);
			UnbindCommand.register(dispatcher);
			WithdrawCommand.register(dispatcher);
			SetOrderCommand.register(dispatcher);
			SetRankCommand.register(dispatcher);
			SetPoiCommand.register(dispatcher);
			LossCounterCommand.register(dispatcher);
			AscendConfirmCommand.register(dispatcher);
			// Register click-action command for book UI
			freq.ascension.commands.AscensionActionCommand.register(dispatcher);
			freq.ascension.commands.ShapelistCommand.register(dispatcher);
			ReloadConfigCommand.register(dispatcher);
			freq.ascension.commands.ClearStunCommand.register(dispatcher);
		});

		LOGGER.info("Ascension SMP Mod Loaded!");
	}

	public static MinecraftServer getServer() {
		return server;
	}

	/**
	 * Returns the shared {@link TaskScheduler} for scheduling delayed or continuous
	 * tasks.
	 */
	public static TaskScheduler getScheduler() {
		return scheduler;
	}

	private static void scheduleDisguiseRecovery(ServerPlayer player, String phase) {
		MinecraftServer currentServer = player.level() != null ? player.level().getServer() : null;
		if (currentServer == null) {
			LOGGER.warn("Could not schedule stale disguise recovery during " + phase + " for "
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

			disguise.removeDisguise();
			LOGGER.warn("Cleared stale disguise during " + phase + " recovery for "
					+ player.getName().getString());
		} catch (Exception e) {
			LOGGER.warn("Failed to clear stale disguise during " + phase + " recovery for "
					+ player.getName().getString() + ": " + e.getMessage());
		}
	}

	/**
	 * Handles a right-click on an Order POI interaction entity.
	 * Routes to the challenger trial system if a Challenger's Sigil is held, heals
	 * the cube
	 * by 30 if a diamond block is held during an active trial, otherwise falls
	 * through to the
	 * standard promotion confirmation flow.
	 */
	private static void handlePoiRightClick(ServerPlayer player, String orderName,
			ServerLevel level, net.minecraft.world.InteractionHand hand) {
		net.minecraft.world.item.ItemStack held = player.getItemInHand(hand);
		if (ChallengerSigil.isSigil(held)) {
			// Route to challenger trial
			ChallengerTrialManager.get().initiateTrial(player, orderName, level);
		} else if (held.is(net.minecraft.world.item.Items.DIAMOND_BLOCK)) {
			// Diamond-block heal during an active trial
			ChallengerTrialManager ctm = ChallengerTrialManager.get();
			ChallengerTrialManager.TrialState state = ctm.get(orderName);
			if (state != null && state.phase == ChallengerTrialManager.Phase.ACTIVE) {
				ctm.healCube(orderName, 30, level.getServer());
				held.shrink(1);

				// Bug 9B: golden glow on cube for 2 seconds
				if (state.cubeDisplay != null && !state.cubeDisplay.isRemoved()) {
					state.cubeDisplay.setGlowColorOverride(0xFFD700);
					state.cubeDisplay.setGlowingTag(true);
					scheduler.schedule(new freq.ascension.api.DelayedTask(40, () -> {
						if (!state.cubeDisplay.isRemoved()) {
							state.cubeDisplay.setGlowingTag(false);
							state.cubeDisplay.setGlowColorOverride(-1);
						}
					}));
				}
				// Bug 9B: spawn heart particles around the cube
				PoiManager healPoi = PoiManager.get(level.getServer());
				net.minecraft.core.BlockPos cubePos = healPoi.getPoiPosition(orderName);
				if (cubePos != null) {
					level.sendParticles(net.minecraft.core.particles.ParticleTypes.HEART,
							cubePos.getX() + 0.5, cubePos.getY() + 1.0, cubePos.getZ() + 0.5,
							10, 0.6, 0.5, 0.6, 0.05);
				}
			} else {
				// Not an active trial — fall through to promotion flow
				PromotionHandler.handlePromotionRequest(player, orderName, level.getServer());
			}
		} else {
			// Route to promotion confirmation flow
			PromotionHandler.handlePromotionRequest(player, orderName, level.getServer());
		}
	}
}
