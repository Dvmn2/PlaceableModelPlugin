package net.dvmn2.placeablemodelplugin.config;

/**
 * Четыре способа "распознать" кастомный предмет — соответствуют вкладкам
 * ResourceManager. Порядок констант = порядок проверки.
 */
public enum DefinitionType {
    CUSTOM_MODEL_DATA("custom_model_data"),
    ITEM_MODEL("item_model"),
    EQUIPABLE("equipable"),
    RENAME("rename", "Rename");

    private final String[] configKeys;

    DefinitionType(String... configKeys) {
        this.configKeys = configKeys;
    }

    /** Допустимые имена массива в config.yml (первое — основное). */
    public String[] configKeys() {
        return configKeys;
    }
}
