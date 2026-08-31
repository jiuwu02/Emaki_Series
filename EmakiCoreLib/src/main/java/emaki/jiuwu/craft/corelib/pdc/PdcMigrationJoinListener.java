package emaki.jiuwu.craft.corelib.pdc;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import emaki.jiuwu.craft.corelib.api.pdc.PdcKeyMigration;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;

import java.util.Map;
import java.util.function.Supplier;

public final class PdcMigrationJoinListener implements Listener {

    private final Supplier<DebugLogger> debugLoggerSupplier;

    public PdcMigrationJoinListener(Supplier<DebugLogger> debugLoggerSupplier) {
        this.debugLoggerSupplier = debugLoggerSupplier == null ? () -> null : debugLoggerSupplier;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        int migrated = PdcKeyMigration.migrateAll(event.getPlayer().getPersistentDataContainer(), false);
        if (migrated <= 0) {
            return;
        }
        DebugLogger debugLogger = debugLoggerSupplier.get();
        if (debugLogger == null || !debugLogger.shouldLog("pdc", event.getPlayer())) {
            return;
        }
        debugLogger.log("pdc", event.getPlayer(), "pdc.join_migrated", Map.of(
                "player", event.getPlayer().getName(),
                "keys", String.valueOf(migrated)
        ));
    }
}
