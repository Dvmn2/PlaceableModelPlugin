package net.dvmn2.placeablemodelplugin.config;

import org.bukkit.block.BlockFace;

import java.util.Locale;

/**
 * Тип поверхности, на которую ставится модель. Определяется гранью кликнутого блока.
 */
public enum Surface {
    /** Верхняя грань блока. */
    FLOOR,
    /** Любая боковая грань блока. */
    WALL,
    /** Нижняя грань блока. */
    CEILING;

    public static Surface of(BlockFace face) {
        return switch (face) {
            case UP -> FLOOR;
            case DOWN -> CEILING;
            default -> WALL;
        };
    }

    /**
     * @return поверхность по имени из конфига (регистр не важен) или null
     */
    public static Surface parse(String name) {
        if (name == null) {
            return null;
        }
        try {
            return valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
