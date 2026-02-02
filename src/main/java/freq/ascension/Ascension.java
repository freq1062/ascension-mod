package freq.ascension;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import freq.ascension.api.TaskScheduler;
import freq.ascension.commands.AscensionMenuOpenCommand;
import freq.ascension.commands.BindCommand;
import freq.ascension.commands.GetInfluenceCommand;
import freq.ascension.commands.GiveInfluenceCommand;
import freq.ascension.commands.UnbindCommand;
import freq.ascension.commands.WithdrawCommand;
import freq.ascension.commands.SetOrderCommand;
import freq.ascension.managers.AbilityManager;
import freq.ascension.managers.InfluenceManager;
import freq.ascension.managers.SpellCooldownManager;
import freq.ascension.registry.OrderRegistry;

public class Ascension implements ModInitializer {
	public static final String MOD_ID = "ascension";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
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
		});

		AbilityManager.init();
		SpellCooldownManager.updateActiveSpells();
		InfluenceManager.init();
		OrderRegistry.registerAllSpells();

		// Register Commands
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			AscensionMenuOpenCommand.register(dispatcher);
			BindCommand.register(dispatcher);
			GetInfluenceCommand.register(dispatcher);
			GiveInfluenceCommand.register(dispatcher);
			UnbindCommand.register(dispatcher);
			WithdrawCommand.register(dispatcher);
			SetOrderCommand.register(dispatcher);
				// Register click-action command for book UI
				freq.ascension.commands.AscensionActionCommand.register(dispatcher);
		});

		LOGGER.info("Ascension SMP Mod Loaded!");
	}

	public static MinecraftServer getServer() {
		return server;
	}
}