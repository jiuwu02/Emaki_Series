package emaki.jiuwu.craft.dungeon.world;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.CompletableFuture;

import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;

public final class WorldCloner {

    private static final String PLAYER_DATA_DIR = "playerdata";

    private final Plugin plugin;
    private final ExecutionDispatcher dispatcher;
    private final File worldContainer;

    public WorldCloner(Plugin plugin, ExecutionDispatcher dispatcher, File worldContainer) {
        this.plugin = plugin;
        this.dispatcher = dispatcher;
        this.worldContainer = worldContainer;
    }

    public CompletableFuture<Void> cloneAsync(File templateDir, String worldName) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        Path target = new File(worldContainer, worldName).toPath();
        dispatcher.runAsync(plugin, () -> {
            try {
                copyTree(templateDir.toPath(), target);
                future.complete(null);
            } catch (Exception exception) {
                deleteTree(target);
                future.completeExceptionally(exception);
            }
        });
        return future;
    }

    public static void deleteTree(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
        }
    }

    private static void copyTree(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (PLAYER_DATA_DIR.equals(String.valueOf(dir.getFileName()))) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                Path destination = target.resolve(source.relativize(dir));
                Files.createDirectories(destination);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path destination = target.resolve(source.relativize(file));
                Files.createDirectories(destination.getParent());
                Files.copy(file, destination, StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
