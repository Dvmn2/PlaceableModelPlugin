package net.dvmn2.placeablemodelplugin.config;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ItemDisplay;
import org.joml.Vector3f;

import java.util.Set;

/**
 * Одна запись из config.yml: условие совпадения + все параметры ItemDisplay
 * и Interaction.
 */
public record PlaceableDefinition(
        DefinitionType type,
        Set<String> values,
        Material baseItem,            // null = любой тип предмета
        Set<Surface> surfaces,        // на какие поверхности можно ставить

        // ItemDisplay
        Vector3f scale,
        Vector3f offset,
        float rotationOffset,
        float pitch,                  // наклон модели, градусы (не зависит от pitch игрока)
        boolean alignToSurface,
        ItemDisplay.ItemDisplayTransform itemTransform,
        float viewRange,
        int blockLight,               // -1 = не задано
        int skyLight,                 // -1 = не задано

        // Interaction
        float interactionWidth,
        float interactionHeight,
        float interactionYOffset,
        boolean responsive,

        // Звуки (null = выключен)
        SoundSpec placeSound,
        SoundSpec pickupSound
) {
    public record SoundSpec(String key, float volume, float pitch) {
    }

    /** Можно ли ставить модель на указанную грань блока. */
    public boolean allows(BlockFace face) {
        return surfaces.contains(Surface.of(face));
    }

    public boolean hasBrightness() {
        return blockLight >= 0 && skyLight >= 0;
    }
}
