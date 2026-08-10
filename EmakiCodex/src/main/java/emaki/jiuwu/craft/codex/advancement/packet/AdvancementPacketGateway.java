package emaki.jiuwu.craft.codex.advancement.packet;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerCommon;

import emaki.jiuwu.craft.codex.advancement.AdvancementRegistrar;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.execution.ThreadOwnership;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;


















public final class AdvancementPacketGateway {

    private final JavaPlugin plugin;
    private final AdvancementRegistrar registrar;
    private final ItemSourceService itemSourceService;
    private final boolean enabled;
    private final ExecutionDispatcher executionDispatcher;
    private final ThreadOwnership threadOwnership;

    private PacketListenerCommon registeredListener;

    private AdvancementResyncService resyncService;







    public AdvancementPacketGateway(JavaPlugin plugin,
            AdvancementRegistrar registrar,
            ItemSourceService itemSourceService,
            boolean enabled,
            ExecutionDispatcher executionDispatcher,
            ThreadOwnership threadOwnership) {
        this.plugin = plugin;
        this.registrar = registrar;
        this.itemSourceService = itemSourceService;
        this.enabled = enabled;
        this.executionDispatcher = executionDispatcher;
        this.threadOwnership = threadOwnership;
    }






    public boolean register() {
        if (!enabled || registeredListener != null || !isPacketEventsPresent()) {
            return false;
        }
        try {
            String namespace = plugin.getName().toLowerCase(Locale.ROOT);
            AdvancementPacketCoordinateChannel listener =
                    new AdvancementPacketCoordinateChannel(registrar, namespace, plugin.getLogger());
            registeredListener = PacketEvents.getAPI().getEventManager().registerListener(listener);
            return true;
        } catch (Throwable throwable) {
            plugin.getLogger().warning("[Codex] Advancement coordinate channel unavailable, skipped: "
                    + throwable.getMessage());
            registeredListener = null;
            return false;
        }
    }


    public void shutdown() {
        if (registeredListener == null) {
            return;
        }
        try {
            PacketEvents.getAPI().getEventManager().unregisterListener(registeredListener);
        } catch (Throwable ignored) {

        } finally {
            registeredListener = null;
        }
    }


    public boolean isActive() {
        return registeredListener != null;
    }


    public boolean canResync() {
        return isPacketEventsPresent();
    }








    public CompletableFuture<Integer> resyncAll() {
        if (!isPacketEventsPresent()) {
            return CompletableFuture.completedFuture(-1);
        }
        try {
            return resyncService().resyncAllAsync();
        } catch (Throwable throwable) {
            plugin.getLogger().warning("[Codex] Advancement resync skipped: " + throwable.getMessage());
            return CompletableFuture.completedFuture(-1);
        }
    }










    public boolean resync(Player player) {
        if (player == null || !isPacketEventsPresent()
                || threadOwnership == null || !threadOwnership.isEntityOwned(player)) {
            return false;
        }
        try {
            return resyncService().resync(player);
        } catch (Throwable throwable) {
            plugin.getLogger().warning("[Codex] Advancement resync skipped for "
                    + player.getName() + ": " + throwable.getMessage());
            return false;
        }
    }

    private AdvancementResyncService resyncService() {
        if (resyncService == null) {
            resyncService = new AdvancementResyncService(
                    plugin, registrar, itemSourceService, executionDispatcher, threadOwnership);
        }
        return resyncService;
    }

    private boolean isPacketEventsPresent() {
        return Bukkit.getPluginManager().getPlugin("packetevents") != null
                || Bukkit.getPluginManager().getPlugin("PacketEvents") != null;
    }
}
