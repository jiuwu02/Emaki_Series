package emaki.jiuwu.craft.corelib.web;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.bukkit.plugin.java.JavaPlugin;

public final class WebRuntimeLibraryService {

    private final Path librariesRoot;

    public WebRuntimeLibraryService(JavaPlugin plugin) {
        this.librariesRoot = plugin.getDataFolder().toPath().resolve("libraries");
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("root", librariesRoot.toString());
        root.put("exists", Files.isDirectory(librariesRoot));
        List<Map<String, Object>> libraries = new ArrayList<>();
        if (Files.isDirectory(librariesRoot)) {
            try (Stream<Path> stream = Files.walk(librariesRoot)) {
                stream.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".jar"))
                        .sorted(Comparator.comparing(Path::toString))
                        .forEach(path -> libraries.add(toEntry(path)));
            } catch (IOException exception) {
                root.put("error", exception.getMessage());
            }
        }
        root.put("count", libraries.size());
        root.put("libraries", libraries);
        return root;
    }

    private Map<String, Object> toEntry(Path path) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("path", librariesRoot.relativize(path).toString().replace('\\', '/'));
        entry.put("fileName", path.getFileName().toString());
        try {
            entry.put("size", Files.size(path));
            entry.put("lastModified", Files.getLastModifiedTime(path).toMillis());
        } catch (IOException exception) {
            entry.put("size", 0L);
            entry.put("lastModified", 0L);
            entry.put("error", exception.getMessage());
        }
        entry.put("status", "CACHED");
        return entry;
    }
}
