package net.dvmn2.placeablemodelplugin;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.dvmn2.placeablemodelplugin.command.PlaceableModelCommand;
import net.dvmn2.placeablemodelplugin.config.DefinitionRegistry;
import net.dvmn2.placeablemodelplugin.listener.BlockInteractListener;
import net.dvmn2.placeablemodelplugin.listener.EntityInteractListener;
import net.dvmn2.placeablemodelplugin.manager.PlacedModelManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class PlaceableModelPlugin extends JavaPlugin {

    public static final String PERM_PLACE = "placeablemodel.place";
    public static final String PERM_PICKUP = "placeablemodel.pickup";
    public static final String PERM_RELOAD = "placeablemodel.reload";

    private static PlaceableModelPlugin instance;

    private final DefinitionRegistry registry = new DefinitionRegistry();
    private PlacedModelManager manager;

    public PlaceableModelPlugin() {
        // Как и в TableGamesPlugin: команды регистрируются в конструкторе,
        // потому что COMMANDS-событие может прийти раньше onEnable().
        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();
            commands.register(
                    PlaceableModelCommand.build(this),
                    "Reload PlaceableModelPlugin config.",
                    List.of("pmodel"));
        });
    }

    public static PlaceableModelPlugin getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        int loaded = reloadAll();

        this.manager = new PlacedModelManager(this, registry);

        getServer().getPluginManager().registerEvents(new BlockInteractListener(this, registry, manager), this);
        getServer().getPluginManager().registerEvents(new EntityInteractListener(this, registry, manager), this);

        getLogger().info("PlaceableModelPlugin enabled, entries loaded: " + loaded + ".");
    }

    @Override
    public void onDisable() {
        getLogger().info("PlaceableModelPlugin disabled.");
    }

    /**
     * Перечитывает config.yml и пересобирает реестр записей.
     *
     * @return число загруженных записей
     */
    public int reloadAll() {
        reloadConfig();
        Lang.setLanguage(getConfig().getString("settings.language", "auto"));
        return registry.load(getConfig(), getLogger());
    }
}
