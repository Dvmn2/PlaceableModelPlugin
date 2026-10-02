package net.dvmn2.placeablemodelplugin.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.dvmn2.placeablemodelplugin.Lang;
import net.dvmn2.placeablemodelplugin.PlaceableModelPlugin;

/**
 * {@code /placeablemodel reload} — перечитать config.yml без перезапуска сервера.
 * Регистрируется через LifecycleEvents.COMMANDS, записи в plugin.yml не нужно.
 */
public final class PlaceableModelCommand {

    private PlaceableModelCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> build(PlaceableModelPlugin plugin) {
        return Commands.literal("placeablemodel")
                .requires(source -> source.getSender().hasPermission(PlaceableModelPlugin.PERM_RELOAD))
                .executes(PlaceableModelCommand::usage)
                .then(Commands.literal("reload").executes(ctx -> reload(ctx, plugin)))
                .build();
    }

    private static int usage(CommandContext<CommandSourceStack> ctx) {
        Lang.send(ctx.getSource().getSender(), Lang.Key.USAGE);
        return Command.SINGLE_SUCCESS;
    }

    private static int reload(CommandContext<CommandSourceStack> ctx, PlaceableModelPlugin plugin) {
        int count = plugin.reloadAll();
        Lang.send(ctx.getSource().getSender(), Lang.Key.RELOADED, count);
        return Command.SINGLE_SUCCESS;
    }
}
