package emaki.jiuwu.craft.corelib.api.script;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.graalvm.polyglot.HostAccess;

import emaki.jiuwu.craft.corelib.script.ScriptConfig;
import emaki.jiuwu.craft.corelib.script.ScriptDeferredOperationQueue;
import emaki.jiuwu.craft.corelib.script.ScriptEntitySnapshot.EntityView;
import emaki.jiuwu.craft.corelib.text.Texts;


public final class ScriptServerApi {

    private final ScriptConfig config;
    private final ScriptDeferredOperationQueue deferredOperations;
    private final Map<String, Boolean> pluginStates;
    private final List<ScriptEntityApi> onlinePlayers;
    private final Map<String, ScriptEntityApi> playersByName;
    private final Map<String, ScriptEntityApi> playersByUuid;
    private final Map<String, ScriptWorldApi> worlds;
    private final Server server;

    public ScriptServerApi(Plugin sourcePlugin, ScriptConfig config) {
        this(sourcePlugin, config, null);
    }

    public ScriptServerApi(Plugin sourcePlugin,
            ScriptConfig config,
            ScriptDeferredOperationQueue deferredOperations) {
        this.config = config == null ? ScriptConfig.defaults() : config;
        this.deferredOperations = deferredOperations;
        this.server = sourcePlugin == null ? null : sourcePlugin.getServer();

        Map<String, Boolean> capturedPlugins = new LinkedHashMap<>();
        List<ScriptEntityApi> capturedPlayers = new ArrayList<>();
        Map<String, ScriptEntityApi> capturedByName = new LinkedHashMap<>();
        Map<String, ScriptEntityApi> capturedByUuid = new LinkedHashMap<>();
        Map<String, ScriptWorldApi> capturedWorlds = new LinkedHashMap<>();
        if (server != null) {
            for (Plugin installed : server.getPluginManager().getPlugins()) {
                if (installed != null) {
                    capturedPlugins.put(Texts.lower(installed.getName()), installed.isEnabled());
                }
            }
            for (Player player : server.getOnlinePlayers()) {
                ScriptEntityApi snapshot = new ScriptEntityApi(player, deferredOperations, server);
                capturedPlayers.add(snapshot);
                capturedByName.put(Texts.lower(player.getName()), snapshot);
                capturedByUuid.put(player.getUniqueId().toString(), snapshot);
            }
            for (World world : server.getWorlds()) {
                capturedWorlds.put(Texts.lower(world.getName()), new ScriptWorldApi(world, deferredOperations));
            }
        }
        this.pluginStates = Map.copyOf(capturedPlugins);
        this.onlinePlayers = List.copyOf(capturedPlayers);
        this.playersByName = Map.copyOf(capturedByName);
        this.playersByUuid = Map.copyOf(capturedByUuid);
        this.worlds = Map.copyOf(capturedWorlds);
    }

    @HostAccess.Export
    public boolean pluginEnabled(String pluginName) {
        return Boolean.TRUE.equals(pluginStates.get(Texts.lower(pluginName)));
    }

    @HostAccess.Export
    public List<ScriptEntityApi> onlinePlayers() {
        return onlinePlayers;
    }

    @HostAccess.Export
    public ScriptEntityApi player(String nameOrUuid) {
        if (Texts.isBlank(nameOrUuid)) {
            return ScriptEntityApi.missing();
        }
        String key = Texts.trim(nameOrUuid);
        try {
            ScriptEntityApi snapshot = playersByUuid.get(UUID.fromString(key).toString());
            return snapshot == null ? ScriptEntityApi.missing() : snapshot;
        } catch (IllegalArgumentException ignored) {
            ScriptEntityApi snapshot = playersByName.get(Texts.lower(key));
            return snapshot == null ? ScriptEntityApi.missing() : snapshot;
        }
    }

    @HostAccess.Export
    public ScriptWorldApi world(String name) {
        return worlds.getOrDefault(Texts.lower(name), new ScriptWorldApi(null));
    }

    @HostAccess.Export
    public void broadcast(String message) {
        if (message != null && deferredOperations != null && server != null) {
            String safeMessage = Texts.toStringSafe(message);
            deferredOperations.enqueueGlobal("server:broadcast", () -> server.broadcastMessage(safeMessage));
        }
    }

    @HostAccess.Export
    public boolean dispatchCommandAsConsole(String command) {
        if (!config.serverApi().allowConsoleCommand() || Texts.isBlank(command)
                || deferredOperations == null || server == null) {
            return false;
        }
        String safeCommand = command.startsWith("/") ? command.substring(1) : command;
        return deferredOperations.enqueueGlobal("server:console-command",
                () -> server.dispatchCommand(server.getConsoleSender(), safeCommand));
    }

    public static final class ScriptWorldApi {

        private final String name;
        private final boolean exists;
        private final ScriptDeferredOperationQueue deferredOperations;
        private final World world;

        public ScriptWorldApi(World world) {
            this(world, null);
        }

        ScriptWorldApi(World world, ScriptDeferredOperationQueue deferredOperations) {
            this.world = world;
            this.name = world == null ? "" : world.getName();
            this.exists = world != null;
            this.deferredOperations = deferredOperations;
        }

        private ScriptWorldApi(String name, boolean exists) {
            this.world = null;
            this.name = name == null ? "" : name;
            this.exists = exists;
            this.deferredOperations = null;
        }

        @HostAccess.Export
        public boolean exists() {
            return exists;
        }

        @HostAccess.Export
        public String name() {
            return name;
        }

        @HostAccess.Export
        public void strikeLightningEffect(double x, double y, double z) {
            if (world == null || deferredOperations == null) {
                return;
            }
            deferredOperations.enqueueLocation(
                    "world:lightning-effect",
                    () -> new Location(world, x, y, z),
                    world::strikeLightningEffect
            );
        }
    }

    public static final class ScriptEntityApi {

        private final Entity entity;
        private final ScriptDeferredOperationQueue deferredOperations;
        private final Server server;
        private final boolean exists;
        private final boolean living;
        private final boolean player;
        private final String name;
        private final String uuid;
        private final String type;
        private final String worldName;
        private final ScriptWorldApi worldSnapshot;
        private final Map<String, Object> location;
        private final double health;
        private final double maxHealth;

        public ScriptEntityApi(Entity entity) {
            this(entity, null, null);
        }

        private static ScriptEntityApi missing() {
            return new ScriptEntityApi(EntityView.missing());
        }

        ScriptEntityApi(Entity entity, ScriptDeferredOperationQueue deferredOperations, Server server) {
            this.entity = entity;
            this.deferredOperations = deferredOperations;
            this.server = server;
            this.exists = entity != null && entity.isValid();
            this.living = entity instanceof LivingEntity;
            this.player = entity instanceof Player;
            this.name = entity == null ? "" : entity.getName();
            this.uuid = entity == null ? "" : entity.getUniqueId().toString();
            this.type = entity == null ? "" : entity.getType().name().toLowerCase(Locale.ROOT);
            Location capturedLocation = entity == null ? null : entity.getLocation().clone();
            World capturedWorld = capturedLocation == null ? null : capturedLocation.getWorld();
            this.worldName = capturedWorld == null ? "" : capturedWorld.getName();
            this.worldSnapshot = new ScriptWorldApi(capturedWorld, deferredOperations);
            this.location = capturedLocation == null ? Map.of() : Map.of(
                    "world", worldName,
                    "x", capturedLocation.getX(),
                    "y", capturedLocation.getY(),
                    "z", capturedLocation.getZ(),
                    "yaw", capturedLocation.getYaw(),
                    "pitch", capturedLocation.getPitch()
            );
            this.health = entity instanceof LivingEntity livingEntity ? livingEntity.getHealth() : 0D;
            this.maxHealth = entity instanceof LivingEntity livingEntity ? livingEntity.getMaxHealth() : 0D;
        }

        public ScriptEntityApi(EntityView view) {
            EntityView safe = view == null ? EntityView.missing() : view;
            this.entity = null;
            this.deferredOperations = null;
            this.server = null;
            this.exists = safe.exists();
            this.living = safe.living();
            this.player = safe.player();
            this.name = safe.name();
            this.uuid = safe.uuid();
            this.type = safe.type();
            this.worldName = safe.world().name();
            this.worldSnapshot = new ScriptWorldApi(safe.world().name(), safe.world().exists());
            this.location = safe.location();
            this.health = safe.health();
            this.maxHealth = safe.maxHealth();
        }

        public Entity entity() {
            return entity;
        }

        @HostAccess.Export public boolean exists() { return exists; }
        @HostAccess.Export public boolean living() { return living; }
        @HostAccess.Export public boolean player() { return player; }
        @HostAccess.Export public String name() { return name; }
        @HostAccess.Export public String uuid() { return uuid; }
        @HostAccess.Export public String type() { return type; }
        @HostAccess.Export public ScriptWorldApi world() { return worldSnapshot; }
        @HostAccess.Export public Map<String, Object> location() { return location; }
        @HostAccess.Export public double health() { return health; }
        @HostAccess.Export public double maxHealth() { return maxHealth; }

        @HostAccess.Export
        public void sendMessage(String message) {
            if (entity instanceof Player && message != null && deferredOperations != null) {
                String safeMessage = Texts.toStringSafe(message);
                deferredOperations.enqueueEntity("entity:message", entity,
                        target -> ((Player) target).sendMessage(safeMessage));
            }
        }

        @HostAccess.Export
        public void setHealth(double value) {
            if (entity instanceof LivingEntity && deferredOperations != null) {
                deferredOperations.enqueueEntity("entity:set-health", entity, target -> {
                    LivingEntity livingTarget = (LivingEntity) target;
                    livingTarget.setHealth(Math.max(0D, Math.min(livingTarget.getMaxHealth(), value)));
                });
            }
        }

        @HostAccess.Export
        public void damage(double amount) {
            if (entity instanceof LivingEntity && deferredOperations != null) {
                deferredOperations.enqueueEntity("entity:damage", entity,
                        target -> ((LivingEntity) target).damage(Math.max(0D, amount)));
            }
        }

        @HostAccess.Export
        public boolean teleport(String targetWorld, double x, double y, double z) {
            if (entity == null || server == null || Texts.isBlank(targetWorld) || deferredOperations == null) {
                return false;
            }
            return deferredOperations.enqueueEntityAsync("entity:teleport", entity, target -> {
                World world = server.getWorld(targetWorld);
                if (world == null) {
                    return CompletableFuture.completedFuture(
                            ScriptDeferredOperationQueue.OperationResult.failure("Teleport world was not found."));
                }
                return target.teleportAsync(new Location(world, x, y, z)).thenApply(success -> success
                        ? ScriptDeferredOperationQueue.OperationResult.ok()
                        : ScriptDeferredOperationQueue.OperationResult.failure("Entity teleport failed."));
            });
        }
    }
}
