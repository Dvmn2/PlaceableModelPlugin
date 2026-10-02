package net.dvmn2.placeablemodelplugin.manager;

import net.dvmn2.placeablemodelplugin.PlaceableModelPlugin;
import net.dvmn2.placeablemodelplugin.config.DefinitionRegistry;
import net.dvmn2.placeablemodelplugin.config.PlaceableDefinition;
import net.dvmn2.placeablemodelplugin.config.PlaceableDefinition.SoundSpec;
import net.dvmn2.placeablemodelplugin.util.PluginKeys;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Map;
import java.util.UUID;

/**
 * Создаёт и убирает пары "ItemDisplay (внешний вид) + Interaction (хитбокс)".
 * <p>
 * Display-сущности нельзя выбрать кликом, поэтому для каждой модели рядом
 * спавнится невидимая Interaction-сущность. Исходный предмет (со всеми
 * компонентами) хранится в PDC Interaction и возвращается игроку при подъёме
 * без потерь. Состояния в файлах нет — всё лежит в самих сущностях.
 */
public final class PlacedModelManager {

    private final PlaceableModelPlugin plugin;
    private final DefinitionRegistry registry;

    public PlacedModelManager(PlaceableModelPlugin plugin, DefinitionRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    // ------------------------------------------------------------------
    //  Установка
    // ------------------------------------------------------------------

    /**
     * Ставит модель на точку {@code point} грани {@code face}.
     *
     * @return true, если пара сущностей создана (вызывающий тратит предмет)
     */
    public boolean place(Player player, Location point, BlockFace face, ItemStack held, PlaceableDefinition def) {
        World world = point.getWorld();
        if (world == null) {
            return false;
        }

        ItemStack single = held.asOne();
        float yaw = player.getLocation().getYaw();

        // Смещение считается в системе координат модели БЕЗ наклона, а наклон (pitch)
        // применяется поверх — модель наклоняется вокруг собственного центра, и
        // положение центра от pitch не зависит.
        Quaternionf baseRotation = computeRotation(face, yaw, def);
        Vector3f offsetWorld = baseRotation.transform(new Vector3f(def.offset()));
        Quaternionf rotation = new Quaternionf(baseRotation)
                .rotateX((float) Math.toRadians(def.pitch()));

        Location origin = new Location(world, point.getX(), point.getY(), point.getZ());
        Location visualCenter = origin.clone().add(offsetWorld.x, offsetWorld.y, offsetWorld.z);

        // У Interaction бокс "растёт" вверх от позиции сущности, поэтому смещаем
        // вниз на половину высоты, чтобы центр бокса совпал с центром модели.
        Location interactionLoc = visualCenter.clone()
                .add(0, def.interactionYOffset() - def.interactionHeight() / 2.0, 0);

        UUID modelId = UUID.randomUUID();

        ItemDisplay display = world.spawn(origin, ItemDisplay.class, entity -> {
            entity.setItemStack(single);
            entity.setItemDisplayTransform(def.itemTransform());
            entity.setBillboard(Display.Billboard.FIXED);
            entity.setViewRange(def.viewRange());
            if (def.hasBrightness()) {
                entity.setBrightness(new Display.Brightness(def.blockLight(), def.skyLight()));
            }
            entity.setTransformation(new Transformation(
                    offsetWorld, rotation, new Vector3f(def.scale()), new Quaternionf()));

            PersistentDataContainer pdc = entity.getPersistentDataContainer();
            pdc.set(PluginKeys.type(), PersistentDataType.STRING, PluginKeys.TYPE_DISPLAY);
            pdc.set(PluginKeys.modelId(), PersistentDataType.STRING, modelId.toString());
        });

        Interaction interaction;
        try {
            interaction = world.spawn(interactionLoc, Interaction.class, entity -> {
                entity.setInteractionWidth(def.interactionWidth());
                entity.setInteractionHeight(def.interactionHeight());
                entity.setResponsive(def.responsive());

                PersistentDataContainer pdc = entity.getPersistentDataContainer();
                pdc.set(PluginKeys.type(), PersistentDataType.STRING, PluginKeys.TYPE_INTERACTION);
                pdc.set(PluginKeys.modelId(), PersistentDataType.STRING, modelId.toString());
                pdc.set(PluginKeys.partner(), PersistentDataType.STRING, display.getUniqueId().toString());
                pdc.set(PluginKeys.item(), PersistentDataType.BYTE_ARRAY, single.serializeAsBytes());
            });
        } catch (RuntimeException ex) {
            display.remove(); // не оставляем "висящий" display без хитбокса
            throw ex;
        }

        display.getPersistentDataContainer().set(
                PluginKeys.partner(), PersistentDataType.STRING, interaction.getUniqueId().toString());

        playSound(visualCenter, def.placeSound());
        return true;
    }

    /**
     * Ориентация модели. Вращение зашито в transformation, а не в yaw сущности,
     * потому что нужно ещё и "класть" модель на стену/потолок.
     * <ul>
     *     <li>UP: стоит на полу, лицом к игроку;</li>
     *     <li>DOWN: висит вверх ногами;</li>
     *     <li>боковые грани: верх модели смотрит от стены.</li>
     * </ul>
     * При {@code align_to_surface: false} модель всегда стоит вертикально.
     * Наклон {@code display.pitch} сюда не входит — он добавляется отдельно и
     * никак не связан с pitch игрока (используется только yaw игрока).
     */
    private Quaternionf computeRotation(BlockFace face, float playerYaw, PlaceableDefinition def) {
        float facingYaw = playerYaw + 180f + def.rotationOffset();
        Quaternionf q = new Quaternionf();

        if (!def.alignToSurface() || face == BlockFace.UP) {
            return q.rotateY((float) Math.toRadians(-facingYaw));
        }
        if (face == BlockFace.DOWN) {
            return q.rotateY((float) Math.toRadians(-facingYaw)).rotateX((float) Math.PI);
        }

        // Боковая грань: локальный +Y модели -> нормаль грани.
        float theta = (float) Math.atan2(face.getModX(), face.getModZ());
        return q.rotateY(theta)
                .rotateX((float) (Math.PI / 2.0))
                .rotateY((float) Math.PI)                                   // лицо модели смотрит вверх
                .rotateY((float) Math.toRadians(-def.rotationOffset()));    // доп. поворот вдоль стены
    }

    // ------------------------------------------------------------------
    //  Подъём
    // ------------------------------------------------------------------

    /**
     * Убирает модель и возвращает игроку исходный предмет.
     */
    public void pickup(Player player, Interaction interaction) {
        PersistentDataContainer pdc = interaction.getPersistentDataContainer();

        ItemStack item = null;
        byte[] bytes = pdc.get(PluginKeys.item(), PersistentDataType.BYTE_ARRAY);
        if (bytes != null) {
            try {
                item = ItemStack.deserializeBytes(bytes);
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Failed to deserialize stored item: " + ex.getMessage());
            }
        }

        Entity display = findDisplay(interaction, pdc);
        if (item == null && display instanceof ItemDisplay itemDisplay) {
            item = itemDisplay.getItemStack(); // запасной вариант
        }
        if (item == null || item.getType().isAir()) {
            // Данные повреждены — просто убираем сущности, чтобы не мешали.
            interaction.remove();
            if (display != null) {
                display.remove();
            }
            return;
        }

        Location soundLoc = interaction.getLocation().add(0, interaction.getInteractionHeight() / 2.0, 0);
        // Запись могла быть удалена из конфига после установки — тогда звук по умолчанию.
        PlaceableDefinition def = registry.find(item);
        SoundSpec sound = def != null ? def.pickupSound() : registry.defaultPickupSound();

        interaction.remove();
        if (display != null) {
            display.remove();
        }

        giveOrDrop(player, item);
        playSound(soundLoc, sound);
    }

    /**
     * Находит парный ItemDisplay: сначала по UUID, затем (запасной вариант) по
     * общему model_id среди ближайших сущностей.
     */
    private Entity findDisplay(Interaction interaction, PersistentDataContainer pdc) {
        String partnerRaw = pdc.get(PluginKeys.partner(), PersistentDataType.STRING);
        if (partnerRaw != null) {
            try {
                Entity partner = Bukkit.getEntity(UUID.fromString(partnerRaw));
                if (partner instanceof ItemDisplay) {
                    return partner;
                }
            } catch (IllegalArgumentException ignored) {
                // повреждённый UUID — ищем по соседству
            }
        }

        String modelId = pdc.get(PluginKeys.modelId(), PersistentDataType.STRING);
        if (modelId == null) {
            return null;
        }
        for (Entity nearby : interaction.getNearbyEntities(4, 4, 4)) {
            if (!(nearby instanceof ItemDisplay)) {
                continue;
            }
            PersistentDataContainer other = nearby.getPersistentDataContainer();
            if (PluginKeys.TYPE_DISPLAY.equals(other.get(PluginKeys.type(), PersistentDataType.STRING))
                    && modelId.equals(other.get(PluginKeys.modelId(), PersistentDataType.STRING))) {
                return nearby;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    //  Вспомогательное
    // ------------------------------------------------------------------

    public void consumeOne(Player player) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        ItemStack current = player.getInventory().getItemInMainHand();
        if (current.getAmount() <= 1) {
            player.getInventory().setItemInMainHand(null);
        } else {
            current.setAmount(current.getAmount() - 1);
        }
    }

    private void giveOrDrop(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        for (ItemStack extra : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), extra);
        }
    }

    private void playSound(Location location, SoundSpec spec) {
        if (spec == null || location.getWorld() == null) {
            return;
        }
        location.getWorld().playSound(location, spec.key(), SoundCategory.BLOCKS, spec.volume(), spec.pitch());
    }
}
