package emaki.jiuwu.craft.corelib.api.script;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.graalvm.polyglot.HostAccess;

import emaki.jiuwu.craft.corelib.async.FoliaSchedulerAdapter;
import emaki.jiuwu.craft.corelib.script.ScriptConfig;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class ScriptServerApi {

    private final Plugin sourcePlugin;
    private final ScriptConfig config;

    public ScriptServerApi(Plugin sourcePlugin, ScriptConfig config) {
        this.sourcePlugin = sourcePlugin;
        this.config = config == null ? ScriptConfig.defaults() : config;
    }

    @HostAccess.Export
    public boolean pluginEnabled(String pluginName) {
        return Texts.isNotBlank(pluginName) && Bukkit.getPluginManager().isPluginEnabled(pluginName);
    }

    @HostAccess.Export
    public List<ScriptEntityApi> onlinePlayers() {
        List<ScriptEntityApi> players = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            players.add(new ScriptEntityApi(player));
        }
        return List.copyOf(players);
    }

    @HostAccess.Export
    public ScriptEntityApi player(String nameOrUuid) {
        Player player = null;
        if (Texts.isNotBlank(nameOrUuid)) {
            try {
                player = Bukkit.getPlayer(UUID.fromString(nameOrUuid));
            } catch (IllegalArgumentException exception) {
                player = Bukkit.getPlayerExact(nameOrUuid);
                if (player == null) {
                    player = Bukkit.getPlayer(nameOrUuid);
                }
            }
        }
        return new ScriptEntityApi(player);
    }

    @HostAccess.Export
    public ScriptWorldApi world(String name) {
        return new ScriptWorldApi(Texts.isBlank(name) ? null : Bukkit.getWorld(name));
    }

    @HostAccess.Export
    public void broadcast(String message) {
        if (message != null) {
            Bukkit.broadcastMessage(message);
        }
    }

    @HostAccess.Export
    public boolean dispatchCommandAsConsole(String command) {
        if (!config.serverApi().allowConsoleCommand() || Texts.isBlank(command)) {
            return false;
        }
        return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.startsWith("/") ? command.substring(1) : command);
    }

    @HostAccess.Export
    public void runSync(Runnable task) {
        if (task != null) {
            FoliaSchedulerAdapter.runTask(sourcePlugin, task);
        }
    }

    @HostAccess.Export
    public CompletableFuture<Void> runSyncAndWait(Runnable task) {
        if (task == null) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Void> future = new CompletableFuture<>();
        FoliaSchedulerAdapter.runTask(sourcePlugin, () -> {
            try {
                task.run();
                future.complete(null);
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });
        return future;
    }

    @HostAccess.Export
    public Class<?> type(String className) throws ClassNotFoundException {
        ScriptConfig.ServerApi serverApi = config.serverApi();
        if (!serverApi.allowTypeAccess() || Texts.isBlank(className)) {
            throw new ClassNotFoundException("Server API type access is disabled.");
        }
        boolean allowed = serverApi.allowedTypePrefixes().stream().anyMatch(className::startsWith);
        if (!allowed) {
            throw new ClassNotFoundException("Server API type is not allowed: " + className);
        }
        return Class.forName(className);
    }

    public static final class ScriptWorldApi {

        private final World world;

        ScriptWorldApi(World world) {
            this.world = world;
        }

        @HostAccess.Export
        public boolean exists() {
            return world != null;
        }

        @HostAccess.Export
        public String name() {
            return world == null ? "" : world.getName();
        }

        @HostAccess.Export
        public void strikeLightningEffect(double x, double y, double z) {
            if (world != null) {
                world.strikeLightningEffect(new Location(world, x, y, z));
            }
        }
    }

    public static final class ScriptEntityApi {

        private final Entity entity;

        public ScriptEntityApi(Entity entity) {
            this.entity = entity;
        }

        public Entity entity() {
            return entity;
        }

        @HostAccess.Export
        public boolean exists() {
            return entity != null && entity.isValid();
        }

        @HostAccess.Export
        public boolean living() {
            return entity instanceof LivingEntity;
        }

        @HostAccess.Export
        public boolean player() {
            return entity instanceof Player;
        }

        @HostAccess.Export
        public String name() {
            return entity == null ? "" : entity.getName();
        }

        @HostAccess.Export
        public String uuid() {
            return entity == null ? "" : entity.getUniqueId().toString();
        }

        @HostAccess.Export
        public String type() {
            return entity == null ? "" : entity.getType().name().toLowerCase();
        }

        @HostAccess.Export
        public ScriptWorldApi world() {
            return new ScriptWorldApi(entity == null ? null : entity.getWorld());
        }

        @HostAccess.Export
        public java.util.Map<String, Object> location() {
            if (entity == null) {
                return java.util.Map.of();
            }
            Location location = entity.getLocation();
            return java.util.Map.of(
                    "world", location.getWorld() == null ? "" : location.getWorld().getName(),
                    "x", location.getX(),
                    "y", location.getY(),
                    "z", location.getZ(),
                    "yaw", location.getYaw(),
                    "pitch", location.getPitch()
            );
        }

        @HostAccess.Export
        public void sendMessage(String message) {
            if (entity instanceof Player player && message != null) {
                player.sendMessage(message);
            }
        }

        @HostAccess.Export
        public double health() {
            return entity instanceof LivingEntity livingEntity ? livingEntity.getHealth() : 0D;
        }

        @HostAccess.Export
        public void setHealth(double health) {
            if (entity instanceof LivingEntity livingEntity) {
                livingEntity.setHealth(Math.max(0D, Math.min(livingEntity.getMaxHealth(), health)));
            }
        }

        @HostAccess.Export
        public void damage(double amount) {
            if (entity instanceof LivingEntity livingEntity) {
                livingEntity.damage(Math.max(0D, amount));
            }
        }

        @HostAccess.Export
        public boolean teleport(String worldName, double x, double y, double z) {
            if (entity == null || Texts.isBlank(worldName)) {
                return false;
            }
            World world = Bukkit.getWorld(worldName);
            return world != null && entity.teleport(new Location(world, x, y, z));
        }
    }
}
