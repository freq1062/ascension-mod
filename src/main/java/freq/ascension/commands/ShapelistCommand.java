package freq.ascension.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;

import freq.ascension.managers.AscensionData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;

import java.util.List;

public class ShapelistCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("shlist")
                .executes(ShapelistCommand::run));
    }

    private static int run(CommandContext<CommandSourceStack> context) {
        if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
            context.getSource().sendFailure(Component.literal("This command can only be used by a player."));
            return 0;
        }

        AscensionData data = (AscensionData) player;

        // Check if player has Magic combat equipped
        freq.ascension.orders.Order combat = data.getCombat();
        if (combat == null || !"magic".equals(combat.getOrderName())) {
            player.sendSystemMessage(Component.literal("§cYou need to equip Magic Combat to use this command."));
            return 0;
        }

        List<String> history = data.getShapeshiftHistory();

        if (history.isEmpty()) {
            player.sendSystemMessage(Component.literal("§7Your shapeshift history is empty. Kill a mob to add it."));
            return 1;
        }

        player.sendSystemMessage(Component.literal("§d--- Shapeshift History ---"));
        // Last entry = next to transform into (TOP); first entry = oldest (BOTTOM)
        for (int i = history.size() - 1; i >= 0; i--) {
            String id = history.get(i);
            ResourceLocation rl = ResourceLocation.parse(id);
            String displayName = BuiltInRegistries.ENTITY_TYPE.getOptional(rl)
                    .map(EntityType::getDescription)
                    .map(Component::getString)
                    .orElse(id);
            String prefix = (i == history.size() - 1) ? "§a► " : "§7  ";
            player.sendSystemMessage(Component.literal(prefix + displayName));
        }
        player.sendSystemMessage(Component.literal("§7(§a►§7 = next transformation)"));
        return 1;
    }
}
