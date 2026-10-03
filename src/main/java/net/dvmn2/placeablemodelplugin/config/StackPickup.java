package net.dvmn2.placeablemodelplugin.config;

import java.util.Locale;

/**
 * Что происходит при подборе модели, над которой стоят другие модели.
 */
public enum StackPickup {
    /** Подбирается выбранная модель и все модели над ней; игрок получает все предметы. */
    ALL,
    /** Подобрать можно только верхнюю модель; нижние защищены, пока над ними что-то стоит. */
    TOP_ONLY;

    /**
     * @return режим по имени из конфига (регистр не важен) или null
     */
    public static StackPickup parse(String name) {
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
