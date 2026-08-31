package emaki.jiuwu.craft.mobs.apiimpl;

import emaki.jiuwu.craft.mobs.EmakiMobsPlugin;
import emaki.jiuwu.craft.mobs.api.MobExtensions;
import emaki.jiuwu.craft.mobs.api.MobSpawnerRegistration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

public final class DefaultMobExtensions implements MobExtensions, Listener, AutoCloseable {

    private final EmakiMobsPlugin plugin;
    private final Map<String, RegisteredSpawner> spawners = new LinkedHashMap<>();
    private long registrationGeneration;

    public DefaultMobExtensions(EmakiMobsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull MobSpawnerRegistration registerCustomSpawner(@Nullable Plugin owner,
                                                                  @Nullable String id,
                                                                  @Nullable CustomSpawner spawner) {
        if (spawner == null) {
            return MobSpawnerRegistration.noop();
        }
        String normalizedId = normalize(id);
        if (normalizedId.isBlank()) {
            return MobSpawnerRegistration.noop();
        }
        long generation;
        RegisteredSpawner replaced;
        synchronized (this) {
            generation = ++registrationGeneration;
            replaced = spawners.put(normalizedId, new RegisteredSpawner(owner, spawner, generation));
        }
        if (replaced != null) {
            plugin.getLogger().warning("[EmakiMobs] Replaced custom spawner '" + normalizedId
                    + "' from owner '" + ownerName(replaced.owner()) + "' with owner '"
                    + ownerName(owner) + "'.");
        }
        notifySpawner(normalizedId, owner, spawner);
        return new RegistrationHandle(this, normalizedId, generation);
    }

    @Override
    public synchronized void unregisterCustomSpawners(@Nullable Plugin owner) {
        if (owner == null) {
            return;
        }
        spawners.entrySet().removeIf(entry -> entry.getValue().owner() == owner);
    }

    public void notifyReload() {
        List<Map.Entry<String, RegisteredSpawner>> snapshot;
        synchronized (this) {
            spawners.entrySet().removeIf(entry -> {
                Plugin owner = entry.getValue().owner();
                return owner != null && !owner.isEnabled();
            });
            snapshot = new ArrayList<>(spawners.entrySet());
        }
        for (Map.Entry<String, RegisteredSpawner> entry : snapshot) {
            RegisteredSpawner registered = entry.getValue();
            notifySpawner(entry.getKey(), registered.owner(), registered.spawner());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginDisable(PluginDisableEvent event) {
        unregisterCustomSpawners(event.getPlugin());
    }

    @Override
    public void close() {
        HandlerList.unregisterAll(this);
        synchronized (this) {
            spawners.clear();
        }
    }

    private synchronized void unregister(String id, long generation) {
        RegisteredSpawner registered = spawners.get(id);
        if (registered == null || registered.generation() != generation) {
            return;
        }
        spawners.remove(id);
    }

    private void notifySpawner(String id, Plugin owner, CustomSpawner spawner) {
        try {
            spawner.onReload();
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING,
                    "[EmakiMobs] Custom spawner '" + id + "' from owner '"
                            + ownerName(owner) + "' failed during reload.", exception);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String ownerName(Plugin owner) {
        return owner == null ? "unowned" : owner.getName();
    }

    private record RegisteredSpawner(Plugin owner, CustomSpawner spawner, long generation) {
    }

    private static final class RegistrationHandle implements MobSpawnerRegistration {

        private final DefaultMobExtensions registry;
        private final String id;
        private final long generation;
        private final AtomicBoolean closed = new AtomicBoolean();

        private RegistrationHandle(DefaultMobExtensions registry, String id, long generation) {
            this.registry = registry;
            this.id = id;
            this.generation = generation;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                registry.unregister(id, generation);
            }
        }
    }
}
