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
import org.bukkit.GameRule;
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
import emaki.jiuwu.craft.dungeon.flow.FlowPoint;
import emaki.jiuwu.craft.dungeon.instance.WorldCommandQueue;
import emaki.jiuwu.craft.dungeon.world.TemplateRepository;
import emaki.jiuwu.craft.dungeon.world.WorldCloner;
import emaki.jiuwu.craft.dungeon.world.WorldDisposer;

public final class EditorService implements Listener {

    public static final String EDIT_WORLD_PREFIX = "emd_edit_";

    private static final long MAX_FILL_BLOCKS = 1_000_000L;
    private static final List<String> REGION_SUBDIRS = List.of("region", "entities");

    private static final List<String> MARK_TYPES = List.of(
            "spawn", "room", "gate", "option", "trigger", "chest"
    );
    private static final Map<String, String> MARK_SECTIONS = Map.of(
            "spawn", "spawn_points",
            "room", "rooms",
            "gate", "gates",
            "option", "options",
            "trigger", "trigger_zones",
            "chest", "rewards"
    );
    private static final Map<String, String> MARK_PREFIXES = Map.of(
            "spawn", "spawn",
            "room", "room",
            "gate", "gate",
            "option", "option",
            "trigger", "trigger",
            "chest", "chest"
    );

    public static List<String> markTypes() {
        return MARK_TYPES;
    }

    private final org.bukkit.plugin.java.JavaPlugin plugin;
    private final ExecutionDispatcher dispatcher;
    private final MessageService messages;
    private final ChatInputService chatInput;
    private final WorldCloner cloner;
    private final WorldDisposer disposer;
    private final TemplateRepository templates;
    private final EditorStateStore states;
    private final File worldContainer;
    private final File dataFolder;
    private final Map<UUID, EditorSession> sessions = new ConcurrentHashMap<>();
    private volatile emaki.jiuwu.craft.dungeon.world.WorldPool pool;
    private volatile Map<String, emaki.jiuwu.craft.dungeon.config.DungeonConfig> configCache;
    private volatile emaki.jiuwu.craft.dungeon.challenge.ChallengeService challenge;
    private volatile EditorGui gui;

    public EditorService(org.bukkit.plugin.java.JavaPlugin plugin,
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

    public void bindPool(emaki.jiuwu.craft.dungeon.world.WorldPool pool) {
        this.pool = pool;
    }

    public void bindConfigCache(Map<String, emaki.jiuwu.craft.dungeon.config.DungeonConfig> configCache) {
        this.configCache = configCache;
    }

    public void bindChallenge(emaki.jiuwu.craft.dungeon.challenge.ChallengeService challenge) {
        this.challenge = challenge;
    }

    public void bindGui(EditorGui gui) {
        this.gui = gui;
    }

    public boolean hasSession(UUID playerId) {
        return playerId != null && sessions.containsKey(playerId);
    }

    public EditorSession sessionOf(UUID playerId) {
        return playerId == null ? null : sessions.get(playerId);
    }

    public List<EditorSession> allSessions() {
        return new ArrayList<>(sessions.values());
    }

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
            for (java.nio.file.Path candidate : emaki.jiuwu.craft.dungeon.world.WorldFolders
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
                for (java.nio.file.Path candidate : emaki.jiuwu.craft.dungeon.world.WorldFolders
                        .allDirs(worldContainer, session.editWorldName())) {
                    WorldCloner.deleteTree(candidate);
                }
            });
            states.release(session.dungeonId());
            return;
        }
        for (java.nio.file.Path stale : emaki.jiuwu.craft.dungeon.world.WorldFolders
                .foreignDirs(worldContainer, session.editWorldName())) {
            WorldCloner.deleteTree(stale);
        }
        World world;
        try {
            world = Bukkit.createWorld(new WorldCreator(session.editWorldName()));
            if (world == null) {
                throw new IllegalStateException("createWorld returned null");
            }
            world.setDifficulty(Difficulty.PEACEFUL);
            world.setGameRule(GameRule.DO_MOB_SPAWNING, Boolean.FALSE);
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
            if (gui != null) {
                gui.openMain(player);
            }
        }, () -> discardSession(session));
    }

    public void markPos(Player player, int which) {
        EditorSession session = requireReady(player);
        if (session == null) {
            return;
        }
        FlowPoint point = lookPoint(player);
        if (point == null) {
            messages.send(player, "editor.no_target", Map.of());
            return;
        }
        if (which == 1) {
            session.pos1(point);
        } else {
            session.pos2(point);
        }
        messages.send(player, "editor.pos_set", Map.of(
                "which", String.valueOf(which),
                "coord", formatPoint(point)
        ));
    }

    public void mark(Player player, String rawType, String rawId) {
        EditorSession session = requireReady(player);
        if (session == null) {
            return;
        }
        String type = Texts.lower(rawType);
        if (!MARK_TYPES.contains(type)) {
            messages.send(player, "editor.mark_unknown_type", Map.of("type", String.valueOf(rawType)));
            return;
        }
        boolean region = "room".equals(type) || "gate".equals(type) || "trigger".equals(type);
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
        FlowPoint point = region ? null : lookPoint(player);
        if (!region && point == null) {
            messages.send(player, "editor.no_target", Map.of());
            return;
        }
        String id = Texts.isBlank(rawId) ? null : Texts.normalizeId(rawId);
        EditorDocument flowDoc = session.flowDoc();
        EditorDocument.SaveResult result;
        String[] assigned = new String[1];
        try {
            synchronized (flowDoc) {
                YamlTextDocument view = flowDoc.view();
                String section = MARK_SECTIONS.get(type);
                String prefix = MARK_PREFIXES.get(type);
                String nodeId = id == null ? nextId(view, section, prefix) : id;
                assigned[0] = nodeId;
                if (view.value(section, nodeId) != null) {
                    messages.send(player, "editor.mark_dup", Map.of("id", nodeId));
                    return;
                }
                Map<String, Object> node = markNode(type, nodeId, point, first, second, view);
                result = flowDoc.mutate(document -> document.set(node, section, nodeId));
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
        String coord = region ? formatRegion(first, second) : formatPoint(point);
        String mark = type + ":" + assigned[0] + " @ " + coord;
        session.stagedMarks().add(mark);
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

    private void finishSave(EditorSession session, boolean terrainChanged) {
        int generation = states.bump(session.dungeonId());
        reloadConfigCache(session.dungeonId());
        invalidateReserves(session.dungeonId());
        session.dirty(false);
        session.stagedMarks().clear();
        Player player = Bukkit.getPlayer(session.playerId());
        if (player != null) {
            messages.send(player, "editor.saved", Map.of(
                    "dungeon", session.dungeonId(),
                    "generation", String.valueOf(generation),
                    "terrain", String.valueOf(terrainChanged)
            ));
        }
    }

    public void discardChanges(Player player) {
        EditorSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            messages.send(player, "editor.no_session", Map.of());
            return;
        }
        discardSession(session, "editor.discarded");
    }

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
        org.bukkit.block.data.BlockData data;
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
            java.util.List<emaki.jiuwu.craft.dungeon.instance.WorldCommand> batch = session.queue().drain();
            if (batch.isEmpty()) {
                return;
            }
            session.inFlightAdd(batch.size());
            for (emaki.jiuwu.craft.dungeon.instance.WorldCommand command : batch) {
                dispatcher.runAtLocation(plugin, anchor, () -> {
                    try {
                        command.execute();
                    } catch (Throwable throwable) {
                        command.fail(throwable);
                        plugin.getLogger().warning("[editor] fill command failed | error=" + throwable);
                    } finally {
                        session.inFlightAdd(-1);
                    }
                });
            }
        }, 1L, 1L));
    }

    private File editDataDir(EditorSession session) {
        World world = session.editWorld();
        if (world != null) {
            return world.getWorldFolder();
        }
        java.nio.file.Path fallback = emaki.jiuwu.craft.dungeon.world.WorldFolders
                .dataDir(worldContainer, session.editWorldName());
        for (java.nio.file.Path candidate : emaki.jiuwu.craft.dungeon.world.WorldFolders
                .allDirs(worldContainer, session.editWorldName())) {
            if (java.nio.file.Files.isDirectory(candidate)) {
                return candidate.toFile();
            }
        }
        return fallback.toFile();
    }

    private void captureRegionSnapshot(EditorSession session) {
        File editDir = editDataDir(session);
        for (String sub : REGION_SUBDIRS) {
            File dir = new File(editDir, sub);
            File[] files = dir.listFiles((folder, name) -> name.endsWith(".mca"));
            if (files == null) {
                continue;
            }
            for (File file : files) {
                session.regionSnapshot().put(sub + "/" + file.getName(), file.lastModified());
            }
        }
    }

    private boolean copyChangedRegions(EditorSession session) {
        File editDir = editDataDir(session);
        File templateDir = templates.templateDir(session.dungeonId()).orElse(null);
        if (templateDir == null) {
            return false;
        }
        boolean changed = false;
        for (String sub : REGION_SUBDIRS) {
            File dir = new File(editDir, sub);
            File[] files = dir.listFiles((folder, name) -> name.endsWith(".mca"));
            if (files == null) {
                continue;
            }
            for (File file : files) {
                long previous = session.regionSnapshot().getOrDefault(sub + "/" + file.getName(), -1L);
                if (file.lastModified() == previous) {
                    continue;
                }
                File targetDir = new File(templateDir, sub);
                try {
                    Files.createDirectories(targetDir.toPath());
                    Files.copy(file.toPath(), new File(targetDir, file.getName()).toPath(),
                            StandardCopyOption.REPLACE_EXISTING);
                    session.regionSnapshot().put(sub + "/" + file.getName(), file.lastModified());
                    changed = true;
                } catch (IOException exception) {
                    plugin.getLogger().warning("[editor] region write-back failed | file=" + sub + "/"
                            + file.getName() + " | error=" + exception.getMessage());
                }
            }
        }
        return changed;
    }

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

    private EditorSession requireReady(Player player) {
        EditorSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            messages.send(player, "editor.no_session", Map.of());
            return null;
        }
        if (!session.ready() || session.editWorld() == null) {
            messages.send(player, "editor.not_ready", Map.of());
            return null;
        }
        if (!player.getWorld().getName().equals(session.editWorldName())) {
            messages.send(player, "editor.not_in_edit_world", Map.of());
            return null;
        }
        return session;
    }

    private FlowPoint lookPoint(Player player) {
        org.bukkit.block.Block block = player.getTargetBlockExact(16);
        if (block == null) {
            return null;
        }
        return new FlowPoint(block.getX() + 0.5D, block.getY(), block.getZ() + 0.5D,
                player.getLocation().getYaw(), player.getLocation().getPitch());
    }

    private Map<String, Object> markNode(String type, String id, FlowPoint point, FlowPoint first, FlowPoint second,
            YamlTextDocument view) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", id);
        node.put("enabled", true);
        switch (type) {
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
            default -> {
            }
        }
        return node;
    }

    private String nextId(YamlTextDocument view, String section, String prefix) {
        Object existing = view.value(section);
        int size = existing instanceof Map<?, ?> map ? map.size() : 0;
        for (int index = size + 1; index < size + 512; index++) {
            String candidate = prefix + index;
            if (view.value(section, candidate) == null) {
                return candidate;
            }
        }
        return prefix + System.currentTimeMillis();
    }

    private Map<String, Object> locationMap(FlowPoint point) {
        Map<String, Object> location = new LinkedHashMap<>();
        location.put("x", point.x());
        location.put("y", point.y());
        location.put("z", point.z());
        location.put("yaw", point.yaw());
        location.put("pitch", point.pitch());
        return location;
    }

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

    private long[] bounds(FlowPoint first, FlowPoint second) {
        long ax = (long) Math.floor(first.x());
        long ay = (long) Math.floor(first.y());
        long az = (long) Math.floor(first.z());
        long bx = (long) Math.floor(second.x());
        long by = (long) Math.floor(second.y());
        long bz = (long) Math.floor(second.z());
        return new long[] {
                Math.min(ax, bx), Math.min(ay, by), Math.min(az, bz),
                Math.max(ax, bx), Math.max(ay, by), Math.max(az, bz)
        };
    }

    private String formatPoint(FlowPoint point) {
        return point.x() + "," + point.y() + "," + point.z();
    }

    private String formatRegion(FlowPoint first, FlowPoint second) {
        long[] bounds = bounds(first, second);
        return bounds[0] + "," + bounds[1] + "," + bounds[2] + " -> "
                + bounds[3] + "," + bounds[4] + "," + bounds[5];
    }

    public void exit(Player player) {
        EditorSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            messages.send(player, "editor.no_session", Map.of());
            return;
        }
        if (session.dirty()) {
            messages.send(player, "editor.exit_dirty", Map.of());
            return;
        }
        discardSession(session);
    }

    public void discardSession(EditorSession session) {
        discardSession(session, "editor.exit_done");
    }

    private void discardSession(EditorSession session, String messageKey) {
        if (session == null || !sessions.remove(session.playerId(), session)) {
            return;
        }
        TaskToken pump = session.pump();
        if (pump != null) {
            pump.cancel();
        }
        session.configDoc().discard();
        session.flowDoc().discard();
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
        }
        messages.send(player, messageKey, Map.of());
        dispatcher.runEntity(plugin, player, () -> {
            if (origin != null) {
                try {
                    player.teleport(origin);
                } catch (Throwable ignored) {
                }
            }
            unload.run();
        }, unload);
    }

    public void closeAll() {
        for (EditorSession session : allSessions()) {
            sessions.remove(session.playerId(), session);
            TaskToken pump = session.pump();
            if (pump != null) {
                pump.cancel();
            }
            Player player = Bukkit.getPlayer(session.playerId());
            Location origin = session.origin();
            if (player != null && origin != null && player.getWorld() == session.editWorld()) {
                try {
                    player.teleport(origin);
                } catch (Throwable ignored) {
                }
            }
            if (player != null) {
                try {
                    player.closeInventory();
                } catch (Throwable ignored) {
                }
            }
            World world = Bukkit.getWorld(session.editWorldName());
            if (world != null) {
                try {
                    Bukkit.unloadWorld(world, false);
                } catch (Throwable ignored) {
                }
            }
            states.release(session.dungeonId());
        }
    }

    public void sweepLeftoverWorlds() {
        dispatcher.runAsync(plugin, () -> {
            int removed = 0;
            for (java.nio.file.Path root : emaki.jiuwu.craft.dungeon.world.WorldFolders
                    .sweepRoots(worldContainer)) {
                File[] children = root.toFile().listFiles(File::isDirectory);
                if (children == null) {
                    continue;
                }
                for (File child : children) {
                    if (!child.getName().startsWith(EDIT_WORLD_PREFIX)) {
                        continue;
                    }
                    if (Bukkit.getWorld(child.getName()) != null) {
                        continue;
                    }
                    WorldCloner.deleteTree(child.toPath());
                    removed++;
                }
            }
            if (removed > 0) {
                messages.info("console.editor_worlds_swept", Map.of("count", String.valueOf(removed)));
            }
        });
    }

    private void preservePlayerDataWhenOffline(EditorSession session) {
        Player player = Bukkit.getPlayer(session.playerId());
        if (player != null) {
            return;
        }
        File editData = new File(editDataDir(session), "playerdata");
        File source = new File(editData, session.playerId() + ".dat");
        if (!source.isFile() || session.origin() == null || session.origin().getWorld() == null) {
            return;
        }
        File originDir = emaki.jiuwu.craft.dungeon.world.WorldFolders
                .dataDir(worldContainer, session.origin().getWorld().getName())
                .resolve("playerdata").toFile();
        try {
            Files.createDirectories(originDir.toPath());
            Files.move(source.toPath(), new File(originDir, source.getName()).toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            plugin.getLogger().warning("[editor] playerdata handoff failed | player=" + session.playerId()
                    + " | error=" + exception.getMessage());
        }
    }

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
            configCache.put(dungeonId, templates.loadConfig(dungeonId));
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

    public EditorStateStore states() {
        return states;
    }

    public void invalidateReserves(String dungeonId) {
        emaki.jiuwu.craft.dungeon.world.WorldPool bound = pool;
        if (bound != null) {
            bound.invalidateAllReserves(dungeonId);
        }
    }

    public File backupRoot() {
        return new File(dataFolder, "edit-backups");
    }
}
