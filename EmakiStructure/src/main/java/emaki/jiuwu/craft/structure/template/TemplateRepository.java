package emaki.jiuwu.craft.structure.template;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;

public final class TemplateRepository {

    public static final Pattern ID_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9_-]{0,63}$");

    private static final String NBT_SUFFIX = ".nbt";

    private final Path structuresDirectory;
    private final ImportLimits limits;
    private final StructureTemplateLoader loader;
    private final StructureTemplateCache cache;
    private final TemplateLoadReport report = new TemplateLoadReport();
    private final Map<String, Path> index = new LinkedHashMap<>();
    private final List<String> pendingCacheWarnings = new ArrayList<>();

    public TemplateRepository(Path structuresDirectory, ImportLimits limits, StructureTemplateCache cache) {
        this.structuresDirectory = Objects.requireNonNull(structuresDirectory, "structuresDirectory");
        this.limits = Objects.requireNonNull(limits, "limits");
        this.loader = new StructureTemplateLoader(limits);
        this.cache = Objects.requireNonNull(cache, "cache");
    }

    public static boolean isValidId(String candidate) {
        return candidate != null && ID_PATTERN.matcher(candidate).matches();
    }

    public synchronized void reload() {
        report.clear();
        cache.invalidateAll();
        index.clear();
        pendingCacheWarnings.clear();
        if (!Files.isDirectory(structuresDirectory)) {
            return;
        }
        List<Path> candidates;
        try (Stream<Path> listed = Files.list(structuresDirectory)) {
            candidates = listed
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(NBT_SUFFIX))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        } catch (IOException exception) {
            report.recordProblem(structuresDirectory.toString(), "error.template.scan_failed",
                    Map.<String, Object>of("file", structuresDirectory.toString(), "detail", describe(exception)));
            return;
        }
        for (Path candidate : candidates) {
            String fileName = candidate.getFileName().toString();
            String id = fileName.substring(0, fileName.length() - NBT_SUFFIX.length());
            if (!isValidId(id)) {
                report.recordProblem(fileName, "error.template.invalid_name",
                        Map.<String, Object>of("file", fileName));
                continue;
            }
            index.put(id, candidate);
        }
    }

    public synchronized Optional<StructureTemplate> template(String id) {
        if (!isValidId(id)) {
            return Optional.empty();
        }
        Optional<StructureTemplate> cached = cache.get(id);
        if (cached.isPresent()) {
            return cached;
        }
        Path path = index.get(id);
        if (path == null) {
            return Optional.empty();
        }
        String fileName = path.getFileName().toString();
        byte[] bytes = readTemplateBytes(fileName, path);
        if (bytes == null) {
            return Optional.empty();
        }
        EmakiResult<StructureTemplate> loaded = loader.load(id, bytes);
        Optional<StructureTemplate> ready = loaded.optionalValue();
        if (ready.isEmpty()) {
            report.recordProblem(fileName, loaded.reasonKey(), loaded.reasonPlaceholders());
            return Optional.empty();
        }
        return ready.map(present -> {
            if (cache.insert(id, present).shouldWarn()) {
                pendingCacheWarnings.add(id);
            }
            return present;
        });
    }

    public synchronized Set<String> templateIds() {
        return Set.copyOf(index.keySet());
    }

    public synchronized List<String> drainCacheWarnings() {
        List<String> drained = List.copyOf(pendingCacheWarnings);
        pendingCacheWarnings.clear();
        return drained;
    }

    public TemplateLoadReport report() {
        return report;
    }

    private byte[] readTemplateBytes(String fileName, Path path) {
        try {
            long fileBytes = Files.size(path);
            if (fileBytes > limits.maxFileBytes()) {
                report.recordProblem(fileName, "error.template.file_too_large",
                        Map.<String, Object>of("file", fileName, "limit", limits.maxFileBytes(),
                                "actual", fileBytes));
                return null;
            }
            return Files.readAllBytes(path);
        } catch (IOException exception) {
            report.recordProblem(fileName, "error.template.read_failed",
                    Map.<String, Object>of("file", fileName, "detail", describe(exception)));
            return null;
        }
    }

    private static String describe(IOException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}
