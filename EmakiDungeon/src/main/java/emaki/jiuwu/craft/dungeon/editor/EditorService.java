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
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

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
import emaki.jiuwu.craft.dungeon.instance.WorldCommand;
import emaki.jiuwu.craft.dungeon.instance.WorldCommandQueue;
import emaki.jiuwu.craft.dungeon.world.TemplateRepository;
import emaki.jiuwu.craft.dungeon.world.WorldCloner;
import emaki.jiuwu.craft.dungeon.world.WorldDisposer;
import emaki.jiuwu.craft.dungeon.world.WorldFolders;
import emaki.jiuwu.craft.dungeon.world.WorldPool;
import emaki.jiuwu.craft.corelib.debug.FailureRecorder;
import java.nio.file.Path;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.java.JavaPlugin;

public final class EditorService implements Listener {

    public static final String EDIT_WORLD_PREFIX = "emd_edit_";

    private static final long MAX_FILL_BLOCKS = 1_000_000L;
    static final List<String> REGION_SUBDIRS = List.of("region", "entities");

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

    public void bindGui(EditorGui gui) { new EditorServiceDelegate(this).bindGui(gui); }

    public void bindMarkers(EditorModuleMarkers markers) { new EditorServiceDelegate(this).bindMarkers(markers); }

    public void refreshMarkers(EditorSession session) { new EditorServiceDelegate(this).refreshMarkers(session); }

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
                new WorldCommandQueue(total -> messages.warning("console.world_command_deferred", Map.of(
                        "world", worldName,
                        "count", String.valueOf(total)))));
        session.origin(player.getLocation());
        try {
            templates.ensureFlowFile(dungeonId);
            File templateDir = templates.templateDir(dungeonId).orElseThrow();
            session.configDoc(EditorDocument.open(plugin.getLogger(),
                    new File(templateDir, "config.yml").toPath(), EditorValidator.yamlOnly()));
            session.flowDoc(EditorDocument.open(plugin.getLogger(),
                    new File(templateDir, "flow.yml").toPath(), EditorValidator.yamlOnly()));
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
            world = Bukkit.createWorld(new WorldCreator(session.editWorldName()));
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

    public void markPos(Player player, int which) { new EditorServiceDelegate(this).markPos(player, which); }

    public void mark(Player player, String rawType, String rawId) {
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
            if (first == null && second == null) {
                FlowPoint point = lookPoint(player);
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
        FlowPoint point = pointRequired ? lookPoint(player) : null;
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

    public void save(Player player) {
        EditorSession session = requireReady(player);
        if (session == null) {
            return;
        }
        if (!session.dirty()) {
            messages.send(player, "editor.save_nothing", Map.of());
            return;
        }
        if (session.queue().pending() > 0 || session.inFlight() > 0) {
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
            messages.send(player, "editor.save_failed", Map.of(
                    "error", failed.status().name() + " " + failed.detail()));
            return;
        }
        World world = session.editWorld();
        dispatcher.runGlobal(plugin, () -> {
            world.save();
            dispatcher.runAsync(plugin, () -> finishSave(session, copyChangedRegions(session)));
        });
    }

    private void finishSave(EditorSession session, boolean terrainChanged) { new EditorServiceDelegate(this).finishSave(session, terrainChanged); }

    public void discardChanges(Player player) { new EditorServiceDelegate(this).discardChanges(player); }

    public void fill(Player player, String rawBlock) {
        EditorSession session = requireReady(player);
        if (session == null) {
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
        BlockData data;
        try {
            data = Bukkit.createBlockData(rawBlock);
        } catch (IllegalArgumentException exception) {
            messages.send(player, "editor.fill_invalid", Map.of("block", String.valueOf(rawBlock)));
            return;
        }
        long[] bounds = bounds(first, second);
        long volume = (bounds[3] - bounds[0] + 1L) * (bounds[4] - bounds[1] + 1L)
                * (bounds[5] - bounds[2] + 1L);
        if (volume <= 0L || volume > MAX_FILL_BLOCKS) {
            messages.send(player, "editor.fill_too_large", Map.of(
                    "count", String.valueOf(volume),
                    "limit", String.valueOf(MAX_FILL_BLOCKS)));
            return;
        }
        if (session.queue().isBackpressured()) {
            messages.send(player, "editor.fill_busy", Map.of());
            return;
        }
        ensurePump(session);
        int cost = (int) Math.min(volume, Integer.MAX_VALUE);
        session.queue().submit(new emaki.jiuwu.craft.dungeon.instance.WorldCommand() {
            @Override
            public int blockCost() {
                return cost;
            }

            @Override
            public void execute() {
                World world = Bukkit.getWorld(session.editWorldName());
                if (world == null) {
                    return;
                }
                for (long x = bounds[0]; x <= bounds[3]; x++) {
                    for (long y = bounds[1]; y <= bounds[4]; y++) {
                        for (long z = bounds[2]; z <= bounds[5]; z++) {
                            world.getBlockAt((int) x, (int) y, (int) z).setBlockData(data, false);
                        }
                    }
                }
            }
        });
        session.dirty(true);
        messages.send(player, "editor.fill_started", Map.of("count", String.valueOf(volume)));
    }

    private void ensurePump(EditorSession session) {
        if (session.pump() != null || session.editWorld() == null) {
            return;
        }
        Location anchor = session.editWorld().getSpawnLocation();
        session.pump(dispatcher.runGlobalTimer(plugin, () -> {
            if (sessions.get(session.playerId()) != session) {
                return;
            }
            List<WorldCommand> batch = session.queue().drain();
            if (batch.isEmpty()) {
                return;
            }
            session.inFlightAdd(batch.size());
            for (WorldCommand command : batch) {
                dispatcher.runAtLocation(plugin, anchor, () -> {
                    try {
                        command.execute();
                    } catch (Throwable throwable) {
                        command.fail(throwable);
                        messages.warning("console.editor_fill_command_failed", Map.of(
                                "error", String.valueOf(throwable)));
                    } finally {
                        session.inFlightAdd(-1);
                    }
                });
            }
        }, 1L, 1L));
    }

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

    private Map<String, Object> markNode(EditorModules.ModuleSpec spec, String id, FlowPoint point,
            FlowPoint first, FlowPoint second, YamlTextDocument view) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", id);
        node.put("enabled", true);
        switch (spec.type()) {
            case "spawn" -> {
                node.put("location", locationMap(point));
                Map<String, Object> trigger = new LinkedHashMap<>();
                trigger.put("range", 8);
                trigger.put("min_players", 1);
                trigger.put("once", true);
                node.put("trigger", trigger);
                node.put("waves", new ArrayList<>());
            }
            case "room" -> {
                Object rooms = view.value("rooms");
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
            case "boss" -> {
                node.put("location", locationMap(point));
                node.put("source", "vanilla");
                node.put("boss_id", "");
                node.put("conditions", new ArrayList<>());
            }
            case "hint" -> {
                node.put("location", locationMap(point));
                node.put("text", "");
                node.put("channel", "chat");
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
            bound.clear(session);
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
        chatInput.cancel(player);
        try {
            player.closeInventory();
        } catch (Throwable ignored) {
            FailureRecorder.recordIgnoredFailure("EmakiDungeon", "EditorService.java#1", ignored);
        }
        messages.send(player, messageKey, Map.of());
        dispatcher.runEntity(plugin, player, () -> {
            if (origin != null) {
                try {
                    player.teleport(origin);
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

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        EditorSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        World world = Bukkit.getWorld(session.editWorldName());
        if (world == null) {
            dispatcher.runGlobal(plugin, () -> discardSession(session));
            return;
        }
        if (player.getWorld() != world) {
            Location spawn = world.getSpawnLocation();
            dispatcher.runEntity(plugin, player, () -> player.teleport(spawn), null);
        }
        messages.send(player, "editor.rejoined", Map.of("dungeon", session.dungeonId()));
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
        } catch (RuntimeException exception) {
            messages.warning("console.dungeon_config_invalid", Map.of(
                    "dungeon", dungeonId,
                    "error", String.valueOf(exception.getMessage())
            ));
            return;
        }
        messages.info("console.editor_template_saved", Map.of(
                "dungeon", dungeonId,
                "generation", String.valueOf(states.generationOf(dungeonId))
        ));
    }

    public EditorStateStore states() { return new EditorServiceDelegate(this).states(); }

    public void invalidateReserves(String dungeonId) { new EditorServiceDelegate(this).invalidateReserves(dungeonId); }

    public File backupRoot() { return new EditorServiceDelegate(this).backupRoot(); }
}
