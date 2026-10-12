package emaki.jiuwu.craft.structure.generate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ReplaceMarkerStore {

    private static final int MAX_LINES = 100_000;
    private static final int TRIMMED_LINES = 80_000;
    private static final String SUFFIX = ".marker";

    private final Path directory;
    private final Map<String, Set<String>> markers = new HashMap<>();
    private final Map<String, LinkedHashSet<String>> order = new HashMap<>();
    private final Object lock = new Object();

    public ReplaceMarkerStore(Path directory) {
        this.directory = directory;
        load();
    }

    public boolean firstProcess(String world, String key) {
        synchronized (lock) {
            Set<String> worldMarkers = markers.computeIfAbsent(world, ignored -> new HashSet<>());
            if (!worldMarkers.contains(key)) {
                worldMarkers.add(key);
                order.computeIfAbsent(world, ignored -> new LinkedHashSet<>()).add(key);
                appendLine(world, key);
                return true;
            }
            return false;
        }
    }

    private void load() {
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*" + SUFFIX)) {
            for (Path file : stream) {
                String world = file.getFileName().toString();
                world = world.substring(0, world.length() - SUFFIX.length());
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                Set<String> worldMarkers = markers.computeIfAbsent(world, ignored -> new HashSet<>());
                LinkedHashSet<String> worldOrder =
                        order.computeIfAbsent(world, ignored -> new LinkedHashSet<>());
                for (String line : lines) {
                    if (!line.isEmpty() && worldMarkers.add(line)) {
                        worldOrder.add(line);
                    }
                }
            }
        } catch (IOException exception) {
        }
    }

    private void appendLine(String world, String key) {
        LinkedHashSet<String> worldOrder = order.get(world);
        if (worldOrder != null && worldOrder.size() > MAX_LINES) {
            trim(world, worldOrder);
        }
        Path file = fileFor(world);
        try {
            Files.createDirectories(directory);
            Files.write(file, List.of(key), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException exception) {
        }
    }

    private void trim(String world, LinkedHashSet<String> worldOrder) {
        List<String> kept = new ArrayList<>(worldOrder);
        int drop = kept.size() - TRIMMED_LINES;
        if (drop <= 0) {
            return;
        }
        kept = kept.subList(drop, kept.size());
        Set<String> keptSet = new HashSet<>(kept);
        markers.put(world, keptSet);
        order.put(world, new LinkedHashSet<>(kept));
        Path file = fileFor(world);
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.write(temporary, kept, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
        }
    }

    private Path fileFor(String world) {
        return directory.resolve(world + SUFFIX);
    }
}
