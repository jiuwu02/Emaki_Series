package emaki.jiuwu.craft.corelib.web.insight;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.web.WebConsoleConfig;
import emaki.jiuwu.craft.corelib.web.WebConsoleRegistry;
import emaki.jiuwu.craft.corelib.web.WebConsoleYamlRegistrar;
import emaki.jiuwu.craft.corelib.web.WebPathSecurity;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;

public final class WebInsightReferenceService {

    private static final int MAX_RESULTS = 200;

    private final JavaPlugin plugin;
    private final WebConsoleConfig config;

    public WebInsightReferenceService(JavaPlugin plugin, WebConsoleConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public List<Map<String, Object>> references(String idType, String id) throws IOException {
        ReferenceTarget target = ReferenceTarget.of(idType, id);
        if (!target.valid()) {
            return List.of();
        }
        List<WebInsightReferenceResult> results = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        WebConsoleYamlRegistrar.scanAll();
        for (WebConsoleRegistry.WebRegisteredFileEntry entry : WebConsoleRegistry.registeredFileEntries()) {
            if (results.size() >= MAX_RESULTS) {
                break;
            }
            scanEntry(entry, target, results, seen);
        }
        return results.stream().limit(MAX_RESULTS).map(WebInsightReferenceResult::toMap).toList();
    }

    private void scanEntry(WebConsoleRegistry.WebRegisteredFileEntry entry, ReferenceTarget target, List<WebInsightReferenceResult> results, Set<String> seen) throws IOException {
        if (isGlobPath(entry.relativePath())) {
            for (String childPath : globChildren(entry)) {
                if (results.size() >= MAX_RESULTS) {
                    return;
                }
                scanFile(entry, childPath, target, results, seen);
            }
            return;
        }
        scanFile(entry, entry.relativePath(), target, results, seen);
    }

    private List<String> globChildren(WebConsoleRegistry.WebRegisteredFileEntry entry) throws IOException {
        String baseDir = extractBaseDir(entry.relativePath());
        String extension = extractExtension(entry.relativePath());
        Path root = moduleRoot(entry.moduleId());
        Path dir = WebPathSecurity.resolveInside(root, baseDir);
        if (dir == null || !Files.isDirectory(dir)) {
            return List.of();
        }
        List<String> paths = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(dir)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> extension.isBlank() || path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(extension.toLowerCase(Locale.ROOT)))
                    .sorted(Comparator.comparing(Path::toString))
                    .forEach(path -> paths.add(root.relativize(path).toString().replace('\\', '/')));
        }
        return paths;
    }

    private void scanFile(WebConsoleRegistry.WebRegisteredFileEntry entry, String path, ReferenceTarget target, List<WebInsightReferenceResult> results, Set<String> seen) throws IOException {
        Path root = moduleRoot(entry.moduleId());
        Path file = WebPathSecurity.resolveInside(root, path);
        if (file == null || !Files.isRegularFile(file) || !isYamlFile(file)) {
            return;
        }
        long maxBytes = Math.max(1, config.configBrowser().maxFileSizeKb()) * 1024L;
        if (Files.size(file) > maxBytes) {
            return;
        }
        String content = Files.readString(file, StandardCharsets.UTF_8);
        try {
            YamlSection yaml = YamlFiles.load(content);
            scanValue(entry, path, "", yaml.asMap(), target, results, seen);
        } catch (Exception ignored) {
            // 引用追踪只认可可解析 YAML 中的语义字段；解析失败时不退化为全文搜索，避免误判普通文本。
        }
    }

    private void scanValue(WebConsoleRegistry.WebRegisteredFileEntry entry, String filePath, String keyPath, Object value, ReferenceTarget target, List<WebInsightReferenceResult> results, Set<String> seen) {
        if (results.size() >= MAX_RESULTS) {
            return;
        }
        if (value instanceof YamlSection section) {
            scanValue(entry, filePath, keyPath, section.asMap(), target, results, seen);
            return;
        }
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> child : map.entrySet()) {
                if (child.getKey() == null) {
                    continue;
                }
                String childKey = String.valueOf(child.getKey());
                String childPath = Texts.isBlank(keyPath) ? childKey : keyPath + "." + childKey;
                matchMapKey(entry, filePath, childPath, childKey, target, results, seen);
                scanValue(entry, filePath, childPath, child.getValue(), target, results, seen);
                if (results.size() >= MAX_RESULTS) {
                    return;
                }
            }
            return;
        }
        if (value instanceof List<?> list) {
            for (int index = 0; index < list.size(); index++) {
                scanValue(entry, filePath, keyPath + "[" + index + "]", list.get(index), target, results, seen);
                if (results.size() >= MAX_RESULTS) {
                    return;
                }
            }
            return;
        }
        String text = String.valueOf(value == null ? "" : value);
        matchScalarValue(entry, filePath, keyPath, text, target, results, seen);
    }

    private void matchMapKey(WebConsoleRegistry.WebRegisteredFileEntry entry, String filePath, String keyPath, String key, ReferenceTarget target, List<WebInsightReferenceResult> results, Set<String> seen) {
        if ("id".equals(normalize(keyPath))) {
            return;
        }
        String candidateType = mapKeyReferenceType(keyPath);
        if (!target.idType().equals(candidateType)) {
            return;
        }
        String candidateId = normalizeReferenceId(candidateType, key);
        if (!target.id().equals(candidateId)) {
            return;
        }
        add(results, seen, new WebInsightReferenceResult(
                entry.moduleId(),
                filePath,
                entry.kind(),
                keyPath,
                candidateType,
                candidateId,
                key,
                edgeType(candidateType, keyPath),
                keyPath + ": " + key
        ));
    }

    private void matchScalarValue(WebConsoleRegistry.WebRegisteredFileEntry entry, String filePath, String keyPath, String value, ReferenceTarget target, List<WebInsightReferenceResult> results, Set<String> seen) {
        if ("id".equals(normalize(keyPath))) {
            return;
        }
        String candidateType = scalarReferenceType(keyPath, value);
        if (!target.idType().equals(candidateType)) {
            return;
        }
        String candidateId = normalizeReferenceId(candidateType, value);
        if (!target.id().equals(candidateId)) {
            return;
        }
        add(results, seen, new WebInsightReferenceResult(
                entry.moduleId(),
                filePath,
                entry.kind(),
                keyPath,
                candidateType,
                candidateId,
                value,
                edgeType(candidateType, keyPath),
                keyPath + ": " + value
        ));
    }

    private void add(List<WebInsightReferenceResult> results, Set<String> seen, WebInsightReferenceResult result) {
        String key = result.moduleId() + "|" + result.path() + "|" + result.keyPath() + "|" + result.idType() + "|" + result.id() + "|" + result.referenceValue();
        if (results.size() < MAX_RESULTS && seen.add(key)) {
            results.add(result);
        }
    }

    private String mapKeyReferenceType(String keyPath) {
        String path = normalize(keyPath);
        if (path.contains("ea_attributes") || path.contains("attributes") || path.contains("stats")) {
            return "attribute";
        }
        if (path.contains("es_skills") || path.contains("skills")) {
            return "skill";
        }
        if (path.contains("gems")) {
            return "gem";
        }
        if (path.contains("level_type") || path.contains("types")) {
            return "level_type";
        }
        return "";
    }

    private String scalarReferenceType(String keyPath, String value) {
        String path = normalize(keyPath);
        String normalizedValue = normalize(value);
        if (path.contains("item_source") || path.contains("item_sources")) {
            if (normalizedValue.startsWith("emakiitem-") || normalizedValue.startsWith("ei-")) {
                return "emaki_item";
            }
            return "item_source";
        }
        if (normalizedValue.startsWith("emakiitem-") || normalizedValue.startsWith("ei-")) {
            return "emaki_item";
        }
        if (path.contains("flat_attributes")
                || path.contains("percent_attributes")
                || path.contains("chance_attributes")
                || path.contains("multiplier_attributes")
                || path.contains("resistance_attributes")
                || path.contains("ea_attributes")
                || path.contains("attributes")
                || path.contains("stats")) {
            return "attribute";
        }
        if (path.contains("es_skills") || path.contains("skills")) {
            return "skill";
        }
        if (path.contains("gems")) {
            return "gem";
        }
        if (path.contains("level_type") || path.contains("types")) {
            return "level_type";
        }
        if (path.contains("forge_recipe")) {
            return "forge_recipe";
        }
        if (path.contains("strengthen_recipe")) {
            return "strengthen_recipe";
        }
        return "";
    }

    private String edgeType(String idType, String keyPath) {
        String path = normalize(keyPath);
        if ("attribute".equals(idType)) {
            return "adds_attribute";
        }
        if ("emaki_item".equals(idType) || "item_source".equals(idType)) {
            return path.contains("result") || path.contains("output") ? "produces_item" : "requires_item";
        }
        if ("skill".equals(idType)) {
            return "grants_skill";
        }
        return "uses";
    }

    private String normalizeReferenceId(String idType, String value) {
        String normalized = normalize(value);
        if ("emaki_item".equals(idType)) {
            if (normalized.startsWith("emakiitem-")) {
                return normalized.substring("emakiitem-".length());
            }
            if (normalized.startsWith("ei-")) {
                return normalized.substring("ei-".length());
            }
        }
        return normalized;
    }

    private boolean isYamlFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".yml") || name.endsWith(".yaml");
    }

    private Path moduleRoot(String moduleId) {
        return plugin.getDataFolder().toPath().getParent().resolve(moduleId).toAbsolutePath().normalize();
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }

    private boolean isGlobPath(String path) {
        return path != null && (path.contains("*") || path.contains("?"));
    }

    private String extractBaseDir(String globPath) {
        int starIndex = globPath.indexOf('*');
        if (starIndex <= 0) return globPath;
        String before = globPath.substring(0, starIndex);
        if (before.endsWith("/")) before = before.substring(0, before.length() - 1);
        return before;
    }

    private String extractExtension(String globPath) {
        int dotIndex = globPath.lastIndexOf("*.");
        if (dotIndex < 0) return "";
        return globPath.substring(dotIndex + 1);
    }

    private record ReferenceTarget(String idType, String id) {

        static ReferenceTarget of(String idType, String id) {
            String normalizedType = idType == null ? "" : idType.toLowerCase(Locale.ROOT).trim();
            String normalizedId = normalizeId(normalizedType, id);
            return new ReferenceTarget(normalizedType, normalizedId);
        }

        boolean valid() {
            return !idType.isBlank() && !id.isBlank();
        }

        private static String normalizeId(String idType, String value) {
            String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
            if ("emaki_item".equals(idType)) {
                if (normalized.startsWith("emakiitem-")) {
                    return normalized.substring("emakiitem-".length());
                }
                if (normalized.startsWith("ei-")) {
                    return normalized.substring("ei-".length());
                }
            }
            return normalized;
        }
    }
}
