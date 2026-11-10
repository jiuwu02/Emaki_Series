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

    void markPos(Player player, int which) {
        EditorSession session = ownerRef.requireReady(player);
        if (session == null) {
            return;
        }
        FlowPoint point = ownerRef.lookPoint(player);
        if (point == null) {
            ownerRef.messages.send(player, "editor.no_target", Map.of());
            return;
        }
        if (which == 1) {
            session.pos1(point);
        } else {
            session.pos2(point);
        }
        ownerRef.messages.send(player, "editor.pos_set", Map.of(
                "which", String.valueOf(which),
                "coord", ownerRef.formatPoint(point)
        ));
    }

    void finishSave(EditorSession session, boolean terrainChanged) {
        int generation = ownerRef.states.bump(session.dungeonId());
        ownerRef.reloadConfigCache(session.dungeonId());
        ownerRef.invalidateReserves(session.dungeonId());
        session.dirty(false);
        session.stagedMarks().clear();
        Player player = Bukkit.getPlayer(session.playerId());
        if (player != null) {
            ownerRef.messages.send(player, "editor.saved", Map.of(
                    "dungeon", session.dungeonId(),
                    "generation", String.valueOf(generation),
                    "terrain", String.valueOf(terrainChanged)
            ));
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
    }

    boolean copyChangedRegions(EditorSession session) {
        File editDir = ownerRef.editDataDir(session);
        File templateDir = ownerRef.templates.templateDir(session.dungeonId()).orElse(null);
        if (templateDir == null) {
            return false;
        }
        boolean changed = false;
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
                    Files.copy(file.toPath(), new File(targetDir, file.getName()).toPath(),
                            StandardCopyOption.REPLACE_EXISTING);
                    session.regionSnapshot().put(sub + "/" + file.getName(), file.lastModified());
                    changed = true;
                } catch (IOException exception) {
                    ownerRef.plugin.getLogger().warning("[editor] region write-back failed | file=" + sub + "/"
                            + file.getName() + " | error=" + exception.getMessage());
                }
            }
        }
        return changed;
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
        if (session.dirty()) {
            ownerRef.messages.send(player, "editor.exit_dirty", Map.of());
            return;
        }
        ownerRef.discardSession(session);
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
                    Bukkit.unloadWorld(world, false);
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
                    if (!child.getName().startsWith(EditorService.EDIT_WORLD_PREFIX)) {
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
                ownerRef.messages.info("console.editor_worlds_swept", Map.of("count", String.valueOf(removed)));
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
            ownerRef.plugin.getLogger().warning("[editor] playerdata handoff failed | player=" + session.playerId()
                    + " | error=" + exception.getMessage());
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
