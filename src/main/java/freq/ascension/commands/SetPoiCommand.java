package freq.ascension.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import freq.ascension.Ascension;
import freq.ascension.managers.ChallengerTrialManager;
import freq.ascension.managers.PoiManager;
import freq.ascension.orders.Order;
import freq.ascension.registry.OrderRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;

/**
 * Implements {@code /setpoi <order> <x> <y> <z>},
 * {@code /setpoiradius <order> <radius>}, and {@code /delpoi <order>}.
 *
 * <p>
 * Requires operator level 2. The /setpoi command captures a terrain snapshot
 * around the
 * target position and spawns a spinning block display + interaction entity.
 */
public class SetPoiCommand {

    private static final SuggestionProvider<CommandSourceStack> ORDER_SUGGESTIONS = (ctx, builder) -> {
        List<String> orders = new ArrayList<>();
        for (Order order : OrderRegistry.iterable()) {
            orders.add(order.getOrderName());
        }
        return SharedSuggestionProvider.suggest(orders, builder);
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("setpoi")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("order", StringArgumentType.word())
                        .suggests(ORDER_SUGGESTIONS)
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(SetPoiCommand::setPoiExecute))));

        dispatcher.register(Commands.literal("setpoiradius")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("order", StringArgumentType.word())
                        .suggests(ORDER_SUGGESTIONS)
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 20))
                                .executes(SetPoiCommand::setPoiRadiusExecute))));

        dispatcher.register(Commands.literal("delpoi")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("order", StringArgumentType.word())
                        .suggests(ORDER_SUGGESTIONS)
                        .executes(SetPoiCommand::delPoiExecute)));
    }

    private static int setPoiExecute(CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        String orderName = StringArgumentType.getString(ctx, "order").toLowerCase();
        Order order = OrderRegistry.get(orderName);
        if (order == null) {
            ctx.getSource().sendFailure(Component.literal("§cUnknown order: '" + orderName + "'."));
            return 0;
        }

        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        MinecraftServer server = ctx.getSource().getServer();
        ServerLevel level = ctx.getSource().getLevel();

        PoiManager poi = PoiManager.get(server);
        int radius = poi.getPoiRadius(orderName);
        List<PoiManager.SnapshotEntry> snapshot = PoiManager.captureSnapshot(level, pos, radius);

        poi.setPoiData(orderName, pos, level.dimension().location().toString(), radius, snapshot);
        poi.spawnPoiEntities(orderName, level, pos, Ascension.scheduler);
        // Bug 8: move cooldown display to the new POI position if one exists
        ChallengerTrialManager.get().repositionCooldownDisplay(orderName, pos, server);

        ctx.getSource().sendSuccess(
                () -> Component.literal("§aPOI set for §e" + orderName + "§a at " +
                        pos.getX() + ", " + pos.getY() + ", " + pos.getZ() +
                        " §7(radius=" + radius + ", snapshot=" + snapshot.size() + " blocks)"),
                true);
        return 1;
    }

    private static int setPoiRadiusExecute(CommandContext<CommandSourceStack> ctx) {
        String orderName = StringArgumentType.getString(ctx, "order").toLowerCase();
        Order order = OrderRegistry.get(orderName);
        if (order == null) {
            ctx.getSource().sendFailure(Component.literal("§cUnknown order: '" + orderName + "'."));
            return 0;
        }

        int radius = IntegerArgumentType.getInteger(ctx, "radius");
        MinecraftServer server = ctx.getSource().getServer();
        PoiManager.get(server).setPoiRadius(orderName, radius);

        ctx.getSource().sendSuccess(
                () -> Component.literal("§aPOI radius for §e" + orderName + "§a set to " + radius + "."),
                true);
        return 1;
    }

    private static int delPoiExecute(CommandContext<CommandSourceStack> ctx) {
        String orderName = StringArgumentType.getString(ctx, "order").toLowerCase();
        Order order = OrderRegistry.get(orderName);
        if (order == null) {
            ctx.getSource().sendFailure(Component.literal("§cUnknown order: '" + orderName + "'."));
            return 0;
        }

        MinecraftServer server = ctx.getSource().getServer();
        PoiManager poi = PoiManager.get(server);
        ServerLevel level = poi.getPoiLevel(server, orderName);

        if (level != null) {
            // First stop any tasks and remove entities from the world
            poi.removePreviousEntities(orderName, level);
        }

        // Then clear data from saved state
        poi.clearPoiData(orderName);

        ctx.getSource().sendSuccess(
                () -> Component
                        .literal("§aPOI for §e" + orderName + "§a has been deleted from the world and saved data."),
                true);
        return 1;
    }
}
