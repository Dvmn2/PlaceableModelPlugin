package net.dvmn2.placeablemodelplugin.util;

import net.dvmn2.placeablemodelplugin.PlaceableModelPlugin;
import org.bukkit.NamespacedKey;

/**
 * Единая точка для всех ключей PersistentDataContainer, которыми помечаются
 * сущности плагина (ItemDisplay и Interaction).
 */
public final class PluginKeys {

    public static final String TYPE_DISPLAY = "placed_display";
    public static final String TYPE_INTERACTION = "placed_interaction";

    private PluginKeys() {
    }

    private static NamespacedKey key(String name) {
        return new NamespacedKey(PlaceableModelPlugin.getInstance(), name);
    }

    /** Тип сущности: {@link #TYPE_DISPLAY} или {@link #TYPE_INTERACTION}. */
    public static NamespacedKey type() {
        return key("type");
    }

    /** Общий UUID пары display + interaction. */
    public static NamespacedKey modelId() {
        return key("model_id");
    }

    /** UUID парной сущности (у display — interaction, у interaction — display). */
    public static NamespacedKey partner() {
        return key("partner");
    }

    /** Сериализованный исходный ItemStack (хранится на interaction). */
    public static NamespacedKey item() {
        return key("item");
    }
}
