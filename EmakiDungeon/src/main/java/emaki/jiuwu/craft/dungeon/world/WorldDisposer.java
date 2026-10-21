package emaki.jiuwu.craft.dungeon.world;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;

public final class WorldDisposer {

    public enum Result {
        DISPOSED,
        HELD
    }

    private static final int MAX_ATTEMPTS = 3;
    private static final long[] RETRY_DELAY_MS = {1000L, 5000L, 15000L};

    private final Plugin plugin;
    private final ExecutionDispatcher dispatcher;
    private final File worldContainer;

    public WorldDisposer(Plugin plugin, ExecutionDispatcher dispatcher, File worldContainer) {
        this.plugin = plugin;
        this.dispatcher = dispatcher;
        this.worldContainer = worldContainer;
    }

    public CompletableFuture<Result> dispose(String worldName) {
        CompletableFuture<Result> future = new CompletableFuture<>();
        attempt(worldName, 0, future);
        return future;
    }

    public boolean deleteResidual(String worldName) {
        List<Path> dirs = WorldFolders.allDirs(worldContainer, worldName);
        for (Path candidate : dirs) {
            WorldCloner.deleteTree(candidate);
        }
        for (Path candidate : dirs) {
            if (Files.exists(candidate)) {
                return false;
            }
        }
        return true;
    }

    private void attempt(String worldName, int tries, CompletableFuture<Result> future) {
        dispatcher.runGlobal(plugin, () -> {
            boolean unloaded;
            try {
                World world = Bukkit.getWorld(worldName);
                unloaded = world == null || Bukkit.unloadWorld(world, false);
            } catch (Throwable throwable) {
                unloaded = false;
            }
            if (!unloaded) {
                retryOrFail(worldName, tries, future);
                return;
            }
            dispatcher.runAsync(plugin, () -> {
                List<Path> dirs = WorldFolders.allDirs(worldContainer, worldName);
                for (Path candidate : dirs) {
                    WorldCloner.deleteTree(candidate);
                }
                boolean remaining = false;
                for (Path candidate : dirs) {
                    if (Files.exists(candidate)) {
                        remaining = true;
                    }
                }
                if (remaining) {
                    retryOrFail(worldName, tries, future);
                } else {
                    future.complete(Result.DISPOSED);
                }
            });
        });
    }

    private void retryOrFail(String worldName, int tries, CompletableFuture<Result> future) {
        int next = tries + 1;
        if (next >= MAX_ATTEMPTS) {
            future.complete(Result.HELD);
            return;
        }
        dispatcher.runAsyncLater(plugin, () -> attempt(worldName, next, future),
                RETRY_DELAY_MS[tries], TimeUnit.MILLISECONDS);
    }
}
