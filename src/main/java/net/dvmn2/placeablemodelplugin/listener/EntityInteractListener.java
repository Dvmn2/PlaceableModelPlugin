package net.dvmn2.placeablemodelplugin.listener;

import net.dvmn2.placeablemodelplugin.Lang;
import net.dvmn2.placeablemodelplugin.PlaceableModelPlugin;
import net.dvmn2.placeablemodelplugin.config.DefinitionRegistry;
import net.dvmn2.placeablemodelplugin.config.PlaceableDefinition;
import net.dvmn2.placeablemodelplugin.manager.PlacedModelManager;
import net.dvmn2.placeablemodelplugin.util.InteractionGuard;
import net.dvmn2.placeablemodelplugin.util.PluginKeys;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * ПКМ по Interaction-сущности поставленной модели:
 * <ul>
 *     <li>пустая основная рука — подобрать модель (исходный предмет возвращается);</li>
 *     <li>в руке тот же предмет, у записи {@code placement.stackable: true} и игрок не
 *     крадётся — предмет ставится поверх (на верх хитбокса самой верхней модели стопки);</li>
 *     <li>в руке предмет-модель и включён {@code settings.place_through_models} —
 *     предмет ставится на блок, который находится за хитбоксом (иначе хитбокс
 *     мешал бы ставить модели вплотную друг к другу).</li>
 * </ul>
 * Событие отменяется всегда, чтобы клик не "просачивался" в ванильную логику.
 * Слушаем родительское {@link PlayerInteractEntityEvent}: оно же ловит и
 * PlayerInteractAtEntityEvent; дубли гасит {@link InteractionGuard}.
 */
public final class EntityInteractListener implements Listener {

    private static final double REACH = 5.0;
    private static final long GUARD_SWEEP_INTERVAL_TICKS = 20L * 60;

    private final DefinitionRegistry registry;
    private final PlacedModelManager manager;
    private final InteractionGuard guard = new InteractionGuard();

    public EntityInteractListener(PlaceableModelPlugin plugin, DefinitionRegistry registry, PlacedModelManager manager) {
        this.registry = registry;
        this.manager = manager;
        Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin, guard::sweepStaleEntries, GUARD_SWEEP_INTERVAL_TICKS, GUARD_SWEEP_INTERVAL_TICKS);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Entity target = event.getRightClicked();
        if (!(target instanceof Interaction interaction)) {
            return;
        }
        String type = interaction.getPersistentDataContainer().get(PluginKeys.type(), PersistentDataType.STRING);
        if (!PluginKeys.TYPE_INTERACTION.equals(type)) {
            return; // чужая Interaction-сущность
        }

        event.setCancelled(true);

        Player player = event.getPlayer();
        if (!guard.tryConsume(player, "entity-" + target.getUniqueId())) {
            return;
        }

        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType().isAir()) {
            handlePickup(player, interaction);
        } else {
            handleHeldItem(player, interaction, held);
        }
    }

    private void handlePickup(Player player, Interaction interaction) {
        if (!player.hasPermission(PlaceableModelPlugin.PERM_PICKUP)) {
            Lang.send(player, Lang.Key.NO_PERMISSION_PICKUP);
            return;
        }
        manager.pickup(player, interaction);
    }

    private void handleHeldItem(Player player, Interaction interaction, ItemStack held) {
        PlaceableDefinition def = registry.find(held);
        if (def == null) {
            return;
        }
        if (!player.hasPermission(PlaceableModelPlugin.PERM_PLACE)) {
            Lang.send(player, Lang.Key.NO_PERMISSION_PLACE);
            return;
        }

        // Тот же предмет, стопка разрешена, игрок не крадётся -> ставим сверху.
        // С Shift клик работает как обычная "постановка сквозь модель".
        if (def.stackable()
                && def.allows(BlockFace.UP)
                && !player.isSneaking()
                && manager.isSameModel(interaction, def)) {
            handleStack(player, interaction, held, def);
            return;
        }

        handlePlaceThrough(player, held, def);
    }

    private void handleStack(Player player, Interaction clicked, ItemStack held, PlaceableDefinition def) {
        Interaction top = manager.findStackTop(clicked, def);
        if (top == null) {
            Lang.sendActionBar(player, Lang.Key.STACK_BLOCKED);
            return;
        }
        if (def.maxStack() > 0 && manager.stackHeight(top, def) >= def.maxStack()) {
            Lang.sendActionBar(player, Lang.Key.STACK_LIMIT);
            return;
        }
        if (manager.placeOnTop(player, top, held, def)) {
            manager.consumeOne(player);
        }
    }

    private void handlePlaceThrough(Player player, ItemStack held, PlaceableDefinition def) {
        if (!registry.placeThroughModels()) {
            return;
        }

        // Луч только по блокам: сущности (в т.ч. наш хитбокс) он игнорирует.
        RayTraceResult hit = player.rayTraceBlocks(REACH);
        if (hit == null || hit.getHitBlock() == null || hit.getHitBlockFace() == null) {
            return;
        }
        Vector pos = hit.getHitPosition();
        Location point = new Location(player.getWorld(), pos.getX(), pos.getY(), pos.getZ());
        BlockFace face = hit.getHitBlockFace();
        if (!def.allows(face)) {
            Lang.sendActionBar(player, Lang.Key.SURFACE_NOT_ALLOWED);
            return;
        }

        if (manager.place(player, point, face, held, def)) {
            manager.consumeOne(player);
        }
    }
}
