package freq.ascension;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import freq.ascension.api.TaskScheduler;
import freq.ascension.commands.*;
import freq.ascension.registry.*;
import freq.ascension.managers.*;
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
		ChallengerTrialManager.get().registerEventListeners();
		PoiManager.init();
		DisguiseManager.init();

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
			ClearStunCommand.register(dispatcher);
		});

		LOGGER.info("Ascension SMP Mod Loaded!");
	}

	public static MinecraftServer getServer() {
		return server;
	}
}
