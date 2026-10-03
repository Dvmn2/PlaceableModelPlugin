package net.dvmn2.placeablemodelplugin.config;

import net.dvmn2.placeablemodelplugin.config.PlaceableDefinition.SoundSpec;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Загружает 4 массива записей из config.yml и по ItemStack находит подходящую
 * {@link PlaceableDefinition}. Поиск — O(1) по значению; при перезагрузке индекс
 * подменяется целиком одним присваиванием.
 */
public final class DefinitionRegistry {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private volatile Map<DefinitionType, Map<String, List<PlaceableDefinition>>> index = emptyIndex();
    private volatile SoundSpec defaultPlaceSound;
    private volatile SoundSpec defaultPickupSound;
    private volatile boolean placeThroughModels = true;

    public SoundSpec defaultPlaceSound() {
        return defaultPlaceSound;
    }

    public SoundSpec defaultPickupSound() {
        return defaultPickupSound;
    }

    public boolean placeThroughModels() {
        return placeThroughModels;
    }

    // ------------------------------------------------------------------
    //  Поиск
    // ------------------------------------------------------------------

    /**
     * @return запись, подходящая предмету, или null. Первой побеждает запись из
     * custom_model_data, затем item_model, equipable, rename.
     */
    public PlaceableDefinition find(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        Map<DefinitionType, Map<String, List<PlaceableDefinition>>> snapshot = index;

        for (DefinitionType type : DefinitionType.values()) {
            Map<String, List<PlaceableDefinition>> byValue = snapshot.get(type);
            if (byValue.isEmpty()) {
                continue;
            }
            String actual = extract(type, meta);
            if (actual == null) {
                continue;
            }
            List<PlaceableDefinition> candidates = byValue.get(actual);
            if (candidates == null) {
                continue;
            }
            for (PlaceableDefinition def : candidates) {
                if (def.baseItem() == null || def.baseItem() == item.getType()) {
                    return def;
                }
            }
        }
        return null;
    }

    private static String extract(DefinitionType type, ItemMeta meta) {
        return switch (type) {
            case CUSTOM_MODEL_DATA -> {
                if (!meta.hasCustomModelDataComponent()) {
                    yield null;
                }
                List<String> strings = meta.getCustomModelDataComponent().getStrings();
                yield strings.isEmpty() ? null : strings.get(0);
            }
            case ITEM_MODEL -> meta.hasItemModel() ? meta.getItemModel().toString() : null;
            case EQUIPABLE -> {
                if (!meta.hasEquippable()) {
                    yield null;
                }
                NamespacedKey model = meta.getEquippable().getModel();
                yield model == null ? null : model.toString();
            }
            case RENAME -> meta.hasDisplayName() && meta.displayName() != null
                    ? PLAIN.serialize(meta.displayName())
                    : null;
        };
    }

    // ------------------------------------------------------------------
    //  Загрузка
    // ------------------------------------------------------------------

    /**
     * (Пере)читывает конфиг.
     *
     * @return общее число загруженных записей
     */
    public int load(FileConfiguration config, Logger log) {
        ConfigurationSection defaults = config.getConfigurationSection("defaults");
        if (defaults == null) {
            defaults = new MemoryConfiguration();
        }

        this.placeThroughModels = config.getBoolean("settings.place_through_models", true);

        Params emptyEntry = new Params(new MemoryConfiguration(), defaults);
        this.defaultPlaceSound = readSound(emptyEntry, "place");
        this.defaultPickupSound = readSound(emptyEntry, "pickup");

        Map<DefinitionType, Map<String, List<PlaceableDefinition>>> fresh = emptyIndex();
        int total = 0;

        for (DefinitionType type : DefinitionType.values()) {
            List<Map<?, ?>> rawEntries = readRawEntries(config, type);
            for (int i = 0; i < rawEntries.size(); i++) {
                String where = type.configKeys()[0] + "[" + i + "]";
                PlaceableDefinition def = parseEntry(type, rawEntries.get(i), defaults, where, log);
                if (def == null) {
                    continue;
                }
                for (String value : def.values()) {
                    fresh.get(type).computeIfAbsent(value, k -> new ArrayList<>()).add(def);
                }
                total++;
            }
        }

        this.index = fresh;
        return total;
    }

    private static List<Map<?, ?>> readRawEntries(FileConfiguration config, DefinitionType type) {
        for (String key : type.configKeys()) {
            if (config.contains(key)) {
                return config.getMapList(key);
            }
        }
        return List.of();
    }

    private PlaceableDefinition parseEntry(DefinitionType type, Map<?, ?> raw, ConfigurationSection defaults,
                                           String where, Logger log) {
        Map<String, Object> copy = new HashMap<>();
        for (Map.Entry<?, ?> e : raw.entrySet()) {
            copy.put(String.valueOf(e.getKey()), e.getValue());
        }
        ConfigurationSection entry = new MemoryConfiguration().createSection("entry", copy);
        Params p = new Params(entry, defaults);

        // --- условие совпадения ---
        Set<String> values = new LinkedHashSet<>();
        Object match = entry.get("match");
        if (match instanceof List<?> list) {
            for (Object o : list) {
                addValue(values, type, o);
            }
        } else {
            addValue(values, type, match);
        }
        if (values.isEmpty()) {
            log.warning("[config] " + where + ": missing or empty 'match' — entry skipped.");
            return null;
        }

        // --- фильтр по типу предмета ---
        Material baseItem = null;
        String baseItemRaw = entry.getString("base_item");
        if (baseItemRaw != null && !baseItemRaw.isBlank()) {
            baseItem = Material.matchMaterial(baseItemRaw.trim());
            if (baseItem == null) {
                log.warning("[config] " + where + ": unknown base_item '" + baseItemRaw + "' — entry skipped.");
                return null;
            }
        }

        // --- допустимые поверхности ---
        Set<Surface> surfaces = readSurfaces(p.get("placement.surfaces"), where, log);

        // --- ItemDisplay ---
        Vector3f scale = readScale(p.get("display.scale"), new Vector3f(1f, 1f, 1f), where, log);
        Vector3f offset = readVector(p.get("display.offset"), new Vector3f(0f, 0.5f, 0f), where + ".display.offset", log);
        float rotationOffset = p.num("display.rotation_offset", 0f);
        float pitch = p.num("display.pitch", 0f);
        boolean align = p.bool("display.align_to_surface", true);
        float viewRange = p.num("display.view_range", 1f);

        ItemDisplay.ItemDisplayTransform transform = ItemDisplay.ItemDisplayTransform.NONE;
        Object transformRaw = p.get("display.item_transform");
        if (transformRaw != null) {
            try {
                transform = ItemDisplay.ItemDisplayTransform.valueOf(
                        String.valueOf(transformRaw).trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                log.warning("[config] " + where + ": unknown item_transform '" + transformRaw + "', using NONE.");
            }
        }

        int blockLight = (int) p.num("display.brightness.block", -1f);
        int skyLight = (int) p.num("display.brightness.sky", -1f);
        if (blockLight < 0 || skyLight < 0) {
            blockLight = -1;
            skyLight = -1;
        } else {
            blockLight = Math.min(15, blockLight);
            skyLight = Math.min(15, skyLight);
        }

        // --- Interaction ---
        float width = Math.max(0.01f, p.num("interaction.width", 1f));
        float height = Math.max(0.01f, p.num("interaction.height", 1f));
        float yOffset = p.num("interaction.y_offset", 0f);
        boolean responsive = p.bool("interaction.responsive", false);

        return new PlaceableDefinition(
                type, Set.copyOf(values), baseItem, surfaces,
                scale, offset, rotationOffset, pitch, align, transform, viewRange, blockLight, skyLight,
                width, height, yOffset, responsive,
                readSound(p, "place"), readSound(p, "pickup"));
    }

    private static void addValue(Set<String> values, DefinitionType type, Object raw) {
        if (raw == null) {
            return;
        }
        String s = String.valueOf(raw);
        if (s.isBlank()) {
            return;
        }
        // item_model / equipable — это ключи ресурсов: "name" == "minecraft:name".
        if ((type == DefinitionType.ITEM_MODEL || type == DefinitionType.EQUIPABLE) && !s.contains(":")) {
            s = "minecraft:" + s.trim();
        }
        values.add(type == DefinitionType.RENAME || type == DefinitionType.CUSTOM_MODEL_DATA ? s : s.trim());
    }

    private static Set<Surface> readSurfaces(Object raw, String where, Logger log) {
        if (raw == null) {
            return EnumSet.allOf(Surface.class);
        }
        List<?> names = raw instanceof List<?> list ? list : List.of(raw);
        Set<Surface> result = EnumSet.noneOf(Surface.class);
        for (Object o : names) {
            Surface surface = Surface.parse(String.valueOf(o));
            if (surface == null) {
                log.warning("[config] " + where + ": unknown surface '" + o + "' (expected floor, wall, ceiling).");
            } else {
                result.add(surface);
            }
        }
        if (result.isEmpty()) {
            log.warning("[config] " + where + ": placement.surfaces has no valid values, all surfaces allowed.");
            return EnumSet.allOf(Surface.class);
        }
        return result;
    }

    private static SoundSpec readSound(Params p, String name) {
        Object key = p.get("sounds." + name + ".sound");
        if (!(key instanceof String s) || s.isBlank()) {
            return null;
        }
        return new SoundSpec(
                s.trim(),
                p.num("sounds." + name + ".volume", 1f),
                p.num("sounds." + name + ".pitch", 1f));
    }

    private static Vector3f readScale(Object raw, Vector3f fallback, String where, Logger log) {
        if (raw instanceof Number n) {
            float f = n.floatValue();
            return new Vector3f(f, f, f);
        }
        return readVector(raw, fallback, where + ".display.scale", log);
    }

    private static Vector3f readVector(Object raw, Vector3f fallback, String where, Logger log) {
        if (raw == null) {
            return new Vector3f(fallback);
        }
        if (raw instanceof List<?> list && list.size() == 3
                && list.stream().allMatch(o -> o instanceof Number)) {
            return new Vector3f(
                    ((Number) list.get(0)).floatValue(),
                    ((Number) list.get(1)).floatValue(),
                    ((Number) list.get(2)).floatValue());
        }
        log.warning("[config] " + where + ": expected [x, y, z], using default.");
        return new Vector3f(fallback);
    }

    private static Map<DefinitionType, Map<String, List<PlaceableDefinition>>> emptyIndex() {
        Map<DefinitionType, Map<String, List<PlaceableDefinition>>> map = new EnumMap<>(DefinitionType.class);
        for (DefinitionType type : DefinitionType.values()) {
            map.put(type, new HashMap<>());
        }
        return map;
    }

    /**
     * Чтение параметра: сначала из записи, затем из секции "defaults".
     */
    private record Params(ConfigurationSection entry, ConfigurationSection defaults) {

        Object get(String path) {
            return entry.contains(path) ? entry.get(path) : defaults.get(path);
        }

        float num(String path, float fallback) {
            return get(path) instanceof Number n ? n.floatValue() : fallback;
        }

        boolean bool(String path, boolean fallback) {
            return get(path) instanceof Boolean b ? b : fallback;
        }
    }
}
