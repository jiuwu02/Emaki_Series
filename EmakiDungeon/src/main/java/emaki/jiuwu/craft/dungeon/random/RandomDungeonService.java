package emaki.jiuwu.craft.dungeon.random;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.dungeon.instance.DungeonInstance;
import emaki.jiuwu.craft.dungeon.instance.InstanceState;
import emaki.jiuwu.craft.dungeon.instance.WorldCommandQueue;
import emaki.jiuwu.craft.dungeon.orchestration.InstanceRegistry;
import emaki.jiuwu.craft.dungeon.random.build.BuildPipeline;
import emaki.jiuwu.craft.dungeon.random.build.BuildValidator;
import emaki.jiuwu.craft.dungeon.random.build.WorldBootstrap;
import emaki.jiuwu.craft.dungeon.random.config.RandomConfig;
import emaki.jiuwu.craft.dungeon.random.gen.Assigner;
import emaki.jiuwu.craft.dungeon.random.gen.Seeds;
import emaki.jiuwu.craft.dungeon.random.gen.Topology;
import emaki.jiuwu.craft.dungeon.random.gen.TopologyGenerator;
import emaki.jiuwu.craft.dungeon.random.layout.Bake;
import emaki.jiuwu.craft.dungeon.random.layout.LayoutEngine;
import emaki.jiuwu.craft.dungeon.random.layout.Placement;
import emaki.jiuwu.craft.dungeon.random.layout.Placement.Layout;
import emaki.jiuwu.craft.dungeon.random.layout.Placement.RoomPlacement;
import emaki.jiuwu.craft.dungeon.random.lifecycle.InstanceStore;
import emaki.jiuwu.craft.dungeon.random.lifecycle.RandomInstance;
import emaki.jiuwu.craft.dungeon.random.lifecycle.RandomInstanceState;
import emaki.jiuwu.craft.dungeon.random.map.TreasureMapService;
import emaki.jiuwu.craft.dungeon.random.model.TemplateMeta;
import emaki.jiuwu.craft.dungeon.random.model.TemplateModel;
import emaki.jiuwu.craft.dungeon.random.nbt.StructureNbt;
import emaki.jiuwu.craft.dungeon.random.template.CaptureService;
import emaki.jiuwu.craft.dungeon.random.template.TemplateStore;
import emaki.jiuwu.craft.dungeon.world.WorldDisposer;
import emaki.jiuwu.craft.dungeon.world.WorldFolders;
import emaki.jiuwu.craft.dungeon.world.WorldNames;

public final class RandomDungeonService {

    public record Result(boolean ok, String messageKey, Map<String, String> params) {

        public static Result fail(String key, Map<String, String> params) {
            return new Result(false, key, params);
        }

        public static Result ok(String key, Map<String, String> params) {
            return new Result(true, key, params);
        }
    }

    private static final int ALLOCATION_LIMIT = 3;

    private final JavaPlugin plugin;
    private final ExecutionDispatcher dispatcher;
    private final MessageService messages;
    private final File dataFolder;
    private final File worldContainer;
    private final WorldDisposer disposer;
    private final InstanceRegistry registry;
    private final Function<DungeonInstance, emaki.jiuwu.craft.dungeon.flow.FlowRuntime> flowFactory;
    private final Consumer<DungeonInstance> registerHook;
    private final WorldNames worldNames;

    private final CaptureService captureService;
    private final InstanceStore instanceStore;
    private final TreasureMapService mapService = new TreasureMapService();
    private final Map<String, RandomInstance> instances = new ConcurrentHashMap<>();
    private final Map<String, TemplateStore> stores = new ConcurrentHashMap<>();
    private final Map<String, Map<String, TemplateMeta>> metasByDungeon = new ConcurrentHashMap<>();
    private final Map<String, Map<String, StructureNbt.StructureData>> templatesByDungeon = new ConcurrentHashMap<>();

    private volatile RandomConfig config;
    private volatile List<String> configErrors = List.of();
    private volatile Function<String, emaki.jiuwu.craft.dungeon.config.DungeonConfig> configOf;
    private TaskToken sweepTask = TaskToken.UNAVAILABLE;

    public void bindConfigOf(Function<String, emaki.jiuwu.craft.dungeon.config.DungeonConfig> configOf) {
        this.configOf = configOf;
    }

    public RandomDungeonService(JavaPlugin plugin, ExecutionDispatcher dispatcher, MessageService messages,
            File dataFolder, File worldContainer, WorldDisposer disposer, InstanceRegistry registry,
            WorldNames worldNames,
            Function<DungeonInstance, emaki.jiuwu.craft.dungeon.flow.FlowRuntime> flowFactory,
            Consumer<DungeonInstance> registerHook) {
        this.plugin = plugin;
        this.dispatcher = dispatcher;
        this.messages = messages;
        this.dataFolder = dataFolder;
        this.worldContainer = worldContainer;
        this.disposer = disposer;
        this.registry = registry;
        this.worldNames = worldNames;
        this.flowFactory = flowFactory;
        this.registerHook = registerHook;
        this.captureService = new CaptureService(this::storeOf);
        this.instanceStore = new InstanceStore(dataFolder);
    }

    public void loadAll() {
        reloadConfig();
        reloadTemplates();
        sweepTask = dispatcher.runGlobalTimer(plugin, this::sweep, 10L, 10L);
    }

    public void shutdown() {
        sweepTask.cancel();
        for (String instanceId : List.copyOf(instances.keySet())) {
            stop(instanceId);
        }
    }

    public void reloadConfig() {
        File file = new File(dataFolder, "random-dungeon.yml");
        if (!file.isFile()) {
            try {
                emaki.jiuwu.craft.corelib.api.yaml.YamlFiles.copyResourceIfMissing(plugin, "random-dungeon.yml", file);
            } catch (IOException ignored) {
            }
        }
        YamlSection root = YamlFiles.load(file);
        RandomConfig.Loaded loaded = RandomConfig.parse(root);
        if (loaded.valid()) {
            config = loaded.config();
                configErrors = List.of();
        } else {
            configErrors = List.copyOf(loaded.errors());
        }
    }

    public void reloadTemplates() {
        metasByDungeon.clear();
        templatesByDungeon.clear();
        for (String dungeonId : dungeonIds()) {
            TemplateStore store = storeOf(dungeonId);
            Map<String, TemplateMeta> dungeonMetas = new ConcurrentHashMap<>();
            Map<String, StructureNbt.StructureData> dungeonTemplates = new ConcurrentHashMap<>();
            for (String id : store.ids()) {
                TemplateMeta meta = store.loadMeta(id);
                if (meta == null) {
                    continue;
                }
                dungeonMetas.put(id, meta);
                StructureNbt.StructureData data = store.loadTemplate(id);
                if (data != null) {
                    dungeonTemplates.put(id, data);
                }
            }
            metasByDungeon.put(dungeonId, dungeonMetas);
            templatesByDungeon.put(dungeonId, dungeonTemplates);
        }
    }

    public RandomConfig config() {
        return config;
    }

    public List<String> configErrors() {
        return configErrors;
    }

    public CaptureService captures() {
        return captureService;
    }

    public TemplateStore storeOf(String dungeonId) {
        return stores.computeIfAbsent(dungeonId,
                id -> TemplateStore.forDungeon(dataFolder, id));
    }

    public List<String> dungeonIds() {
        File root = new File(dataFolder, "world");
        File[] children = root.listFiles(File::isDirectory);
        List<String> ids = new ArrayList<>();
        if (children != null) {
            for (File child : children) {
                if (new File(child, "config.yml").isFile()) {
                    ids.add(child.getName());
                }
            }
        }
        ids.sort(String::compareTo);
        return ids;
    }

    public Map<String, TemplateMeta> templateMetas(String dungeonId) {
        return metasByDungeon.getOrDefault(dungeonId, Map.of());
    }

    private Map<String, TemplateMeta> metasOf(String dungeonId) {
        return metasByDungeon.getOrDefault(dungeonId, Map.of());
    }

    private Map<String, StructureNbt.StructureData> templatesOf(String dungeonId) {
        return templatesByDungeon.getOrDefault(dungeonId, Map.of());
    }

    public List<RandomInstance> instances() {
        return new ArrayList<>(instances.values());
    }

    public RandomInstance instanceOf(String instanceId) {
        return instances.get(instanceId);
    }

    public Result create(String dungeonId, int players, Long seedOverride) {
        if (config == null) {
            return Result.fail("random.config_invalid", Map.of("errors",
                    String.join("; ", configErrors)));
        }
        if (dungeonId == null || dungeonId.isBlank()) {
            return Result.fail("random.create_no_dungeon", Map.of());
        }
        var dungeonConfig = configOf == null ? null : configOf.apply(dungeonId);
        if (dungeonConfig == null || !emaki.jiuwu.craft.dungeon.type.DungeonTypeRegistry.RANDOM
                .equals(dungeonConfig.type())) {
            return Result.fail("random.not_random_type", Map.of("dungeon", dungeonId));
        }
        Map<String, TemplateMeta> metas = metasOf(dungeonId);
        Map<String, StructureNbt.StructureData> templates = templatesOf(dungeonId);
        int active = 0;
        for (RandomInstance instance : instances.values()) {
            if (instance.state() != RandomInstanceState.DESTROYED
                    && instance.state() != RandomInstanceState.FAILED) {
                active++;
            }
        }
        int cap = Math.min(config.limits.concurrentInstances(), 20);
        if (active >= cap) {
            return Result.fail("random.create_busy", Map.of("active", String.valueOf(active)));
        }
        if (metas.isEmpty()) {
            return Result.fail("random.no_templates", Map.of());
        }
        int roomCount = config.roomCountFor(players);
        long seed = seedOverride != null ? seedOverride
                : ("fixed".equals(config.seed.source()) ? config.seed.fixedValue()
                        : java.util.concurrent.ThreadLocalRandom.current().nextLong());
        RandomInstance instance = null;
        for (int allocation = 0; allocation < ALLOCATION_LIMIT; allocation++) {
            Seeds seeds = Seeds.derive(seed);
            Topology topology = new TopologyGenerator().generate(seeds.topology(), roomCount);
            Assigner.Result assignment = new Assigner().assign(seeds.assignment(), topology,
                    templateOptions(metas), mandatoryRules());
            if (assignment.nodeTemplate().isEmpty()) {
                continue;
            }
            LayoutEngine.Result layoutResult = new LayoutEngine().layout(
                    seeds.layout(), seeds.orientation(), seeds.link(),
                    topology, assignment.nodeTemplate(), metas,
                    config, config.limits.maxLayoutBacktracks(), 64);
            if (layoutResult.layout() == null) {
                if ("door-reject".equals(layoutResult.failure())) {
                    continue;
                }
                return Result.fail("random.layout_failed", Map.of(
                        "room-count", String.valueOf(roomCount),
                        "attempts", String.valueOf(config.limits.maxLayoutBacktracks())));
            }
            Bake.Result baked = Bake.bake(layoutResult.layout(), metas);
            String instanceId = worldNames.next("rnd");
            instance = new RandomInstance(instanceId, seed, seeds, config.configHash,
                    topology.hash(), roomCount);
            instance.layout(layoutResult.layout());
            instance.record("generated allocation=" + (allocation + 1));
            instance.forceState(RandomInstanceState.GENERATING);
            instances.put(instanceId, instance);
            instanceStore.save(instance);
            buildWorld(instance, baked, metas, templates);
            return Result.ok("random.create_started", Map.of(
                    "instance", instanceId,
                    "seed", String.valueOf(seed),
                    "rooms", String.valueOf(roomCount)));
        }
        return Result.fail("random.allocation_failed", Map.of("limit", String.valueOf(ALLOCATION_LIMIT)));
    }

    private List<Assigner.TemplateOption> templateOptions(Map<String, TemplateMeta> metas) {
        List<Assigner.TemplateOption> options = new ArrayList<>();
        for (Map.Entry<String, TemplateMeta> entry : metas.entrySet()) {
            RandomConfig.RoomType type = config.roomTypes.get(entry.getKey());
            int weight = type == null ? 1 : type.weight();
            int minCount = type == null ? 0 : type.minCount();
            int maxCount = type == null ? 0 : type.maxCount();
            options.add(new Assigner.TemplateOption(entry.getKey(), entry.getValue().tags(),
                    weight, minCount, maxCount));
        }
        options.sort((left, right) -> left.typeId().compareTo(right.typeId()));
        return options;
    }

    private List<Assigner.MandatoryRule> mandatoryRules() {
        List<Assigner.MandatoryRule> rules = new ArrayList<>();
        for (RandomConfig.MandatoryRule rule : config.mandatory.values()) {
            rules.add(new Assigner.MandatoryRule(rule.tag(), rule.minCount(), rule.maxCount(),
                    rule.placementRule(), rule.percentile()));
        }
        return rules;
    }

    private void buildWorld(RandomInstance instance, Bake.Result baked,
            Map<String, TemplateMeta> metas, Map<String, StructureNbt.StructureData> templates) {
        dispatcher.runGlobal(plugin, () -> {
            World world;
            try {
                world = WorldBootstrap.createWorld(instance.instanceId(), config.world.type());
            } catch (RuntimeException exception) {
                failInstance(instance, "world_create_failed");
                return;
            }
            File flowFile = WorldFolders.dataDir(worldContainer, instance.instanceId())
                    .resolve("flow.yml").toFile();
            try {
                Files.createDirectories(flowFile.getParentFile().toPath());
                YamlFiles.save(flowFile, baked.flowRoot());
            } catch (IOException exception) {
                failInstance(instance, "flow_write_failed");
                return;
            }
            BuildPipeline.Plan plan = BuildPipeline.plan(instance.layout(),
                    List.of(), metas, templates, config.corridor.floorBlock(), "stone_bricks");
            new BuildPipeline().start(plugin, dispatcher, world, plan,
                    config.limits.blocksPerTick(),
                    () -> dispatcher.runGlobal(plugin, () -> finishBuild(instance, world, plan)),
                    warning -> instance.record("warn " + warning));
        });
    }

    private void finishBuild(RandomInstance instance, World world, BuildPipeline.Plan plan) {
        BuildValidator.Report report = new BuildValidator().validate(world, instance.layout(), plan);
        if (!report.passed()) {
            instance.record("validate_failed " + report.failures());
            failInstance(instance, "build_validation_failed");
            return;
        }
        int[] spawn = spawnOf(instance);
        BuildPipeline.prewarm(world, spawn[0], spawn[2], config.world.prewarmRadiusChunks());
        spawnCapturedEntities(world, instance);
        DungeonInstance dungeonInstance = new DungeonInstance(instance.instanceId(), "random",
                new WorldCommandQueue(total -> messages.warning(null, "console.world_command_deferred",
                        Map.of("world", instance.instanceId(), "count", String.valueOf(total)))));
        if (!registry.register(dungeonInstance)) {
            failInstance(instance, "register_failed");
            return;
        }
        instance.dungeonInstance(dungeonInstance);
        if (!dungeonInstance.transition(InstanceState.CREATING, InstanceState.MOUNTING)
                || !dungeonInstance.transition(InstanceState.MOUNTING, InstanceState.IDLE)) {
            failInstance(instance, "state_failed");
            return;
        }
        var runtime = flowFactory.apply(dungeonInstance);
        if (runtime != null) {
            dungeonInstance.setFlowRuntime(runtime);
        }
        if (registerHook != null) {
            registerHook.accept(dungeonInstance);
        }
        if (dungeonInstance.flowRuntime() != null) {
            var flowRuntime = dungeonInstance.flowRuntime();
            dungeonInstance.startLoop(plugin, dispatcher, event -> {
                flowRuntime.onEvent(event);
                var scriptSession = dungeonInstance.scriptSession();
                if (scriptSession != null) {
                    scriptSession.onIngress(event);
                }
            }, () -> {
            }, throwable -> failInstance(instance, "runtime_crash"));
        }
        instance.forceState(RandomInstanceState.READY);
        instance.record("ready");
        instanceStore.save(instance);
    }

    private int[] spawnOf(RandomInstance instance) {
        var flowFile = WorldFolders.dataDir(worldContainer, instance.instanceId())
                .resolve("flow.yml").toFile();
        YamlSection root = YamlFiles.load(flowFile);
        YamlSection spawn = root == null ? null : root.getSection("spawn_point");
        YamlSection location = spawn == null ? null : spawn.getSection("location");
        if (location == null) {
            return new int[] {0, 64, 0};
        }
        return new int[] {
                location.getDouble("x", 0.0D).intValue(),
                location.getDouble("y", 64.0D).intValue(),
                location.getDouble("z", 0.0D).intValue()};
    }

    private void spawnCapturedEntities(World world, RandomInstance instance) {
        for (RoomPlacement room : instance.layout().rooms()) {
            for (Map<String, Object> entry : room.meta().entities()) {
                try {
                    org.bukkit.entity.EntityType type = org.bukkit.entity.EntityType.valueOf(
                            String.valueOf(entry.get("type")));
                    double lx = entry.get("x") instanceof Number number ? number.doubleValue() : 0.0D;
                    double ly = entry.get("y") instanceof Number number ? number.doubleValue() : 0.0D;
                    double lz = entry.get("z") instanceof Number number ? number.doubleValue() : 0.0D;
                    double[] local = emaki.jiuwu.craft.dungeon.random.layout.Transforms.transformExact(
                            room.meta(), room.variant(), lx, ly, lz);
                    float yaw = (float) (entry.get("yaw") instanceof Number number ? number.doubleValue() : 0.0D)
                            + room.variant().rotations() * 90.0F;
                    Location location = new Location(world, room.originX() + local[0],
                            room.originY() + local[1], room.originZ() + local[2], yaw, 0.0F);
                    var entity = world.spawnEntity(location, type);
                    if (entry.get("name") != null) {
                        entity.customName(net.kyori.adventure.text.Component.text(
                                String.valueOf(entry.get("name"))));
                    }
                } catch (RuntimeException ignored) {
                }
            }
        }
    }

    private void failInstance(RandomInstance instance, String reason) {
        instance.record("failed " + reason);
        instance.forceState(RandomInstanceState.FAILED);
        instanceStore.save(instance);
        DungeonInstance dungeonInstance = instance.dungeonInstance();
        if (dungeonInstance != null) {
            dungeonInstance.closeLoop();
            registry.unregister(instance.instanceId());
        }
        mapService.remove(instance.instanceId());
        dispatcher.runAsync(plugin, () -> disposer.dispose(instance.instanceId()));
        instance.forceState(RandomInstanceState.DESTROYED);
        instanceStore.save(instance);
    }

    public Result enter(Player player, String instanceId) {
        RandomInstance instance = instances.get(instanceId);
        if (instance == null) {
            return Result.fail("random.unknown_instance", Map.of("instance", String.valueOf(instanceId)));
        }
        if (instance.state() != RandomInstanceState.READY && instance.state() != RandomInstanceState.RUNNING) {
            return Result.fail("random.instance_not_ready", Map.of("instance", instanceId,
                    "state", instance.state().name()));
        }
        World world = Bukkit.getWorld(instanceId);
        if (world == null) {
            return Result.fail("random.world_missing", Map.of("instance", instanceId));
        }
        int[] spawn = spawnOf(instance);
        Location entry = new Location(world, spawn[0] + 0.5D, spawn[1], spawn[2] + 0.5D);
        instance.transition(RandomInstanceState.READY, RandomInstanceState.RUNNING);
        DungeonInstance dungeonInstance = instance.dungeonInstance();
        if (dungeonInstance != null) {
            dungeonInstance.transition(InstanceState.IDLE, InstanceState.RUNNING);
        }
        dispatcher.runEntity(plugin, player, () -> {
            player.teleport(entry);
            giveMap(player, instance);
        }, () -> {
        });
        instance.record("enter " + player.getUniqueId());
        instanceStore.save(instance);
        return Result.ok("random.entered", Map.of("instance", instanceId));
    }

    private void giveMap(Player player, RandomInstance instance) {
        if (!config.treasureMap.enabled() || instance.layout() == null) {
            return;
        }
        List<TreasureMapService.RoomCell> cells = new ArrayList<>();
        int floors = 1;
        for (RoomPlacement room : instance.layout().rooms()) {
            int floor = 1;
            if (!room.meta().anchors().isEmpty()) {
                floor = room.meta().anchors().get(0).floor();
            }
            floors = Math.max(floors, floor);
            String icon = null;
            for (String tag : room.meta().tags()) {
                String mapped = config.treasureMap.targetIcons().get(tag);
                if (mapped != null) {
                    icon = mapped;
                    break;
                }
            }
            cells.add(new TreasureMapService.RoomCell(room.nodeId(),
                    Math.max(0, Math.min(63, 32 + room.nodeId() % 32)),
                    Math.max(0, Math.min(63, 32 + (room.nodeId() / 32) * 4)),
                    floor, icon));
        }
        List<int[]> links = new ArrayList<>();
        for (Placement.LinkRecord link : instance.layout().links()) {
            links.add(new int[] {link.parentNodeId(), link.childNodeId()});
        }
        TreasureMapService.InstanceMap map = mapService.create(instance.instanceId(), cells, links,
                config.treasureMap.showUnexploredOutline(), config.treasureMap.floorMarkers(), floors);
        MapView view = Bukkit.createMap(Bukkit.getWorld(instance.instanceId()));
        mapService.bind(instance.instanceId(), player, view, null);
        ItemStack item = map.createItem(null);
        MapMeta meta = (MapMeta) item.getItemMeta();
        if (meta != null && view != null) {
            meta.setMapView(view);
            item.setItemMeta(meta);
        }
        player.getInventory().addItem(item);
    }

    public Result leave(Player player) {
        String instanceId = null;
        RandomInstance found = null;
        for (RandomInstance instance : instances.values()) {
            if (instance.state() == RandomInstanceState.RUNNING
                    && player.getWorld() != null
                    && player.getWorld().getName().equals(instance.instanceId())) {
                instanceId = instance.instanceId();
                found = instance;
                break;
            }
        }
        if (instanceId == null) {
            return Result.fail("random.not_in_instance", Map.of());
        }
        if (found != null && found.dungeonInstance() != null
                && found.dungeonInstance().flowRuntime() != null) {
            found.dungeonInstance().flowRuntime().clearHintBossBar(player.getUniqueId());
        }
        Location back = player.getLocation();
        var snapshot = player.getWorld().getEnvironment();
        player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
        mapService.explore(instanceId, -1);
        return Result.ok("random.left", Map.of("instance", instanceId));
    }

    public Result stop(String instanceId) {
        RandomInstance instance = instances.get(instanceId);
        if (instance == null) {
            return Result.fail("random.unknown_instance", Map.of("instance", String.valueOf(instanceId)));
        }
        instance.forceState(RandomInstanceState.REWARDING);
        instance.forceState(RandomInstanceState.RESETTING);
        World world = Bukkit.getWorld(instanceId);
        if (world != null) {
            for (Player player : List.copyOf(world.getPlayers())) {
                player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
            }
        }
        DungeonInstance dungeonInstance = instance.dungeonInstance();
        if (dungeonInstance != null) {
            dungeonInstance.closeLoop();
            registry.unregister(instanceId);
        }
        mapService.remove(instanceId);
        instance.forceState(RandomInstanceState.DESTROYED);
        instance.record("destroyed");
        instanceStore.save(instance);
        dispatcher.runAsync(plugin, () -> disposer.dispose(instanceId));
        return Result.ok("random.stopped", Map.of("instance", instanceId));
    }

    public Result replay(String recordName) {
        RandomInstance record = instanceStore.load(recordName);
        if (record == null) {
            return Result.fail("random.record_missing", Map.of("record", String.valueOf(recordName)));
        }
        if (config == null) {
            return Result.fail("random.config_invalid", Map.of("errors", String.join("; ", configErrors)));
        }
        if (!record.configHash().equals(config.configHash)) {
            return Result.fail("random.replay_config_mismatch", Map.of(
                    "record", recordName,
                    "expected", record.configHash(),
                    "actual", config.configHash));
        }
        Topology topology = new TopologyGenerator().generate(record.seeds().topology(), record.roomCount());
        if (!topology.hash().equals(record.topologyHash())) {
            return Result.fail("random.replay_topology_mismatch", Map.of(
                    "record", recordName,
                    "expected", record.topologyHash(),
                    "actual", topology.hash()));
        }
        return Result.ok("random.replay_ok", Map.of(
                "record", recordName,
                "topology-hash", topology.hash()));
    }

    public List<String> records() {
        return instanceStore.listRecords();
    }

    private void sweep() {
        for (RandomInstance instance : instances.values()) {
            if (instance.state() != RandomInstanceState.RUNNING || instance.layout() == null) {
                continue;
            }
            World world = Bukkit.getWorld(instance.instanceId());
            if (world == null) {
                continue;
            }
            for (Player player : world.getPlayers()) {
                Location location = player.getLocation();
                for (RoomPlacement room : instance.layout().rooms()) {
                    if (location.getX() < room.originX()
                            || location.getX() > room.originX() + 1
                            || location.getZ() < room.originZ()
                            || location.getZ() > room.originZ() + 1) {
                        continue;
                    }
                    mapService.explore(instance.instanceId(), room.nodeId());
                }
            }
        }
    }
}
