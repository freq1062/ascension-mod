package freq.ascension;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

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
import freq.ascension.managers.PlantProximityManager;
import freq.ascension.managers.SpellCooldownManager;
import freq.ascension.registry.OrderRegistry;

// import io.github.retrooper.packetevents.PacketEventsServerMod;
// import io.github.retrooper.packetevents.factory.fabric.FabricPacketEventsAPI;

public class Ascension implements ModInitializer {
	public static final String MOD_ID = "ascension";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final TaskScheduler scheduler = new TaskScheduler();
	// public static FabricPacketEventsAPI api; // PacketEvents temporarily disabled
	private static MinecraftServer server;

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		Config.load();

		// PacketEvents temporarily disabled due to Adventure API dependency issues
		// TODO: Fix PacketEvents dependencies - requires compatible Adventure API
		// api = PacketEventsServerMod.constructApi(MOD_ID);
		// PacketEvents.setAPI(api);
		// api.load();

		// Register a server tick event to process scheduled tasks
		ServerTickEvents.END_SERVER_TICK.register((MinecraftServer s) -> {
			server = s;
			scheduler.tick(s.getTickCount());
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
		AbilityManager.init();
		InfluenceManager.init();
		PlantProximityManager.init();
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