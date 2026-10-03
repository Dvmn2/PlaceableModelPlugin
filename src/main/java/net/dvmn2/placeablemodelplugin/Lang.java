package net.dvmn2.placeablemodelplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * RU/EN локализация. Язык задаётся в config.yml (settings.language):
 * "ru" / "en" — фиксированный; "auto" — по клиентской локали игрока.
 */
public final class Lang {

    public enum Key {
        NO_PERMISSION_PLACE,
        NO_PERMISSION_PICKUP,
        SURFACE_NOT_ALLOWED,
        STACK_BLOCKED,
        STACK_LIMIT,
        PICKUP_BLOCKED_STACK,
        RELOADED,
        USAGE
    }

    private static final Map<Key, String> RU = new EnumMap<>(Key.class);
    private static final Map<Key, String> EN = new EnumMap<>(Key.class);

    static {
        RU.put(Key.NO_PERMISSION_PLACE, "§cУ вас нет прав, чтобы ставить этот предмет.");
        RU.put(Key.NO_PERMISSION_PICKUP, "§cУ вас нет прав, чтобы подбирать этот предмет.");
        RU.put(Key.SURFACE_NOT_ALLOWED, "§cЭтот предмет нельзя поставить на эту поверхность.");
        RU.put(Key.STACK_BLOCKED, "§cСверху нет места для установки.");
        RU.put(Key.STACK_LIMIT, "§cДостигнута максимальная высота стопки.");
        RU.put(Key.PICKUP_BLOCKED_STACK, "§cСначала уберите предметы, стоящие сверху.");
        RU.put(Key.RELOADED, "§aКонфиг перезагружен. Загружено записей: %d.");
        RU.put(Key.USAGE, "§cИспользование: /placeablemodel reload");

        EN.put(Key.NO_PERMISSION_PLACE, "§cYou don't have permission to place this item.");
        EN.put(Key.NO_PERMISSION_PICKUP, "§cYou don't have permission to pick this item up.");
        EN.put(Key.SURFACE_NOT_ALLOWED, "§cThis item cannot be placed on this surface.");
        EN.put(Key.STACK_BLOCKED, "§cThere is no room to place this on top.");
        EN.put(Key.STACK_LIMIT, "§cMaximum stack height reached.");
        EN.put(Key.PICKUP_BLOCKED_STACK, "§cRemove the items on top first.");
        EN.put(Key.RELOADED, "§aConfig reloaded. Entries loaded: %d.");
        EN.put(Key.USAGE, "§cUsage: /placeablemodel reload");

        for (Key key : Key.values()) {
            if (!RU.containsKey(key) || !EN.containsKey(key)) {
                throw new IllegalStateException("Missing translation for " + key);
            }
        }
    }

    private static volatile String configuredLanguage = "auto";

    private Lang() {
    }

    public static void setLanguage(String language) {
        configuredLanguage = (language == null || language.isBlank())
                ? "auto"
                : language.toLowerCase(Locale.ROOT);
    }

    public static String get(Key key, CommandSender sender, Object... args) {
        Map<Key, String> table = resolveTable(sender);
        String template = table.getOrDefault(key, EN.get(key));
        return args.length == 0 ? template : String.format(Locale.US, template, args);
    }

    public static Component component(Key key, CommandSender sender, Object... args) {
        return LegacyComponentSerializer.legacySection().deserialize(get(key, sender, args));
    }

    public static void sendActionBar(Player recipient, Key key, Object... args) {
        recipient.sendActionBar(component(key, recipient, args));
    }

    public static void send(CommandSender recipient, Key key, Object... args) {
        recipient.sendMessage(component(key, recipient, args));
    }

    private static Map<Key, String> resolveTable(CommandSender sender) {
        return switch (configuredLanguage) {
            case "ru" -> RU;
            case "en" -> EN;
            default -> sender instanceof Player player
                    ? ("ru".equalsIgnoreCase(player.locale().getLanguage()) ? RU : EN)
                    : EN;
        };
    }
}
