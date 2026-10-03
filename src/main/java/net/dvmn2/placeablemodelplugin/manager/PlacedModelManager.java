package net.dvmn2.placeablemodelplugin.manager;

import net.dvmn2.placeablemodelplugin.Lang;
import net.dvmn2.placeablemodelplugin.PlaceableModelPlugin;
import net.dvmn2.placeablemodelplugin.config.DefinitionRegistry;
import net.dvmn2.placeablemodelplugin.config.PlaceableDefinition;
import net.dvmn2.placeablemodelplugin.config.PlaceableDefinition.SoundSpec;
import net.dvmn2.placeablemodelplugin.config.StackPickup;
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
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
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

    /** Максимум моделей, которые просматриваются в одной стопке (защита от бесконечного цикла). */
    private static final int MAX_STACK_SCAN = 64;

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
        Quaternionf baseRotation = computeRotation(face, player.getLocation().getYaw(), def);
        Location origin = new Location(world, point.getX(), point.getY(), point.getZ());
        return spawnPair(origin, baseRotation, held, def);
    }

    /**
     * Ставит модель поверх уже стоящей: низ хитбокса новой модели совпадает
     * с верхом хитбокса {@code below}. По горизонтали модель центрируется по
     * {@code below}, ориентация — как при установке на пол.
     *
     * @return true, если пара сущностей создана (вызывающий тратит предмет)
     */
    public boolean placeOnTop(Player player, Interaction below, ItemStack held, PlaceableDefinition def) {
        World world = below.getWorld();
        Location belowLoc = below.getLocation();
        double interactionBottom = belowLoc.getY() + below.getInteractionHeight();

        Quaternionf baseRotation = computeRotation(BlockFace.UP, player.getLocation().getYaw(), def);
        Vector3f offsetWorld = baseRotation.transform(new Vector3f(def.offset()));

        // Обратный расчёт к spawnPair: нужно, чтобы
        //   interactionLoc.y = visualCenter.y + yOffset - height / 2 = interactionBottom
        double centerY = interactionBottom - def.interactionYOffset() + def.interactionHeight() / 2.0;
        Location origin = new Location(world,
                belowLoc.getX() - offsetWorld.x,
                centerY - offsetWorld.y,
                belowLoc.getZ() - offsetWorld.z);
        return spawnPair(origin, baseRotation, held, def);
    }

    /**
     * Спавнит пару ItemDisplay + Interaction.
     *
     * @param origin       позиция display-сущности
     * @param baseRotation ориентация модели без наклона (pitch)
     */
    private boolean spawnPair(Location origin, Quaternionf baseRotation, ItemStack held, PlaceableDefinition def) {
        World world = origin.getWorld();
        ItemStack single = held.asOne();

        // Смещение считается в системе координат модели БЕЗ наклона, а наклон (pitch)
        // применяется поверх — модель наклоняется вокруг собственного центра, и
        // положение центра от pitch не зависит.
        Vector3f offsetWorld = baseRotation.transform(new Vector3f(def.offset()));
        Quaternionf rotation = new Quaternionf(baseRotation)
                .rotateX((float) Math.toRadians(def.pitch()));

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

    // ------------------------------------------------------------------
    //  Стопка (предметы друг на друге)
    // ------------------------------------------------------------------

    /**
     * Проверяет, что поставленная модель {@code interaction} — тот же предмет,
     * что описан записью {@code def} (сравнивается запись конфига, а не NBT).
     */
    public boolean isSameModel(Interaction interaction, PlaceableDefinition def) {
        ItemStack stored = readStoredItem(interaction.getPersistentDataContainer());
        return stored != null && registry.find(stored) == def;
    }

    /**
     * Поднимается по стопке над {@code start}, пока над хитбоксом есть другая
     * модель того же предмета, и возвращает верхнюю.
     *
     * @return верхняя модель стопки или null, если над стопкой стоит модель
     * другого предмета (места нет) либо стопка выше предела просмотра
     */
    public Interaction findStackTop(Interaction start, PlaceableDefinition def) {
        Interaction current = start;
        for (int i = 0; i < MAX_STACK_SCAN; i++) {
            Interaction above = findAdjacent(current, true);
            if (above == null) {
                return current;
            }
            if (!isSameModel(above, def)) {
                return null;
            }
            current = above;
        }
        return null;
    }

    /**
     * Число подряд стоящих моделей того же предмета, заканчивая моделью {@code top}
     * и считая вниз. Модель другого предмета под стопкой (например, стол) не считается.
     */
    public int stackHeight(Interaction top, PlaceableDefinition def) {
        int count = 1;
        Interaction current = top;
        for (int i = 0; i < MAX_STACK_SCAN; i++) {
            Interaction below = findAdjacent(current, false);
            if (below == null || !isSameModel(below, def)) {
                break;
            }
            count++;
            current = below;
        }
        return count;
    }

    /**
     * Все модели, стоящие непосредственно друг на друге над {@code start}
     * (снизу вверх), независимо от предмета.
     */
    private List<Interaction> collectAbove(Interaction start) {
        List<Interaction> result = new ArrayList<>();
        Interaction current = start;
        for (int i = 0; i < MAX_STACK_SCAN; i++) {
            Interaction above = findAdjacent(current, true);
            if (above == null) {
                break;
            }
            result.add(above);
            current = above;
        }
        return result;
    }

    /**
     * Ищет поставленную модель, хитбокс которой вплотную примыкает к хитбоксу
     * {@code current} сверху или снизу (проверяется точка над/под его центром).
     */
    private Interaction findAdjacent(Interaction current, boolean above) {
        Location loc = current.getLocation();
        double y = above
                ? loc.getY() + current.getInteractionHeight() + 0.01
                : loc.getY() - 0.01;
        Vector probe = new Vector(loc.getX(), y, loc.getZ());
        Location probeLoc = new Location(current.getWorld(), probe.getX(), probe.getY(), probe.getZ());

        for (Entity nearby : current.getWorld().getNearbyEntities(probeLoc, 0.1, 0.1, 0.1)) {
            if (nearby instanceof Interaction other
                    && !other.getUniqueId().equals(current.getUniqueId())
                    && isPlacedInteraction(other)
                    && other.getBoundingBox().contains(probe)) {
                return other;
            }
        }
        return null;
    }

    private boolean isPlacedInteraction(Interaction interaction) {
        return PluginKeys.TYPE_INTERACTION.equals(
                interaction.getPersistentDataContainer().get(PluginKeys.type(), PersistentDataType.STRING));
    }

    private ItemStack readStoredItem(PersistentDataContainer pdc) {
        byte[] bytes = pdc.get(PluginKeys.item(), PersistentDataType.BYTE_ARRAY);
        if (bytes == null) {
            return null;
        }
        try {
            return ItemStack.deserializeBytes(bytes);
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Failed to deserialize stored item: " + ex.getMessage());
            return null;
        }
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
     * Убирает модель и возвращает игроку исходный предмет. Если над моделью стоят
     * другие модели, поведение зависит от {@code placement.stack_pickup}:
     * {@code all} — подбираются все; {@code top_only} — подбор запрещён.
     */
    public void pickup(Player player, Interaction interaction) {
        ItemStack clickedItem = readStoredItem(interaction.getPersistentDataContainer());
        PlaceableDefinition def = clickedItem != null ? registry.find(clickedItem) : null;
        // Запись могла быть удалена из конфига после установки — тогда значения по умолчанию.
        StackPickup mode = def != null ? def.stackPickup() : registry.defaultStackPickup();
        SoundSpec sound = def != null ? def.pickupSound() : registry.defaultPickupSound();

        List<Interaction> above = collectAbove(interaction);
        if (!above.isEmpty() && mode == StackPickup.TOP_ONLY) {
            Lang.sendActionBar(player, Lang.Key.PICKUP_BLOCKED_STACK);
            return;
        }

        Location soundLoc = interaction.getLocation().add(0, interaction.getInteractionHeight() / 2.0, 0);

        removeModel(player, interaction);
        for (Interaction extra : above) {
            removeModel(player, extra);
        }

        playSound(soundLoc, sound);
    }

    /**
     * Удаляет пару сущностей одной модели и отдаёт игроку её предмет.
     */
    private void removeModel(Player player, Interaction interaction) {
        PersistentDataContainer pdc = interaction.getPersistentDataContainer();

        ItemStack item = readStoredItem(pdc);
        Entity display = findDisplay(interaction, pdc);
        if (item == null && display instanceof ItemDisplay itemDisplay) {
            item = itemDisplay.getItemStack(); // запасной вариант
        }

        interaction.remove();
        if (display != null) {
            display.remove();
        }

        // Если данные повреждены, сущности просто убираются, чтобы не мешали.
        if (item != null && !item.getType().isAir()) {
            giveOrDrop(player, item);
        }
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
