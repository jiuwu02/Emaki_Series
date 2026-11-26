package emaki.jiuwu.craft.dungeon.editor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.chat.ChatInputService;
import emaki.jiuwu.craft.corelib.editor.EditorDocument;
import emaki.jiuwu.craft.corelib.editor.EditorValidator;
import emaki.jiuwu.craft.corelib.editor.YamlTextDocument;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.dungeon.challenge.ChallengeService;
import emaki.jiuwu.craft.dungeon.config.DungeonConfig;
import emaki.jiuwu.craft.dungeon.type.DungeonType;
import emaki.jiuwu.craft.dungeon.type.DungeonTypeRegistry;
import emaki.jiuwu.craft.dungeon.flow.FlowPoint;
import emaki.jiuwu.craft.dungeon.instance.WorldCommandQueue;
import emaki.jiuwu.craft.dungeon.world.TemplateRepository;
import emaki.jiuwu.craft.dungeon.world.WorldCloner;
import emaki.jiuwu.craft.dungeon.world.WorldDisposer;
import emaki.jiuwu.craft.dungeon.world.WorldFolders;
import emaki.jiuwu.craft.dungeon.world.WorldMounts;
import emaki.jiuwu.craft.dungeon.world.WorldPool;
import emaki.jiuwu.craft.dungeon.world.WorldTypeSupport;
import emaki.jiuwu.craft.corelib.debug.FailureRecorder;
import java.nio.file.Path;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;
import emaki.jiuwu.craft.corelib.log.LogTag;

public final class EditorService implements Listener {

    public static final String EDIT_WORLD_PREFIX = "emd_edit_";

    private volatile java.util.function.Supplier<emaki.jiuwu.craft.dungeon.random.RandomDungeonService> randomServiceOf;
    private volatile java.util.function.Function<String, DungeonConfig> dungeonConfigOf;

    static final List<String> REGION_SUBDIRS = List.of("region", "entities");
    static final List<String> WORLD_DATA_FILES = List.of(
            "level.dat",
            "data/minecraft/game_rules.dat");

    public static List<String> markTypes() { return EditorServiceDelegate.markTypes(); }

    final JavaPlugin plugin;
    final ExecutionDispatcher dispatcher;
    final MessageService messages;
    private final ChatInputService chatInput;
    private final WorldCloner cloner;
    final WorldDisposer disposer;
    final TemplateRepository templates;
    final EditorStateStore states;
    final File worldContainer;
    final File dataFolder;
    final Map<UUID, EditorSession> sessions = new ConcurrentHashMap<>();
    volatile WorldPool pool;
    volatile Map<String, DungeonConfig> configCache;
    volatile DungeonTypeRegistry typeRegistry;
    volatile ChallengeService challenge;
    volatile EditorGui gui;
    volatile EditorModuleMarkers markers;

    public EditorService(JavaPlugin plugin,
            ExecutionDispatcher dispatcher,
            MessageService messages,
            ChatInputService chatInput,
            WorldCloner cloner,
            WorldDisposer disposer,
            TemplateRepository templates,
            EditorStateStore states,
            File worldContainer,
            File dataFolder) {
        this.plugin = plugin;
        this.dispatcher = dispatcher;
        this.messages = messages;
        this.chatInput = chatInput;
        this.cloner = cloner;
        this.disposer = disposer;
        this.templates = templates;
        this.states = states;
        this.worldContainer = worldContainer;
        this.dataFolder = dataFolder;
    }

    public void bindPool(WorldPool pool) { new EditorServiceDelegate(this).bindPool(pool); }

    public void bindConfigCache(Map<String, DungeonConfig> configCache) { new EditorServiceDelegate(this).bindConfigCache(configCache); }

    public void bindTypeRegistry(DungeonTypeRegistry typeRegistry) { new EditorServiceDelegate(this).bindTypeRegistry(typeRegistry); }

    public void bindChallenge(ChallengeService challenge) { new EditorServiceDelegate(this).bindChallenge(challenge); }

    public void bindRandomService(java.util.function.Supplier<emaki.jiuwu.craft.dungeon.random.RandomDungeonService> supplier) {
        this.randomServiceOf = supplier;
    }

    public void bindDungeonConfig(java.util.function.Function<String, DungeonConfig> supplier) {
        this.dungeonConfigOf = supplier;
    }

    public void bindGui(EditorGui gui) { new EditorServiceDelegate(this).bindGui(gui); }

    public void bindMarkers(EditorModuleMarkers markers) { new EditorServiceDelegate(this).bindMarkers(markers); }

    public void refreshMarkers(EditorSession session) { new EditorServiceDelegate(this).refreshMarkers(session); }

    public void refreshMarkersOf(UUID playerId) {
        EditorSession session = playerId == null ? null : sessions.get(playerId);
        if (session != null) {
            refreshMarkers(session);
        }
    }

    public boolean hasSession(UUID playerId) { return new EditorServiceDelegate(this).hasSession(playerId); }

    public EditorSession sessionOf(UUID playerId) { return new EditorServiceDelegate(this).sessionOf(playerId); }

    public List<EditorSession> allSessions() { return new EditorServiceDelegate(this).allSessions(); }

    public void open(Player player, String dungeonId) {
        UUID playerId = player.getUniqueId();
        if (sessions.containsKey(playerId)) {
            messages.send(player, "editor.already_open", Map.of());
            return;
        }
        if (templates.templateDir(dungeonId).isEmpty()) {
            messages.send(player, "editor.unknown_dungeon", Map.of("dungeon", String.valueOf(dungeonId)));
            return;
        }
        String worldName = EDIT_WORLD_PREFIX + dungeonId;
        if (!states.tryAcquire(dungeonId, playerId, worldName)) {
            messages.send(player, "editor.locked", Map.of("dungeon", dungeonId));
            return;
        }
        EditorSession session = new EditorSession(playerId, dungeonId, worldName,
                new WorldCommandQueue(total -> messages.warning(null, "console.world_command_deferred", Map.of(
                        "world", worldName,
                        "count", String.valueOf(total)))));
        session.origin(player.getLocation());
        try {
            templates.ensureFlowFile(dungeonId);
            File templateDir = templates.templateDir(dungeonId).orElseThrow();
            session.configDoc(EditorDocument.open(plugin.getLogger(),
                    new File(templateDir, "config.yml").toPath(), EditorValidator.yamlOnly()));
            File flowFile = new File(templateDir, "flow.yml");
            session.flowDoc(EditorDocument.open(plugin.getLogger(),
                    flowFile.toPath(), EditorValidator.yamlOnly()));
        } catch (IOException | RuntimeException exception) {
            states.release(dungeonId);
            messages.send(player, "editor.open_failed", Map.of(
                    "error", String.valueOf(exception.getMessage())));
            return;
        }
        sessions.put(playerId, session);
        messages.send(player, "editor.opening", Map.of("dungeon", dungeonId));
        File templateDir = templates.templateDir(dungeonId).orElse(null);
        if (templateDir == null) {
            failOpen(session, "template_missing");
            return;
        }
        cloner.cloneAsync(templateDir, worldName).whenComplete((ignored, error) -> {
            if (error != null) {
                failOpen(session, String.valueOf(error.getMessage()));
                return;
            }
            dispatcher.runGlobal(plugin, () -> mount(session));
        });
    }

    private void failOpen(EditorSession session, String reason) {
        sessions.remove(session.playerId());
        states.release(session.dungeonId());
        dispatcher.runAsync(plugin, () -> {
            for (Path candidate : WorldFolders
                    .allDirs(worldContainer, session.editWorldName())) {
                WorldCloner.deleteTree(candidate);
            }
        });
        Player player = Bukkit.getPlayer(session.playerId());
        if (player != null) {
            messages.send(player, "editor.open_failed", Map.of("error", reason));
        }
    }

    private void mount(EditorSession session) {
        if (sessions.get(session.playerId()) != session) {
            dispatcher.runAsync(plugin, () -> {
                for (Path candidate : WorldFolders
                        .allDirs(worldContainer, session.editWorldName())) {
                    WorldCloner.deleteTree(candidate);
                }
            });
            states.release(session.dungeonId());
            return;
        }
        World world;
        try {
            world = WorldMounts.create(WorldTypeSupport.creator(session.editWorldName()));
            if (world == null) {
                throw new IllegalStateException("createWorld returned null");
            }
            world.setDifficulty(Difficulty.PEACEFUL);
            world.setGameRule(GameRules.SPAWN_MOBS, Boolean.FALSE);
        } catch (Throwable throwable) {
            failOpen(session, String.valueOf(throwable.getMessage()));
            return;
        }
        session.editWorld(world);
        captureRegionSnapshot(session);
        Player player = Bukkit.getPlayer(session.playerId());
        if (player == null) {
            discardSession(session);
            return;
        }
        dispatcher.runEntity(plugin, player, () -> {
            if (sessions.get(session.playerId()) != session) {
                return;
            }
            Location fresh = player.getLocation();
            session.origin(fresh);
            rememberReturn(player, fresh);
            session.ready(true);
            player.teleport(world.getSpawnLocation());
            messages.send(player, "editor.opened", Map.of(
                    "dungeon", session.dungeonId(),
                    "world", session.editWorldName()
            ));
            refreshMarkers(session);
            if (gui != null) {
                gui.openMain(player);
            }
        }, () -> discardSession(session));
    }

    public void openMenu(Player player) {
        EditorGui bound = gui;
        if (bound != null && sessions.containsKey(player.getUniqueId())) {
            bound.openMain(player);
        }
    }

    public String dungeonTypeOf(UUID playerId) {
        EditorSession session = sessions.get(playerId);
        if (session == null) {
            return "";
        }
        DungeonConfig fresh = templates.findConfig(session.dungeonId());
        if (fresh != null) {
            return fresh.type();
        }
        DungeonConfig config = configCache == null ? null : configCache.get(session.dungeonId());
        return config == null ? "" : config.type();
    }

    public void mark(Player player, String rawType, String rawId) {
        mark(player, rawType, rawId, null);
    }

    public void mark(Player player, String rawType, String rawId, FlowPoint clickPoint) {
        EditorSession session = requireReady(player);
        if (session == null) {
            return;
        }
        String type = Texts.lower(rawType);
        EditorModules.ModuleSpec spec = EditorModules.of(type);
        if (spec == null) {
            messages.send(player, "editor.mark_unknown_type", Map.of("type", String.valueOf(rawType)));
            return;
        }
        boolean region = spec.coord() == EditorModules.Coord.REGION;
        boolean pointRequired = spec.coord() == EditorModules.Coord.POINT;
        FlowPoint first = session.pos1();
        FlowPoint second = session.pos2();
        if (region) {
            if (!emaki.jiuwu.craft.dungeon.type.DungeonTypeRegistry.RANDOM
                    .equals(dungeonTypeOf(player.getUniqueId()))) {
                messages.send(player, "editor.random_only", Map.of());
                return;
            }
            if (first == null && second == null) {
                FlowPoint point = clickPoint != null ? clickPoint : lookPoint(player);
                if (point == null) {
                    messages.send(player, "editor.no_target", Map.of());
                    return;
                }
                session.pos1(point);
                messages.send(player, "editor.pos_set", Map.of("which", "1", "coord", formatPoint(point)));
                messages.send(player, "editor.pos_auto", Map.of());
                return;
            }
            if (first == null) {
                messages.send(player, "editor.pos_missing", Map.of("which", "1"));
                return;
            }
            if (second == null) {
                messages.send(player, "editor.pos_missing", Map.of("which", "2"));
                return;
            }
        }
        FlowPoint point = pointRequired ? (clickPoint != null ? clickPoint : lookPoint(player)) : null;
        if (pointRequired && point == null) {
            messages.send(player, "editor.no_target", Map.of());
            return;
        }
        String id = Texts.isBlank(rawId) ? null : Texts.normalizeId(rawId);
        String section = spec.section();
        EditorDocument flowDoc = session.flowDoc();
        EditorDocument.SaveResult result;
        String[] assigned = new String[1];
        try {
            synchronized (flowDoc) {
                YamlTextDocument view = flowDoc.view();
                if (spec.single()) {
                    if (view.value(section) != null) {
                        messages.send(player, "editor.mark_dup", Map.of("id", section));
                        return;
                    }
                    assigned[0] = section;
                    Map<String, Object> node = markNode(spec, section, point, first, second, view);
                    result = flowDoc.mutate(document -> document.set(node, section));
                } else {
                    String nodeId = id == null ? nextId(view, section, spec.prefix()) : id;
                    assigned[0] = nodeId;
                    if (view.value(section, nodeId) != null) {
                        messages.send(player, "editor.mark_dup", Map.of("id", nodeId));
                        return;
                    }
                    Map<String, Object> node = markNode(spec, nodeId, point, first, second, view);
                    result = flowDoc.mutate(document -> document.set(node, section, nodeId));
                }
            }
        } catch (RuntimeException exception) {
            messages.send(player, "editor.mark_failed", Map.of(
                    "error", String.valueOf(exception.getMessage())));
            return;
        }
        if (!result.saved()) {
            messages.send(player, "editor.mark_failed", Map.of("error", result.detail()));
            return;
        }
        session.dirty(true);
        String coord = region ? formatRegion(first, second)
                : pointRequired ? formatPoint(point) : "-";
        String mark = type + ":" + assigned[0] + " @ " + coord;
        session.stagedMarks().add(mark);
        refreshMarkers(session);
        messages.send(player, "editor.mark_ok", Map.of(
                "type", type,
                "id", assigned[0],
                "coord", coord
        ));
    }

    public void startTest(Player player) {
        EditorSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            messages.send(player, "editor.no_session", Map.of());
            return;
        }
        if (!session.ready() || session.editWorld() == null) {
            messages.send(player, "editor.not_ready", Map.of());
            return;
        }
        if (session.dirty()) {
            messages.send(player, "editor.test_dirty", Map.of());
            return;
        }
        if (isRandomType(session.dungeonId())) {
            startRandomTest(player, session);
            return;
        }
        if (challenge == null) {
            messages.send(player, "general.invalid_args", Map.of());
            return;
        }
        var result = challenge.apiEnter(player, session.dungeonId(), true);
        if (result.isSuccess()) {
            messages.send(player, "editor.test_started", Map.of("dungeon", session.dungeonId()));
            return;
        }
        messages.send(player, "editor.test_failed", Map.of(
                "code", String.valueOf(result.code())));
    }

    private boolean isRandomType(String dungeonId) {
        if (randomServiceOf == null) {
            return false;
        }
        var service = randomServiceOf.get();
        if (service == null) {
            return false;
        }
        var dungeonConfig = dungeonConfigOf == null ? null : dungeonConfigOf.apply(dungeonId);
        return dungeonConfig != null
                && emaki.jiuwu.craft.dungeon.type.DungeonTypeRegistry.RANDOM.equals(dungeonConfig.type());
    }

    /** random 类型副本的测试: 走随机生成链路, 生成完成 READY 后自动传送进去。 */
    private void startRandomTest(Player player, EditorSession session) {
        var service = randomServiceOf == null ? null : randomServiceOf.get();
        if (service == null) {
            messages.send(player, "editor.test_random_unavailable", Map.of());
            return;
        }
        long seed = java.util.concurrent.ThreadLocalRandom.current().nextLong();
        var result = service.createForTest(session.dungeonId(), player, seed);
        if (!result.ok()) {
            messages.send(player, result.messageKey(), result.params());
            return;
        }
        messages.send(player, "editor.test_random_started", result.params());
    }

    public void save(Player player) {
        save(player, false);
    }

    public void save(Player player, boolean exitAfter) {
        EditorSession session = requireReady(player);
        if (session == null) {
            return;
        }
        session.exitPending(exitAfter);
        if (session.queue().pending() > 0 || session.inFlight() > 0) {
            session.exitPending(false);
            messages.send(player, "editor.save_busy", Map.of());
            return;
        }
        EditorDocument.SaveResult configResult;
        EditorDocument.SaveResult flowResult;
        synchronized (session.configDoc()) {
            configResult = session.configDoc().flush(backupRoot().toPath());
        }
        synchronized (session.flowDoc()) {
            flowResult = session.flowDoc().flush(backupRoot().toPath());
        }
        if (!configResult.saved() || !flowResult.saved()) {
            EditorDocument.SaveResult failed = !flowResult.saved() ? flowResult : configResult;
            session.exitPending(false);
            messages.send(player, "editor.save_failed", Map.of(
                    "error", failed.status().name() + " " + failed.detail()));
            return;
        }
        World world = session.editWorld();
        dispatcher.runGlobal(plugin, () -> {
            WorldMounts.save(world, () -> dispatcher.runAsync(plugin, () -> finishSave(session, copyChangedRegions(session))));
        });
    }

    private void finishSave(EditorSession session, boolean terrainChanged) { new EditorServiceDelegate(this).finishSave(session, terrainChanged); }

    public void discardChanges(Player player) { new EditorServiceDelegate(this).discardChanges(player); }

    File editDataDir(EditorSession session) { return new EditorServiceDelegate(this).editDataDir(session); }

    private void captureRegionSnapshot(EditorSession session) { new EditorServiceDelegate(this).captureRegionSnapshot(session); }

    private boolean copyChangedRegions(EditorSession session) { return new EditorServiceDelegate(this).copyChangedRegions(session); }

    public void listMarks(Player player) {
        EditorSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            messages.send(player, "editor.no_session", Map.of());
            return;
        }
        if (session.stagedMarks().isEmpty()) {
            messages.send(player, "editor.marks_empty", Map.of());
            return;
        }
        messages.send(player, "editor.marks_header", Map.of(
                "count", String.valueOf(session.stagedMarks().size())));
        for (String mark : session.stagedMarks()) {
            messages.send(player, "editor.marks_entry", Map.of("mark", mark));
        }
    }

    EditorSession requireReady(Player player) { return new EditorServiceDelegate(this).requireReady(player); }

    FlowPoint lookPoint(Player player) { return new EditorServiceDelegate(this).lookPoint(player); }

    public record ModuleRef(String type, String nodeId) {
    }

    public ModuleRef moduleAt(EditorSession session, Block block) {
        EditorModuleMarkers markerRef = markers;
        if (markerRef == null || block == null) {
            return null;
        }
        YamlTextDocument view = session.flowDoc().view();
        for (EditorModules.Coord coord : List.of(EditorModules.Coord.POINT, EditorModules.Coord.REGION)) {
            for (EditorModules.ModuleSpec spec : EditorModules.all()) {
                if (spec.coord() != coord) {
                    continue;
                }
                Object raw = view.value(spec.section());
                if (!(raw instanceof Map<?, ?> value)) {
                    continue;
                }
                if (spec.single()) {
                    ModuleRef hit = hitNode(markerRef, spec, spec.section(), value, block);
                    if (hit != null) {
                        return hit;
                    }
                    continue;
                }
                for (Map.Entry<?, ?> entry : value.entrySet()) {
                    String nodeId = String.valueOf(entry.getKey());
                    if (!(entry.getValue() instanceof Map<?, ?> node)) {
                        continue;
                    }
                    ModuleRef hit = hitNode(markerRef, spec, nodeId, node, block);
                    if (hit != null) {
                        return hit;
                    }
                }
            }
        }
        return null;
    }

    private ModuleRef hitNode(EditorModuleMarkers markerRef, EditorModules.ModuleSpec spec,
            String nodeId, Map<?, ?> node, Block block) {
        EditorModuleMarkers.Box box = markerRef.boxOf(node);
        if (box == null) {
            return null;
        }
        boolean hit;
        if (spec.coord() == EditorModules.Coord.POINT) {
            hit = Math.floor(box.centerX()) == block.getX()
                    && Math.floor(box.centerY()) == block.getY()
                    && Math.floor(box.centerZ()) == block.getZ();
        } else {
            hit = block.getX() >= Math.floor(box.minX()) && block.getX() <= Math.floor(box.maxX())
                    && block.getY() >= Math.floor(box.minY()) && block.getY() <= Math.floor(box.maxY())
                    && block.getZ() >= Math.floor(box.minZ()) && block.getZ() <= Math.floor(box.maxZ());
        }
        return hit ? new ModuleRef(spec.type(), nodeId) : null;
    }

    public void deleteModule(Player player, String rawType, String rawNodeId) {
        EditorSession session = requireReady(player);
        if (session == null) {
            return;
        }
        EditorModules.ModuleSpec spec = EditorModules.of(Texts.lower(rawType));
        String nodeId = Texts.isBlank(rawNodeId) ? null : rawNodeId;
        if (spec == null) {
            messages.send(player, "editor.mark_unknown_type", Map.of("type", String.valueOf(rawType)));
            return;
        }
        EditorDocument flowDoc = session.flowDoc();
        EditorDocument.SaveResult result;
        synchronized (flowDoc) {
            YamlTextDocument view = flowDoc.view();
            if (spec.single()) {
                if (view.value(spec.section()) == null) {
                    messages.send(player, "editor.module_missing", Map.of("id", String.valueOf(nodeId)));
                    return;
                }
                result = flowDoc.mutate(document -> document.remove(spec.section()));
            } else {
                if (nodeId == null || view.value(spec.section(), nodeId) == null) {
                    messages.send(player, "editor.module_missing", Map.of("id", String.valueOf(nodeId)));
                    return;
                }
                String target = nodeId;
                result = flowDoc.mutate(document -> document.remove(spec.section(), target));
            }
        }
        if (!result.saved()) {
            messages.send(player, "editor.module_delete_failed", Map.of("error", result.detail()));
            return;
        }
        session.dirty(true);
        refreshMarkers(session);
        messages.send(player, "editor.module_deleted", Map.of(
                "type", spec.type(),
                "id", String.valueOf(nodeId)));
    }

    public void applyRegion(Player player, String rawType, String rawNodeId) {
        EditorSession session = requireReady(player);
        if (session == null) {
            return;
        }
        EditorModules.ModuleSpec spec = EditorModules.of(Texts.lower(rawType));
        String nodeId = Texts.isBlank(rawNodeId) ? null : rawNodeId;
        if (spec == null) {
            messages.send(player, "editor.mark_unknown_type", Map.of("type", String.valueOf(rawType)));
            return;
        }
        FlowPoint first = session.pos1();
        FlowPoint second = session.pos2();
        if (first == null) {
            messages.send(player, "editor.pos_missing", Map.of("which", "1"));
            return;
        }
        if (second == null) {
            messages.send(player, "editor.pos_missing", Map.of("which", "2"));
            return;
        }
        Map<String, Object> region = nestedRegion(first, second);
        EditorDocument flowDoc = session.flowDoc();
        EditorDocument.SaveResult result;
        synchronized (flowDoc) {
            YamlTextDocument view = flowDoc.view();
            if (spec.single()) {
                if (view.value(spec.section()) == null) {
                    messages.send(player, "editor.module_missing", Map.of("id", String.valueOf(nodeId)));
                    return;
                }
                result = flowDoc.mutate(document -> document.set(region, spec.section(), "region"));
            } else {
                if (nodeId == null || view.value(spec.section(), nodeId) == null) {
                    messages.send(player, "editor.module_missing", Map.of("id", String.valueOf(nodeId)));
                    return;
                }
                String target = nodeId;
                result = flowDoc.mutate(document -> document.set(region, spec.section(), target, "region"));
            }
        }
        if (!result.saved()) {
            messages.send(player, "editor.region_update_failed", Map.of("error", result.detail()));
            return;
        }
        session.dirty(true);
        refreshMarkers(session);
        messages.send(player, "editor.region_updated", Map.of(
                "type", spec.type(),
                "id", String.valueOf(nodeId),
                "coord", formatRegion(first, second)));
    }

    private Map<String, Object> markNode(EditorModules.ModuleSpec spec, String id, FlowPoint point,
            FlowPoint first, FlowPoint second, YamlTextDocument view) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", id);
        node.put("enabled", true);
        switch (spec.type()) {
            case "mob_spawn" -> {
                node.put("location", locationMap(point));
                Map<String, Object> trigger = new LinkedHashMap<>();
                trigger.put("range", 8);
                trigger.put("min_players", 1);
                trigger.put("once", true);
                node.put("trigger", trigger);
                node.put("waves", new ArrayList<>());
            }
            case "room" -> {
                Object rooms = view.value("room_zones");
                node.put("order", rooms instanceof Map<?, ?> existing ? existing.size() + 1 : 1);
                node.put("region", nestedRegion(first, second));
            }
            case "gate" -> {
                node.put("gate_id", id);
                node.put("door_regions", new ArrayList<>(List.of(flatRegion(first, second))));
                node.put("logic", "AND");
                node.put("open_mode", "blocks");
            }
            case "option" -> {
                node.put("option_id", id);
                node.put("location", locationMap(point));
                node.put("select_mode", "single");
                node.put("time_limit_seconds", 0);
                node.put("min_select", 1);
                node.put("max_select", 1);
                Map<String, Object> optionCase = new LinkedHashMap<>();
                optionCase.put("label", "main");
                optionCase.put("case", "main");
                optionCase.put("ending", "");
                optionCase.put("seal_regions", new ArrayList<>());
                node.put("cases", new LinkedHashMap<>(Map.of("main", optionCase)));
            }
            case "trigger" -> {
                node.put("region", nestedRegion(first, second));
                node.put("direction", "enter");
                node.put("conditions", new ArrayList<>());
            }
            case "chest" -> {
                node.put("location", locationMap(point));
                node.put("loot_table", "");
                node.put("conditions", new ArrayList<>());
            }
            case "exit" -> {
                node.put("location", locationMap(point));
                node.put("need_confirm", true);
            }
            case "entry" -> {
                node.put("location", locationMap(point));
            }
            case "checkpoint" -> {
                node.put("record_id", id);
                node.put("min_players", 1);
                node.put("region", nestedRegion(first, second));
            }
            case "respawn" -> {
                node.put("location", locationMap(point));
                node.put("checkpoint", "");
            }
            case "block" -> {
                node.put("location", locationMap(point));
                node.put("click", "right");
                node.put("conditions", new ArrayList<>());
                node.put("actions", new ArrayList<>());
            }
            case "tip" -> {
                node.put("location", locationMap(point));
                node.put("text", "");
                node.put("type", "message");
                node.put("conditions", new ArrayList<>());
            }
            case "teleport" -> {
                node.put("region", nestedRegion(first, second));
                node.put("target", locationMap(first));
                node.put("conditions", new ArrayList<>());
                node.put("all_members", false);
            }
            case "boundary" -> {
                node.put("region", nestedRegion(first, second));
            }
            case "effect" -> {
                node.put("region", nestedRegion(first, second));
                node.put("effects", new ArrayList<>());
                node.put("interval_ticks", 40);
            }
            case "mechanism" -> {
                node.put("region", nestedRegion(first, second));
                node.put("targets", new LinkedHashMap<>());
                node.put("conditions", new ArrayList<>());
            }
            case "room_timer" -> {
                node.put("region", nestedRegion(first, second));
                node.put("seconds", 60);
            }
            case "kill_target" -> {
                node.put("target", "");
                node.put("count", 1);
            }
            case "item" -> {
                node.put("location", locationMap(point));
                node.put("item", "");
                node.put("amount", 1);
                node.put("consume", false);
            }
            case "fx" -> {
                node.put("location", locationMap(point));
                node.put("sound", "");
                node.put("particle", "");
                node.put("radius", 8.0D);
            }
            case "temp_item" -> {
                node.put("item", "");
                node.put("amount", 1);
                node.put("timing", "entry");
                node.put("hint", "");
            }
            default -> {
            }
        }
        return node;
    }

    String nextId(EditorSession session, EditorModules.ModuleSpec spec) {
        return new EditorServiceDelegate(this).nextId(session.flowDoc().view(), spec.section(), spec.prefix());
    }

    private String nextId(YamlTextDocument view, String section, String prefix) { return new EditorServiceDelegate(this).nextId(view, section, prefix); }

    private Map<String, Object> locationMap(FlowPoint point) { return new EditorServiceDelegate(this).locationMap(point); }

    private Map<String, Object> nestedRegion(FlowPoint first, FlowPoint second) {
        long[] bounds = bounds(first, second);
        Map<String, Object> min = new LinkedHashMap<>();
        min.put("x", bounds[0]);
        min.put("y", bounds[1]);
        min.put("z", bounds[2]);
        Map<String, Object> max = new LinkedHashMap<>();
        max.put("x", bounds[3]);
        max.put("y", bounds[4]);
        max.put("z", bounds[5]);
        Map<String, Object> region = new LinkedHashMap<>();
        region.put("min", min);
        region.put("max", max);
        return region;
    }

    private Map<String, Object> flatRegion(FlowPoint first, FlowPoint second) {
        long[] bounds = bounds(first, second);
        Map<String, Object> region = new LinkedHashMap<>();
        region.put("min.x", bounds[0]);
        region.put("min.y", bounds[1]);
        region.put("min.z", bounds[2]);
        region.put("max.x", bounds[3]);
        region.put("max.y", bounds[4]);
        region.put("max.z", bounds[5]);
        return region;
    }

    private long[] bounds(FlowPoint first, FlowPoint second) { return new EditorServiceDelegate(this).bounds(first, second); }

    String formatPoint(FlowPoint point) { return new EditorServiceDelegate(this).formatPoint(point); }

    private String formatRegion(FlowPoint first, FlowPoint second) {
        long[] bounds = bounds(first, second);
        return bounds[0] + "," + bounds[1] + "," + bounds[2] + " -> "
                + bounds[3] + "," + bounds[4] + "," + bounds[5];
    }

    public void exit(Player player) { new EditorServiceDelegate(this).exit(player); }

    public void discardSession(EditorSession session) {
        discardSession(session, "editor.exit_done");
    }

    void discardSession(EditorSession session, String messageKey) {
        if (session == null || !sessions.remove(session.playerId(), session)) {
            return;
        }
        TaskToken pump = session.pump();
        if (pump != null) {
            pump.cancel();
        }
        session.configDoc().discard();
        session.flowDoc().discard();
        EditorModuleMarkers bound = markers;
        if (bound != null) {
            try {
                bound.clear(session);
            } catch (Throwable ignored) {
                FailureRecorder.recordIgnoredFailure("EmakiDungeon", "EditorService.java#7", ignored);
            }
        }
        Location origin = session.origin();
        Runnable unload = () -> {
            preservePlayerDataWhenOffline(session);
            dispatcher.runGlobal(plugin, () -> disposer.dispose(session.editWorldName())
                    .whenComplete((result, error) -> states.release(session.dungeonId())));
        };
        Player player = Bukkit.getPlayer(session.playerId());
        if (player == null) {
            unload.run();
            return;
        }
        dispatcher.runEntity(plugin, player, () -> {
            chatInput.cancel(player);
            try {
                player.closeInventory();
            } catch (Throwable ignored) {
                FailureRecorder.recordIgnoredFailure("EmakiDungeon", "EditorService.java#1", ignored);
            }
            messages.send(player, messageKey, Map.of());
            if (origin != null && player.isOnline()) {
                try {
                    player.teleport(origin);
                    states.clearReturn(player.getUniqueId());
                } catch (Throwable ignored) {
                    FailureRecorder.recordIgnoredFailure("EmakiDungeon", "EditorService.java#2", ignored);
                }
            }
            unload.run();
        }, unload);
    }

    public void closeAll() { new EditorServiceDelegate(this).closeAll(); }

    public void sweepLeftoverWorlds() { new EditorServiceDelegate(this).sweepLeftoverWorlds(); }

    private void preservePlayerDataWhenOffline(EditorSession session) { new EditorServiceDelegate(this).preservePlayerDataWhenOffline(session); }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        markWorldDirty(event.getPlayer(), event.getBlock().getWorld());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        markWorldDirty(event.getPlayer(), event.getBlock().getWorld());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        markWorldDirty(event.getPlayer(), event.getBlockClicked().getWorld());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        markWorldDirty(event.getPlayer(), event.getBlockClicked().getWorld());
    }

    private void markWorldDirty(Player player, World world) {
        EditorSession session = sessions.get(player.getUniqueId());
        if (session != null && session.ready() && world != null
                && world.getName().equals(session.editWorldName())) {
            session.dirty(true);
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        EditorSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            dispatcher.runEntity(plugin, player, () -> restoreReturn(player, null), () -> {
            });
            return;
        }
        dispatcher.runGlobal(plugin, () -> discardSession(session));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        EditorSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        returnPlayer(player, session);
        discardSession(session);
    }

    private void returnPlayer(Player player, EditorSession session) {
        restoreReturn(player, session == null ? null : session.editWorldName());
    }

    void restoreReturn(Player player, String editWorldName) {
        if (player == null) {
            return;
        }
        EditorStateStore.ReturnPoint point = states.returnOf(player.getUniqueId());
        if (point == null) {
            return;
        }
        World world = point.world() == null || point.world().isBlank()
                ? null : Bukkit.getWorld(point.world());
        Location target = world == null
                ? (Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0).getSpawnLocation())
                : new Location(world, point.x(), point.y(), point.z(), point.yaw(), point.pitch());
        if (target == null) {
            states.clearReturn(player.getUniqueId());
            return;
        }
        if (editWorldName != null && player.getWorld() != null
                && !player.getWorld().getName().equals(editWorldName)) {
            states.clearReturn(player.getUniqueId());
            return;
        }
        try {
            player.teleport(target);
        } catch (Throwable ignored) {
            FailureRecorder.recordIgnoredFailure("EmakiDungeon", "EditorService.java#8", ignored);
            return;
        }
        Location now = player.getLocation();
        if (now.getWorld() != null && now.getWorld().equals(target.getWorld())
                && now.distanceSquared(target) < 4.0D) {
            states.clearReturn(player.getUniqueId());
        }
    }

    void rememberReturn(Player player, Location location) {
        if (player == null || location == null || location.getWorld() == null) {
            return;
        }
        states.rememberReturn(player.getUniqueId(), location.getWorld().getName(),
                location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch());
    }

    public void reloadConfigCache(String dungeonId) {
        if (configCache == null) {
            return;
        }
        try {
            DungeonConfig loaded = templates.loadConfig(dungeonId);
            DungeonTypeRegistry registry = typeRegistry;
            if (registry != null) {
                DungeonType resolved = registry.resolve(loaded.type(), dungeonId);
                loaded = loaded.withType(resolved.id());
            }
            configCache.put(dungeonId, loaded);
            var service = randomServiceOf == null ? null : randomServiceOf.get();
            if (service != null) {
                service.reloadConfigFor(dungeonId);
            }
        } catch (RuntimeException exception) {
            messages.warning(null, "console.dungeon_config_invalid", Map.of(
                    "dungeon", dungeonId,
                    "error", String.valueOf(exception.getMessage())
            ));
            return;
        }
        messages.info(null, "console.editor_template_saved", Map.of(
                "dungeon", dungeonId,
                "generation", String.valueOf(states.generationOf(dungeonId))
        ));
    }

    public EditorStateStore states() { return new EditorServiceDelegate(this).states(); }

    public void invalidateReserves(String dungeonId) { new EditorServiceDelegate(this).invalidateReserves(dungeonId); }

    public File backupRoot() { return new EditorServiceDelegate(this).backupRoot(); }
}
