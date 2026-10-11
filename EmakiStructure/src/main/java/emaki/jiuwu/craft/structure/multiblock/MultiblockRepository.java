package emaki.jiuwu.craft.structure.multiblock;

import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

public final class MultiblockRepository {

    public record Problem(String source, String reasonKey, Map<String, Object> placeholders) {
    }

    private final Path directory;
    private final Map<String, MultiblockDefinition> definitions = new ConcurrentHashMap<>();
    private final List<Problem> problems = new CopyOnWriteArrayList<>();

    public MultiblockRepository(Path directory) {
        this.directory = directory;
    }

    public Path directory() {
        return directory;
    }

    public void reload() {
        definitions.clear();
        problems.clear();
        if (!Files.isDirectory(directory)) {
            try {
                Files.createDirectories(directory);
            } catch (IOException exception) {
                problems.add(new Problem(directory.toString(),
                        "error.multiblock.scan_failed",
                        Map.of("file", directory.toString(),
                                "detail", String.valueOf(exception.getMessage()))));
                return;
            }
            return;
        }
        List<Path> files;
        try (Stream<Path> streamed = Files.list(directory)) {
            files = streamed
                    .filter(path -> path.getFileName().toString().endsWith(".yml"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        } catch (IOException exception) {
            problems.add(new Problem(directory.toString(),
                    "error.multiblock.scan_failed",
                    Map.of("file", directory.toString(),
                            "detail", String.valueOf(exception.getMessage()))));
            return;
        }
        for (Path file : files) {
            loadFile(file);
        }
    }

    private void loadFile(Path file) {
        String fileName = file.getFileName().toString();
        MultiblockLoader.LoadResult result;
        try {
            YamlSection section = YamlFiles.load(file.toFile());
            result = MultiblockLoader.load(section, fileName);
        } catch (RuntimeException exception) {
            problems.add(new Problem(fileName, "error.multiblock.parse_failed",
                    Map.of("file", fileName,
                            "detail", String.valueOf(exception.getMessage()))));
            return;
        }
        if (result.fileRejected()) {
            problems.add(new Problem(fileName, result.rejectKey(), result.rejectPlaceholders()));
            return;
        }
        for (MultiblockLoader.Issue issue : result.issues()) {
            Map<String, Object> placeholders = new HashMap<>(issue.placeholders());
            placeholders.put("index", issue.index());
            problems.add(new Problem(fileName, issue.reasonKey(), placeholders));
        }
        MultiblockDefinition definition = result.definition();
        MultiblockDefinition existing = definitions.putIfAbsent(definition.id(), definition);
        if (existing != null) {
            problems.add(new Problem(fileName, "error.multiblock.duplicate_id",
                    Map.of("file", fileName, "id", definition.id())));
        }
    }

    public List<MultiblockDefinition> all() {
        return List.copyOf(definitions.values());
    }

    public Optional<MultiblockDefinition> byId(String id) {
        return Optional.ofNullable(definitions.get(id));
    }

    public List<String> ids() {
        return definitions.keySet().stream().sorted().toList();
    }

    public List<Problem> drainProblems() {
        List<Problem> snapshot = new ArrayList<>(problems);
        problems.clear();
        return snapshot;
    }

    public boolean hasProblems() {
        return !problems.isEmpty();
    }
}
