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
import emaki.jiuwu.craft.corelib.log.LogTag;
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
import emaki.jiuwu.craft.dungeon.random.gen.GenerationFingerprint;
import emaki.jiuwu.craft.dungeon.random.gen.Seeds;
import emaki.jiuwu.craft.dungeon.random.gen.Topology;
import emaki.jiuwu.craft.dungeon.random.gen.TopologyFactory;
import emaki.jiuwu.craft.dungeon.random.layout.Bake;
import emaki.jiuwu.craft.dungeon.random.layout.ConstraintLayoutEngine;
import emaki.jiuwu.craft.dungeon.random.layout.Placement;
import emaki.jiuwu.craft.dungeon.random.layout.Transforms;
import emaki.jiuwu.craft.dungeon.random.layout.Placement.CorridorRun;
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

    public static final String GENERATOR_ID = "constraint";
    public static final int GENERATOR_VERSION = 1;
    private static final int ALLOCATION_LIMIT = 16;

    public record Generated(Layout layout, Topology topology,
            String layoutHash, String templatePoolHash, String generatorId, int algorithmVersion) {
    }

    /** 预览时每份副本最多尝试的种子数; 实际几何通过率远高于此, 触及上限说明配置本身有硬缺口。 */
    private static final int PREVIEW_SEED_RETRIES = 64;

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
    /** 预览世界 -> 发起预览的玩家, 退出时据此销毁世界。 */
    private final Map<String, UUID> previewEntries = new ConcurrentHashMap<>();
    private final Map<String, TemplateStore> stores = new ConcurrentHashMap<>();
    private final Map<String, Map<String, TemplateMeta>> metasByDungeon = new ConcurrentHashMap<>();
    private final Map<String, Map<String, StructureNbt.StructureData>> templatesByDungeon = new ConcurrentHashMap<>();

    private final Map<String, RandomConfig> configs = new ConcurrentHashMap<>();
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
        warnLegacyFile();
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

    /**
     * 逐个副本读取它自己 world/<副本ID>/config.yml 里的 random 段。
     * 任何一个副本配置非法都只影响该副本, 不再整份拒绝。
     */
    public void reloadConfig() {
        configs.clear();
        List<String> errors = new ArrayList<>();
        for (String dungeonId : dungeonIds()) {
            File file = new File(new File(new File(dataFolder, "world"), dungeonId), "config.yml");
            YamlSection root = YamlFiles.load(file);
            if (root == null || root.getSection(RandomConfig.SECTION) == null) {
                errors.add(dungeonId + ": " + RandomConfig.SECTION + " 段缺失");
                messages.warning(LogTag.CONFIG, "console.random_config_section_missing",
                        Map.of("dungeon", dungeonId));
                continue;
            }
            RandomConfig.Loaded loaded = RandomConfig.parse(root);
            if (loaded.valid()) {
                configs.put(dungeonId, loaded.config());
            } else {
                for (String error : loaded.errors()) {
                    errors.add(dungeonId + ": " + error);
                }
            }
        }
        configErrors = List.copyOf(errors);
    }

    /** 取指定副本的随机生成配置; null 表示该副本未配置或配置非法。 */
    public RandomConfig configFor(String dungeonId) {
        return dungeonId == null ? null : configs.get(dungeonId);
    }

    /** 只重载单个副本的随机配置 (编辑世界保存模板后调用)。 */
    public void reloadConfigFor(String dungeonId) {
        if (dungeonId == null || dungeonId.isBlank()) {
            return;
        }
        File file = new File(new File(new File(dataFolder, "world"), dungeonId), "config.yml");
        YamlSection root = YamlFiles.load(file);
        if (root == null || root.getSection(RandomConfig.SECTION) == null) {
            configs.remove(dungeonId);
            return;
        }
        RandomConfig.Loaded loaded = RandomConfig.parse(root);
        if (loaded.valid()) {
            configs.put(dungeonId, loaded.config());
        } else {
            configs.remove(dungeonId);
        }
    }

    private void warnLegacyFile() {
        File legacy = new File(dataFolder, "random-dungeon.yml");
        if (!legacy.isFile()) {
            return;
        }
        messages.warning(LogTag.CONFIG, "console.random_config_legacy_ignored", Map.of());
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
                // transition 已下线: 楼层由门锚点的 Y 位置决定, 不再需要专门的跨层结构模板
                if (TemplateModel.TYPE_LEGACY_TRANSITION.equals(meta.structureType())) {
                    messages.warning(LogTag.CONFIG, "console.random_transition_retired", Map.of(
                            "dungeon", dungeonId, "id", id));
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

    /**
     * 编辑器测试专用: 生成随机副本, 标记为测试实例, READY 后把发起者传送进去。
     * 生成是异步分帧的, 因此在 finishBuild 完成后回调。
     */
    public Result createForTest(String dungeonId, Player player, long seedOverride) {
        Result result = create(dungeonId, 1, seedOverride, player.getUniqueId());
        if (!result.ok()) {
            return result;
        }
        return result;
    }

    public Result create(String dungeonId, int players, Long seedOverride) {
        return create(dungeonId, players, seedOverride, null);
    }

    private Result create(String dungeonId, int players, Long seedOverride, UUID testPlayer) {
        if (dungeonId == null || dungeonId.isBlank()) {
            return Result.fail("random.create_no_dungeon", Map.of());
        }
        RandomConfig config = configFor(dungeonId);
        if (config == null) {
            return Result.fail("random.config_invalid", Map.of("errors",
                    dungeonId + ": " + RandomConfig.SECTION + " 段缺失或非法"));
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
        String templateHash = GenerationFingerprint.templatePool(metas, templates);
        RandomInstance instance = null;
        String lastDiagnostic = "";
        for (int allocation = 0; allocation < ALLOCATION_LIMIT; allocation++) {
            Generated generated = generateSpatial(seed + allocation, roomCount, metas, templates, config);
            if (generated == null) {
                lastDiagnostic = "constraint-search";
                continue;
            }
            Bake.Result baked = Bake.bake(generated.layout(), metas);
            String id = worldNames.next("rnd");
            instance = new RandomInstance(id, dungeonId, seed + allocation,
                    Seeds.derive(seed + allocation), config.configHash, generated.topology().hash(), roomCount,
                    generated.generatorId(), generated.algorithmVersion(), generated.layoutHash(), templateHash);
            instance.testPlayer(testPlayer);
            instance.layout(generated.layout());
            instance.record("generated constraint states=" + generated.layout().attempts());
            instance.forceState(RandomInstanceState.GENERATING);
            instances.put(id, instance);
            instanceStore.save(instance);
            buildWorld(instance, baked, metas, templates, config);
            return Result.ok("random.create_started", Map.of("instance", id,
                    "seed", String.valueOf(seed + allocation), "rooms", String.valueOf(roomCount)));
        }
        return Result.fail("random.allocation_failed", Map.of(
                "limit", String.valueOf(ALLOCATION_LIMIT),
                "diagnostic", lastDiagnostic.isBlank() ? "-" : lastDiagnostic));
    }

    public Generated generateSpatial(long seed, int roomCount, Map<String, TemplateMeta> metas,
            Map<String, StructureNbt.StructureData> templates, RandomConfig config) {
        Seeds seeds = Seeds.derive(seed);
        List<Assigner.TemplateOption> options = templateOptions(metas, config);
        ConstraintLayoutEngine.Result result = new ConstraintLayoutEngine().generate(seeds.layout(),
                roomCount, metas, options, mandatoryRules(config), config, 64);
        if (result.layout() == null) {
            return null;
        }
        return new Generated(result.layout(), TopologyFactory.fromLayout(result.layout()),
                GenerationFingerprint.layout(result.layout()), GenerationFingerprint.templatePool(metas, templates),
                GENERATOR_ID, GENERATOR_VERSION);
    }

    /**
     * 预览: 在同一个临时世界里并排生成 count 份完整副本, 每份间隔约一个区块。
     * 只造方块结构, 不建 flow、不注册实例、不生成出生点。
     */
    public Result preview(Player player, String dungeonId, int count, Long seedOverride, int spacingBlocks) {
        if (dungeonId == null || dungeonId.isBlank()) {
            return Result.fail("random.create_no_dungeon", Map.of());
        }
        RandomConfig config = configFor(dungeonId);
        if (config == null) {
            return Result.fail("random.config_invalid", Map.of("errors", configErrors.isEmpty()
                    ? dungeonId + ": " + RandomConfig.SECTION + " 段缺失"
                    : String.join("; ", configErrors)));
        }
        Map<String, TemplateMeta> metas = metasOf(dungeonId);
        Map<String, StructureNbt.StructureData> templates = templatesOf(dungeonId);
        if (metas.isEmpty()) {
            return Result.fail("random.no_templates", Map.of());
        }
        int roomCount = config.roomCountFor(1);
        long baseSeed = seedOverride != null ? seedOverride
                : java.util.concurrent.ThreadLocalRandom.current().nextLong();
        List<Layout> layouts = new ArrayList<>(count);
        // 每份预览推进种子直到成功: 某个种子搜索不可行不该让整次预览失败
        String lastDiagnostic = "";
        int cursor = 0;
        int maxAttempts = count * PREVIEW_SEED_RETRIES;
        while (layouts.size() < count && cursor < maxAttempts) {
            Generated generated = generateSpatial(baseSeed + cursor++, roomCount, metas, templates, config);
            if (generated == null) {
                lastDiagnostic = "constraint-search";
                continue;
            }
            layouts.add(generated.layout());
        }
        if (layouts.size() < count) {
            return Result.fail("random.preview_layout_failed", Map.of(
                    "index", String.valueOf(layouts.size() + 1),
                    "diagnostic", lastDiagnostic.isBlank() ? "-" : lastDiagnostic));
        }
        String worldName = worldNames.next("rndp");
        dispatcher.runGlobal(plugin, () -> {
            World world;
            try {
                world = WorldBootstrap.createWorld(worldName, config.world.type());
            } catch (RuntimeException exception) {
                return;
            }
            List<BuildPipeline.PlannedBlock> merged = new ArrayList<>();
            int cursorX = 0;
            int entryX = Integer.MIN_VALUE;
            int entryY = Integer.MAX_VALUE;
            int entryZ = 0;
            for (Layout layout : layouts) {
                String layoutCorridor = corridorTypeOf(layout);
                BuildPipeline.Plan plan = BuildPipeline.plan(layout, List.of(), metas, templates,
                        config.corridor.floorBlock(), "stone_bricks",
                        layoutCorridor, layoutCorridor == null ? null : metas.get(layoutCorridor),
                        config.corridor.tileGap());
                // 按该份副本的真实包围盒平移, 保证相邻两份不重叠
                int minX = Integer.MAX_VALUE;
                int maxX = Integer.MIN_VALUE;
                for (RoomPlacement room : layout.rooms()) {
                    int footprint = Transforms
                            .footprintX(room.meta(), room.variant());
                    minX = Math.min(minX, room.originX());
                    maxX = Math.max(maxX, room.originX() + footprint);
                }
                if (minX == Integer.MAX_VALUE) {
                    continue;
                }
                int shift = cursorX - minX;
                for (BuildPipeline.PlannedBlock block : plan.rooms()) {
                    merged.add(new BuildPipeline.PlannedBlock(block.x() + shift, block.y(), block.z(), block.data()));
                }
                for (BuildPipeline.PlannedBlock block : plan.connections()) {
                    merged.add(new BuildPipeline.PlannedBlock(block.x() + shift, block.y(), block.z(), block.data()));
                }
                if (entryX == Integer.MIN_VALUE) {
                    // 落点取第一份副本的根房间中心, 站在房间里而不是虚空
                    for (RoomPlacement room : layout.rooms()) {
                        if (room.nodeId() != 0) {
                            continue;
                        }
                        entryX = room.originX() + shift
                                + Transforms.footprintX(room.meta(), room.variant()) / 2;
                        entryY = room.originY();
                        entryZ = room.originZ()
                                + Transforms.footprintZ(room.meta(), room.variant()) / 2;
                        break;
                    }
                }
                cursorX += (maxX - minX) + spacingBlocks;
            }
            BuildPipeline.Plan shifted = new BuildPipeline.Plan(merged, List.of(), List.of(), merged.size());
            previewEntries.put(worldName, player.getUniqueId());
            final int spawnX = entryX == Integer.MIN_VALUE ? 0 : entryX;
            final int spawnY = entryY == Integer.MAX_VALUE ? 64 : entryY;
            final int spawnZ = entryZ;
            new BuildPipeline().start(plugin, dispatcher, world, shifted,
                    config.limits.blocksPerTick(),
                    () -> deliverPreview(worldName, spawnX, spawnY, spawnZ),
                    warning -> {
                    });
        });
        return Result.ok("random.preview_started", Map.of(
                "world", worldName,
                "count", String.valueOf(count),
                "rooms", String.valueOf(roomCount),
                "seed", String.valueOf(baseSeed)));
    }

    private void deliverPreview(String worldName, int x, int y, int z) {
        UUID owner = previewEntries.get(worldName);
        World world = Bukkit.getWorld(worldName);
        if (owner == null || world == null) {
            return;
        }
        BuildPipeline.prewarm(world, x, z, 2);
        Player player = Bukkit.getPlayer(owner);
        if (player == null || !player.isOnline()) {
            return;
        }
        Location target = new Location(world, x + 0.5D, y + 1.0D, z + 0.5D);
        dispatcher.runEntity(plugin, player, () -> {
            player.teleport(target);
            messages.send(player, "random.preview_entered", Map.of("world", worldName));
        }, () -> {
        });
    }

    public Result leavePreview(Player player) {
        String worldName = player.getWorld() == null ? null : player.getWorld().getName();
        if (worldName == null || previewEntries.remove(worldName) == null) {
            return Result.fail("random.not_in_instance", Map.of());
        }
        Location back = Bukkit.getWorlds().get(0).getSpawnLocation();
        // 先传送出世界再销毁, 否则玩家会卡在被卸载的世界里
        dispatcher.runEntity(plugin, player, () -> {
            player.teleport(back);
            disposer.dispose(worldName);
        }, () -> {
        });
        return Result.ok("random.preview_left", Map.of("world", worldName));
    }

    /** 房间候选: 只含 structure-type=room 的模板, 走廊模板不参与房间抽样。 */
    private List<Assigner.TemplateOption> templateOptions(Map<String, TemplateMeta> metas, RandomConfig config) {
        List<Assigner.TemplateOption> options = new ArrayList<>();
        for (Map.Entry<String, TemplateMeta> entry : metas.entrySet()) {
            if (TemplateModel.TYPE_CORRIDOR.equals(entry.getValue().structureType())) {
                continue;
            }
            RandomConfig.RoomType type = config.roomTypes.get(entry.getKey());
            int weight = type == null || type.weight() == null
                    ? entry.getValue().weight() : type.weight();
            int minCount = type == null ? 0 : type.minCount();
            int maxCount = type == null ? 0 : type.maxCount();
            options.add(new Assigner.TemplateOption(entry.getKey(), entry.getValue().tags(),
                    weight, minCount, maxCount, entry.getValue().anchors().size()));
        }
        options.sort((left, right) -> left.typeId().compareTo(right.typeId()));
        return options;
    }

    /** 布局内已选定的走廊模板; 该布局没有可用走廊模板时返回 null (回退纯程序化地板)。 */
    private String corridorTypeOf(Layout layout) {
        for (CorridorRun run : layout.corridors()) {
            if (run.templateId() != null && !run.templateId().isBlank()) {
                return run.templateId();
            }
        }
        return null;
    }

    private List<Assigner.MandatoryRule> mandatoryRules(RandomConfig config) {
        List<Assigner.MandatoryRule> rules = new ArrayList<>();
        for (RandomConfig.MandatoryRule rule : config.mandatory.values()) {
            rules.add(new Assigner.MandatoryRule(rule.tag(), rule.minCount(), rule.maxCount(),
                    rule.placementRule(), rule.percentile()));
        }
        return rules;
    }

    private void buildWorld(RandomInstance instance, Bake.Result baked,
            Map<String, TemplateMeta> metas, Map<String, StructureNbt.StructureData> templates,
            RandomConfig config) {
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
            String corridorTypeId = corridorTypeOf(instance.layout());
            BuildPipeline.Plan plan = BuildPipeline.plan(instance.layout(),
                    List.of(), metas, templates, config.corridor.floorBlock(), "stone_bricks",
                    corridorTypeId, corridorTypeId == null ? null : metas.get(corridorTypeId),
                    config.corridor.tileGap());
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
        RandomConfig config = configFor(instance.dungeonId());
        BuildPipeline.prewarm(world, spawn[0], spawn[2],
                config == null ? 2 : config.world.prewarmRadiusChunks());
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
        java.util.UUID waiting = instance.testPlayer();
        Player online = waiting == null ? null : Bukkit.getPlayer(waiting);
        if (online != null && online.isOnline()) {
            enter(online, instance.instanceId());
        }
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
                    double[] local = Transforms.transformExact(
                            room.meta(), room.variant(), lx, ly, lz);
                    float yaw = (float) (entry.get("yaw") instanceof Number number ? number.doubleValue() : 0.0D)
                            + room.variant().rotations();
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
        RandomConfig config = configFor(instance.dungeonId());
        if (config == null || !config.treasureMap.enabled() || instance.layout() == null) {
            return;
        }
        List<TreasureMapService.RoomCell> cells = new ArrayList<>();
        List<RoomPlacement> placed = instance.layout().rooms();
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (RoomPlacement room : placed) {
            int x = room.originX() + Transforms
                    .footprintX(room.meta(), room.variant()) / 2;
            int z = room.originZ() + Transforms.footprintZ(room.meta(), room.variant()) / 2;
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
        }
        double scale = Math.min(1.0D, Math.min(60.0D / Math.max(1, maxX - minX),
                60.0D / Math.max(1, maxZ - minZ)));
        int floors = 1;
        for (RoomPlacement room : placed) {
            int floor = instance.layout().floorOf(room.nodeId());
            floors = Math.max(floors, floor);
            String icon = null;
            for (String tag : room.meta().tags()) {
                String mapped = config.treasureMap.targetIcons().get(tag);
                if (mapped != null) {
                    icon = mapped;
                    break;
                }
            }
            int centerX = room.originX() + Transforms
                    .footprintX(room.meta(), room.variant()) / 2;
            int centerZ = room.originZ() + Transforms.footprintZ(room.meta(), room.variant()) / 2;
            cells.add(new TreasureMapService.RoomCell(room.nodeId(),
                    Math.max(0, Math.min(63, 2 + (int) Math.round((centerX - minX) * scale))),
                    Math.max(0, Math.min(63, 2 + (int) Math.round((centerZ - minZ) * scale))),
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
        Result previewLeft = leavePreview(player);
        if (previewLeft.ok()) {
            return previewLeft;
        }
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
        RandomConfig config = configFor(record.dungeonId());
        if (config == null) {
            return Result.fail("random.replay_config_missing", Map.of("record", recordName,
                    "dungeon", String.valueOf(record.dungeonId())));
        }
        if (!GENERATOR_ID.equals(record.generatorId())
                || record.algorithmVersion() != GENERATOR_VERSION || record.layoutHash().isBlank()
                || record.templatePoolHash().isBlank()) {
            return Result.fail("random.replay_unsupported", Map.of("record", recordName));
        }
        if (!record.configHash().equals(config.configHash)) {
            return Result.fail("random.replay_config_mismatch", Map.of(
                    "record", recordName,
                    "expected", record.configHash(),
                    "actual", config.configHash));
        }
        Generated generated = generateSpatial(record.seed(), record.roomCount(), metasOf(record.dungeonId()),
                templatesOf(record.dungeonId()), config);
        if (generated == null || !generated.layoutHash().equals(record.layoutHash())
                || !generated.templatePoolHash().equals(record.templatePoolHash())) {
            return Result.fail("random.replay_layout_mismatch", Map.of("record", recordName,
                    "expected", record.layoutHash(),
                    "actual", generated == null ? "unavailable" : generated.layoutHash()));
        }
        return Result.ok("random.replay_ok", Map.of("record", recordName,
                "topology-hash", generated.topology().hash()));
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
                    if (location.getX() < room.originX() || location.getY() < room.originY()
                            || location.getZ() < room.originZ()
                            || location.getX() > room.originX() + Transforms
                                    .footprintX(room.meta(), room.variant())
                            || location.getY() > room.originY() + room.meta().sizeY()
                            || location.getZ() > room.originZ() + Transforms.footprintZ(room.meta(), room.variant())) {
                        continue;
                    }
                    mapService.explore(instance.instanceId(), room.nodeId());
                }
            }
        }
    }
}
