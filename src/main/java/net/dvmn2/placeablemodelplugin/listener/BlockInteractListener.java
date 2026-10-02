package net.dvmn2.placeablemodelplugin.listener;

import net.dvmn2.placeablemodelplugin.Lang;
import net.dvmn2.placeablemodelplugin.PlaceableModelPlugin;
import net.dvmn2.placeablemodelplugin.config.DefinitionRegistry;
import net.dvmn2.placeablemodelplugin.config.PlaceableDefinition;
import net.dvmn2.placeablemodelplugin.manager.PlacedModelManager;
import net.dvmn2.placeablemodelplugin.util.InteractionGuard;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * ПКМ по блоку предметом, подходящим под одну из записей config.yml, ставит модель
 * на любую грань блока в точку клика. Учитывается только основная рука.
 * <p>
 * Если кликнутый блок интерактивный (сундук, дверь, ...) и игрок не крадётся,
 * ничего не ставится — блок работает как обычно; с Shift предмет ставится.
 */
public final class BlockInteractListener implements Listener {

    private static final long GUARD_SWEEP_INTERVAL_TICKS = 20L * 60;

    private final DefinitionRegistry registry;
    private final PlacedModelManager manager;
    private final InteractionGuard guard = new InteractionGuard();

    public BlockInteractListener(PlaceableModelPlugin plugin, DefinitionRegistry registry, PlacedModelManager manager) {
        this.registry = registry;
        this.manager = manager;
        Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin, guard::sweepStaleEntries, GUARD_SWEEP_INTERVAL_TICKS, GUARD_SWEEP_INTERVAL_TICKS);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        PlaceableDefinition def = registry.find(held);
        if (def == null) {
            return;
        }

        if (!player.isSneaking() && clicked.getType().isInteractable()) {
            return; // пусть сработает сам блок (сундук, дверь, ...)
        }

        event.setCancelled(true);

        if (!player.hasPermission(PlaceableModelPlugin.PERM_PLACE)) {
            if (guard.tryConsume(player, "no-perm-place")) {
                Lang.send(player, Lang.Key.NO_PERMISSION_PLACE);
            }
            return;
        }
        if (!guard.tryConsume(player, "place")) {
            return;
        }

        BlockFace face = event.getBlockFace();
        Location point = event.getInteractionPoint();
        if (point == null) {
            // Запасной вариант: центр кликнутой грани.
            point = clicked.getLocation().add(0.5 + face.getModX() * 0.5,
                    0.5 + face.getModY() * 0.5,
                    0.5 + face.getModZ() * 0.5);
        }

        if (manager.place(player, point, face, held, def)) {
            manager.consumeOne(player);
        }
    }
}
