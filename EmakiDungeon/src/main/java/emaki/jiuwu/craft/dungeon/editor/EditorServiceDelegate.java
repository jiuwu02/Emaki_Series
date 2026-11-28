package emaki.jiuwu.craft.dungeon.editor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
import emaki.jiuwu.craft.dungeon.world.WorldMounts;
import emaki.jiuwu.craft.dungeon.world.WorldPool;
import emaki.jiuwu.craft.corelib.debug.FailureRecorder;
import java.nio.file.Path;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.java.JavaPlugin;
import emaki.jiuwu.craft.corelib.log.LogTag;

final class EditorServiceDelegate {

    private final EditorService ownerRef;

    EditorServiceDelegate(EditorService ownerRef) {
        this.ownerRef = ownerRef;
    }

    static List<String> markTypes() {
        return EditorModules.types();
    }

    void bindPool(WorldPool pool) {
        ownerRef.pool = pool;
    }

    void bindConfigCache(Map<String, DungeonConfig> configCache) {
        ownerRef.configCache = configCache;
    }

    void bindTypeRegistry(DungeonTypeRegistry typeRegistry) {
        ownerRef.typeRegistry = typeRegistry;
    }

    void bindChallenge(ChallengeService challenge) {
        ownerRef.challenge = challenge;
    }

    void bindGui(EditorGui gui) {
        ownerRef.gui = gui;
    }

    void bindMarkers(EditorModuleMarkers markers) {
        ownerRef.markers = markers;
    }

    void refreshMarkers(EditorSession session) {
        EditorModuleMarkers bound = ownerRef.markers;
        if (bound != null && session != null) {
            bound.refresh(session);
        }
    }

    boolean hasSession(UUID playerId) {
        return playerId != null && ownerRef.sessions.containsKey(playerId);
    }

    EditorSession sessionOf(UUID playerId) {
        return playerId == null ? null : ownerRef.sessions.get(playerId);
    }

    List<EditorSession> allSessions() {
        return new ArrayList<>(ownerRef.sessions.values());
    }

    void finishSave(EditorSession session, boolean terrainChanged) {
        // 世界已完成保存且区域已回写, 此时重采才能读到玩家改过的真实方块
        recaptureRandomStructures(session);
        boolean changed = terrainChanged || session.dirty();
        int generation = changed ? ownerRef.states.bump(session.dungeonId())
                : ownerRef.states.generationOf(session.dungeonId());
        if (changed) {
            ownerRef.reloadConfigCache(session.dungeonId());
            ownerRef.invalidateReserves(session.dungeonId());
            session.dirty(false);
            session.stagedMarks().clear();
        }
        Player player = Bukkit.getPlayer(session.playerId());
        if (player != null) {
            ownerRef.messages.send(player, "editor.saved", Map.of(
                    "dungeon", session.dungeonId(),
                    "generation", String.valueOf(generation),
                    "terrain", String.valueOf(terrainChanged)
            ));
        }
        if (session.exitPending()) {
            ownerRef.discardSession(session);
        }
    }

    /**
     * 随机副本保存时按模板选区重采结构。
     *
     * <p>采集 GUI 的保存只写元数据, 方块快照来自玩家上一次保存模板的时刻。玩家框选后
     * 修改结构内容再退出编辑器, 模板会停留在旧内容上, 因此这里补一次重采。
     */
    private void recaptureRandomStructures(EditorSession session) {
        if (!emaki.jiuwu.craft.dungeon.type.DungeonTypeRegistry.RANDOM
                .equals(ownerRef.dungeonTypeOf(session.playerId()))) {
            return;
        }
        var service = ownerRef.randomService();
        if (service == null) {
            return;
        }
        Player player = Bukkit.getPlayer(session.playerId());
        int count = service.captures().recaptureStructures(player, session.dungeonId(),
                service.configFor(session.dungeonId()));
        if (count > 0 && player != null) {
            ownerRef.messages.send(player, "editor.structures_recaptured", Map.of(
                    "count", String.valueOf(count),
                    "dungeon", session.dungeonId()));
        }
    }

    void discardChanges(Player player) {
        EditorSession session = ownerRef.sessions.get(player.getUniqueId());
        if (session == null) {
            ownerRef.messages.send(player, "editor.no_session", Map.of());
            return;
        }
        ownerRef.discardSession(session, "editor.discarded");
    }

    File editDataDir(EditorSession session) {
        World world = session.editWorld();
        if (world != null) {
            return world.getWorldFolder();
        }
        Path fallback = WorldFolders
                .dataDir(ownerRef.worldContainer, session.editWorldName());
        for (Path candidate : WorldFolders
                .allDirs(ownerRef.worldContainer, session.editWorldName())) {
            if (Files.isDirectory(candidate)) {
                return candidate.toFile();
            }
        }
        return fallback.toFile();
    }

    void captureRegionSnapshot(EditorSession session) {
        File editDir = ownerRef.editDataDir(session);
        for (String sub : EditorService.REGION_SUBDIRS) {
            File dir = new File(editDir, sub);
            File[] files = dir.listFiles((folder, name) -> name.endsWith(".mca"));
            if (files == null) {
                continue;
            }
            for (File file : files) {
                session.regionSnapshot().put(sub + "/" + file.getName(), file.lastModified());
            }
        }
        for (String relative : EditorService.WORLD_DATA_FILES) {
            File dataFile = new File(editDir, relative);
            if (dataFile.isFile()) {
                session.regionSnapshot().put(relative, dataFile.lastModified());
            }
        }
    }

    boolean copyChangedRegions(EditorSession session) {
        File editDir = ownerRef.editDataDir(session);
        File templateDir = ownerRef.templates.templateDir(session.dungeonId()).orElse(null);
        if (templateDir == null) {
            return false;
        }
        boolean changed = false;
        File backupDir = new File(ownerRef.backupRoot(), session.dungeonId() + File.separator + "world-"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
        boolean backed = false;
        for (String sub : EditorService.REGION_SUBDIRS) {
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
                    backed |= backupTemplateFile(new File(targetDir, file.getName()),
                            sub + File.separator + file.getName(), backupDir);
                    Files.copy(file.toPath(), new File(targetDir, file.getName()).toPath(),
                            StandardCopyOption.REPLACE_EXISTING);
                    session.regionSnapshot().put(sub + "/" + file.getName(), file.lastModified());
                    changed = true;
                } catch (IOException exception) {
                    ownerRef.messages.warning(LogTag.EDITOR, "console.editor_region_writeback_failed", Map.of(
                            "file", String.valueOf(sub + "/" + file.getName()),
                            "error", String.valueOf(exception.getMessage())));
                }
            }
        }
        for (String relative : EditorService.WORLD_DATA_FILES) {
            File dataFile = new File(editDir, relative);
            if (!dataFile.isFile()) {
                continue;
            }
            long previous = session.regionSnapshot().getOrDefault(relative, -1L);
            if (dataFile.lastModified() == previous) {
                continue;
            }
            File target = new File(templateDir, relative);
            try {
                Files.createDirectories(target.toPath().getParent());
                backed |= backupTemplateFile(target, relative, backupDir);
                Files.copy(dataFile.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                session.regionSnapshot().put(relative, dataFile.lastModified());
                changed = true;
            } catch (IOException exception) {
                ownerRef.messages.warning(LogTag.EDITOR, "console.editor_region_writeback_failed", Map.of(
                        "file", relative,
                        "error", String.valueOf(exception.getMessage())));
            }
        }
        if (backed) {
            ownerRef.messages.info(LogTag.EDITOR, "console.editor_world_backup_created", Map.of(
                    "path", backupDir.getPath()));
        }
        return changed;
    }

    private boolean backupTemplateFile(File target, String relative, File backupDir) {
        if (!target.isFile()) {
            return false;
        }
        File slot = new File(backupDir, relative);
        try {
            Files.createDirectories(slot.getParentFile().toPath());
            Files.copy(target.toPath(), slot.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException exception) {
            ownerRef.messages.warning(LogTag.EDITOR, "console.editor_world_backup_failed", Map.of(
                    "file", relative,
                    "error", String.valueOf(exception.getMessage())));
            return false;
        }
    }

    EditorSession requireReady(Player player) {
        EditorSession session = ownerRef.sessions.get(player.getUniqueId());
        if (session == null) {
            ownerRef.messages.send(player, "editor.no_session", Map.of());
            return null;
        }
        if (!session.ready() || session.editWorld() == null) {
            ownerRef.messages.send(player, "editor.not_ready", Map.of());
            return null;
        }
        if (!player.getWorld().getName().equals(session.editWorldName())) {
            ownerRef.messages.send(player, "editor.not_in_edit_world", Map.of());
            return null;
        }
        return session;
    }

    FlowPoint lookPoint(Player player) {
        Block block = player.getTargetBlockExact(16);
        if (block == null) {
            return null;
        }
        return new FlowPoint(block.getX() + 0.5D, block.getY(), block.getZ() + 0.5D,
                player.getLocation().getYaw(), player.getLocation().getPitch());
    }

    String nextId(YamlTextDocument view, String section, String prefix) {
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

    Map<String, Object> locationMap(FlowPoint point) {
        Map<String, Object> location = new LinkedHashMap<>();
        location.put("x", point.x());
        location.put("y", point.y());
        location.put("z", point.z());
        location.put("yaw", point.yaw());
        location.put("pitch", point.pitch());
        return location;
    }

    long[] bounds(FlowPoint first, FlowPoint second) {
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

    String formatPoint(FlowPoint point) {
        return point.x() + "," + point.y() + "," + point.z();
    }

    void exit(Player player) {
        EditorSession session = ownerRef.sessions.get(player.getUniqueId());
        if (session == null) {
            ownerRef.messages.send(player, "editor.no_session", Map.of());
            return;
        }
        if (session.queue().pending() > 0 || session.inFlight() > 0) {
            ownerRef.messages.send(player, "editor.save_busy", Map.of());
            return;
        }
        ownerRef.save(player, true);
    }

    void closeAll() {
        for (EditorSession session : ownerRef.allSessions()) {
            ownerRef.sessions.remove(session.playerId(), session);
            TaskToken pump = session.pump();
            if (pump != null) {
                pump.cancel();
            }
            EditorModuleMarkers bound = ownerRef.markers;
            if (bound != null) {
                bound.clear(session);
            }
            Player player = Bukkit.getPlayer(session.playerId());
            Location origin = session.origin();
            if (player != null && origin != null && player.getWorld() == session.editWorld()) {
                try {
                    player.teleport(origin);
                    ownerRef.states.clearReturn(player.getUniqueId());
                } catch (Throwable ignored) {
                    FailureRecorder.recordIgnoredFailure("EmakiDungeon", "EditorService.java#3", ignored);
                }
            }
            if (player != null) {
                try {
                    player.closeInventory();
                } catch (Throwable ignored) {
                    FailureRecorder.recordIgnoredFailure("EmakiDungeon", "EditorService.java#4", ignored);
                }
            }
            World world = Bukkit.getWorld(session.editWorldName());
            if (world != null) {
                try {
                    WorldMounts.unload(world, false);
                } catch (Throwable ignored) {
                    FailureRecorder.recordIgnoredFailure("EmakiDungeon", "EditorService.java#5", ignored);
                }
            }
            try {
                ownerRef.disposer.deleteResidual(session.editWorldName());
            } catch (Throwable ignored) {
                FailureRecorder.recordIgnoredFailure("EmakiDungeon", "EditorService.java#6", ignored);
            }
            ownerRef.states.release(session.dungeonId());
        }
    }

    void sweepLeftoverWorlds() {
        ownerRef.dispatcher.runAsync(ownerRef.plugin, () -> {
            int removed = 0;
            for (Path root : WorldFolders
                    .sweepRoots(ownerRef.worldContainer)) {
                File[] children = root.toFile().listFiles(File::isDirectory);
                if (children == null) {
                    continue;
                }
                for (File child : children) {
                    if (!child.getName().startsWith("emd")) {
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
                ownerRef.messages.info(null, "console.editor_worlds_swept", Map.of("count", String.valueOf(removed)));
            }
        });
    }

    void preservePlayerDataWhenOffline(EditorSession session) {
        Player player = Bukkit.getPlayer(session.playerId());
        if (player != null) {
            return;
        }
        File editData = new File(ownerRef.editDataDir(session), "playerdata");
        File source = new File(editData, session.playerId() + ".dat");
        if (!source.isFile() || session.origin() == null || session.origin().getWorld() == null) {
            return;
        }
        File originDir = WorldFolders
                .dataDir(ownerRef.worldContainer, session.origin().getWorld().getName())
                .resolve("playerdata").toFile();
        try {
            Files.createDirectories(originDir.toPath());
            Files.move(source.toPath(), new File(originDir, source.getName()).toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            ownerRef.messages.warning(LogTag.EDITOR, "console.editor_playerdata_handoff_failed", Map.of(
                    "player", String.valueOf(session.playerId()),
                    "error", String.valueOf(exception.getMessage())));
        }
    }

    EditorStateStore states() {
        return ownerRef.states;
    }

    void invalidateReserves(String dungeonId) {
        WorldPool bound = ownerRef.pool;
        if (bound != null) {
            bound.invalidateAllReserves(dungeonId);
        }
    }

    File backupRoot() {
        return new File(ownerRef.dataFolder, "edit-backups");
    }

}
