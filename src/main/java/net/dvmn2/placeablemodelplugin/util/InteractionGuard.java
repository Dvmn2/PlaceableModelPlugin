package net.dvmn2.placeablemodelplugin.util;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Защита от повторной обработки одного физического клика (Bukkit/Paper может
 * присылать несколько событий на одно действие, в т.ч. PlayerInteractAtEntityEvent
 * и PlayerInteractEntityEvent подряд). Ключи содержат id сущностей, поэтому
 * {@link #sweepStaleEntries()} нужно вызывать периодически.
 */
public final class InteractionGuard {

    private static final long DEBOUNCE_WINDOW_NANOS = 100_000_000L;   // 100 мс
    private static final long STALE_ENTRY_NANOS = 60_000_000_000L;    // 60 с

    private final Map<String, Long> lastHandledAt = new ConcurrentHashMap<>();

    /**
     * @return true при первом обращении с этим ключом в окне дебаунса.
     */
    public boolean tryConsume(Player player, String actionKey) {
        String mapKey = player.getUniqueId() + "|" + actionKey;
        long now = System.nanoTime();
        Long previous = lastHandledAt.put(mapKey, now);
        return previous == null || (now - previous) > DEBOUNCE_WINDOW_NANOS;
    }

    public void sweepStaleEntries() {
        long now = System.nanoTime();
        lastHandledAt.entrySet().removeIf(entry -> (now - entry.getValue()) > STALE_ENTRY_NANOS);
    }
}
