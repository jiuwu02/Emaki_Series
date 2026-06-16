package emaki.jiuwu.craft.corelib.web.insight;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
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

public final class WebInsightSearchService {

    private static final int MAX_RESULTS = 200;
    private static final Set<String> REFERENCE_KEYS = Set.of(
            "item_sources",
            "item_source",
            "ea_attributes",
            "attributes",
            "stats",
            "skills",
            "es_skills",
            "gems",
            "types",
            "level_type",
            "gui",
            "actions"
    );

    private final JavaPlugin plugin;
    private final WebConsoleConfig config;

    public WebInsightSearchService(JavaPlugin plugin, WebConsoleConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public List<Map<String, Object>> search(String query) throws IOException {
        String normalizedQuery = normalize(query);
        if (normalizedQuery.isBlank()) {
            return List.of();
        }
        List<WebInsightSearchResult> results = new ArrayList<>();
        WebConsoleYamlRegistrar.scanAll();
        for (WebConsoleRegistry.WebRegisteredFileEntry entry : WebConsoleRegistry.registeredFileEntries()) {
            scanEntry(entry, normalizedQuery, results);
        }
        return results.stream()
                .sorted(resultOrder(normalizedQuery))
                .limit(MAX_RESULTS)
                .map(WebInsightSearchResult::toMap)
                .toList();
    }

    private void scanEntry(WebConsoleRegistry.WebRegisteredFileEntry entry, String query, List<WebInsightSearchResult> results) throws IOException {
        if (isGlobPath(entry.relativePath())) {
            for (String childPath : globChildren(entry)) {
                scanFile(entry, childPath, query, results);
            }
            return;
        }

        scanFile(entry, entry.relativePath(), query, results);
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

    private void scanFile(WebConsoleRegistry.WebRegisteredFileEntry entry, String path, String query, List<WebInsightSearchResult> results) throws IOException {
        Path root = moduleRoot(entry.moduleId());
        Path target = WebPathSecurity.resolveInside(root, path);
        if (target == null || !Files.isRegularFile(target) || !isYamlFile(target)) {
            return;
        }
        long maxBytes = Math.max(1, config.configBrowser().maxFileSizeKb()) * 1024L;
        if (Files.size(target) > maxBytes) {
            return;
        }
        if (contains(path, query) || contains(entry.title(), query)) {
            add(results, new WebInsightSearchResult(entry.moduleId(), path, entry.kind(), "", "file", inferIdType(entry.moduleId(), path), path));
        }
        String content = Files.readString(target, StandardCharsets.UTF_8);
        scanCommentLines(entry, path, content, query, results);
        try {
            YamlSection yaml = YamlFiles.load(content);
            scanValue(entry, path, "", yaml.asMap(), query, results);
        } catch (Exception ignored) {
            if (contains(content, query)) {
                add(results, new WebInsightSearchResult(entry.moduleId(), path, entry.kind(), "", "text", "", snippet(content, query)));
            }
        }
    }

    private void scanCommentLines(WebConsoleRegistry.WebRegisteredFileEntry entry, String filePath, String content, String query, List<WebInsightSearchResult> results) {
        if (Texts.isBlank(content)) {
            return;
        }
        String[] lines = content.split("\\R", -1);
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            int commentIndex = line.indexOf('#');
            if (commentIndex < 0) {
                continue;
            }
            String comment = line.substring(commentIndex);
            if (contains(comment, query)) {
                add(results, new WebInsightSearchResult(entry.moduleId(), filePath, entry.kind(), "line " + (index + 1), "comment", inferIdType(entry.moduleId(), filePath), snippet(comment, query)));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void scanValue(WebConsoleRegistry.WebRegisteredFileEntry entry, String filePath, String keyPath, Object value, String query, List<WebInsightSearchResult> results) {
        if (value instanceof YamlSection section) {
            scanValue(entry, filePath, keyPath, section.asMap(), query, results);
            return;
        }
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> child : map.entrySet()) {
                if (child.getKey() == null) {
                    continue;
                }
                String childKey = String.valueOf(child.getKey());
                String childPath = Texts.isBlank(keyPath) ? childKey : keyPath + "." + childKey;
                scanValue(entry, filePath, childPath, child.getValue(), query, results);
                if (results.size() >= MAX_RESULTS) {
                    return;
                }
            }
            return;
        }
        if (value instanceof List<?> list) {
            for (int index = 0; index < list.size(); index++) {
                scanValue(entry, filePath, keyPath + "[" + index + "]", list.get(index), query, results);
                if (results.size() >= MAX_RESULTS) {
                    return;
                }
            }
            return;
        }
        String text = String.valueOf(value == null ? "" : value);
        String semanticPath = semanticKeyPath(keyPath);
        boolean pathHit = contains(semanticPath, query);
        boolean valueHit = contains(text, query);
        String matchType = matchType(semanticPath);
        String idType = "definition".equals(matchType) ? inferIdType(entry.moduleId(), filePath) : referenceIdType(semanticPath, text);
        String id = resultId(matchType, idType, text);
        WebInsightAliasResolver.AliasResolution alias = aliasResolution(matchType, idType, id);
        boolean aliasHit = alias != null && matchesAliasQuery(idType, alias, query);
        if (!pathHit && !valueHit && !aliasHit) {
            return;
        }
        String snippet = Texts.isBlank(keyPath) ? text : keyPath + ": " + text;
        add(results, new WebInsightSearchResult(
                entry.moduleId(),
                filePath,
                entry.kind(),
                keyPath,
                matchType,
                idType,
                id,
                snippet(snippet, valueHit ? query : id),
                alias != null,
                alias == null ? "" : alias.sourceId(),
                alias == null ? "" : alias.targetId(),
                alias == null ? "" : idType
        ));
    }

    private WebInsightAliasResolver.AliasResolution aliasResolution(String matchType, String idType, String sourceId) {
        if (!"reference".equals(matchType) || Texts.isBlank(idType) || Texts.isBlank(sourceId)) {
            return null;
        }
        return WebInsightAliasRegistry.resolve(idType, sourceId);
    }

    private boolean matchesAliasQuery(String idType, WebInsightAliasResolver.AliasResolution alias, String query) {
        if (alias == null || Texts.isBlank(query)) {
            return false;
        }
        String normalizedSource = normalizeReferenceId(idType, alias.sourceId());
        String normalizedTarget = normalizeReferenceId(idType, alias.targetId());
        return normalizedSource.contains(query)
                || normalizedTarget.contains(query)
                || normalize(alias.sourceId()).contains(query)
                || normalize(alias.targetId()).contains(query);
    }

    private String resultId(String matchType, String idType, String value) {
        if ("definition".equals(matchType)) {
            return normalizeReferenceId(idType, value);
        }
        if ("reference".equals(matchType)) {
            return normalizeReferenceId(idType, value);
        }
        return "";
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

    private void add(List<WebInsightSearchResult> results, WebInsightSearchResult result) {
        results.add(result);
    }

    private Comparator<WebInsightSearchResult> resultOrder(String query) {
        return Comparator
                .comparingInt((WebInsightSearchResult result) -> resultRank(result, query))
                .thenComparing(result -> normalize(result.path()))
                .thenComparing(result -> normalize(result.keyPath()))
                .thenComparing(result -> normalize(result.snippet()));
    }

    private int resultRank(WebInsightSearchResult result, String query) {
        String path = normalize(result.path());
        String keyPath = normalize(semanticKeyPath(result.keyPath()));
        String snippet = normalize(result.snippet());
        String id = normalize(result.id());
        String matchType = normalize(result.matchType());
        if ("file".equals(matchType) && (path.equals(query) || fileName(path).equals(query))) return 0;
        if ("definition".equals(matchType) && (id.equals(query) || snippet.endsWith(": " + query))) return 1;
        if ("file".equals(matchType)) return 2;
        if (keyPath.equals(query)) return 3;
        if (keyPath.contains(query)) return 4;
        if ("definition".equals(matchType)) return 5;
        if ("reference".equals(matchType)) return 6;
        if ("text".equals(matchType)) return 7;
        if ("comment".equals(matchType)) return 8;
        return snippet.contains(query) ? 9 : 10;
    }

    private String fileName(String path) {
        String normalized = path == null ? "" : path.replace('\\', '/');
        int index = normalized.lastIndexOf('/');
        return index >= 0 ? normalized.substring(index + 1) : normalized;
    }

    private String matchType(String keyPath) {
        if ("id".equals(keyPath)) {
            return "definition";
        }
        return isReferencePath(keyPath) ? "reference" : "text";
    }

    private String semanticKeyPath(String keyPath) {
        if (Texts.isBlank(keyPath)) {
            return "";
        }
        return keyPath.replaceAll("\\[\\d+\\]", "");
    }

    private boolean isReferencePath(String keyPath) {
        for (String segment : keyPath.replace('[', '.').replace(']', '.').split("\\.")) {
            if (REFERENCE_KEYS.contains(segment)) {
                return true;
            }
        }
        return false;
    }

    private String referenceIdType(String keyPath, String value) {
        String lowerPath = normalize(keyPath);
        if (lowerPath.contains("ea_attributes") || lowerPath.contains("attributes") || lowerPath.contains("stats")) return "attribute";
        if (lowerPath.contains("es_skills") || lowerPath.contains("skills")) return "skill";
        if (lowerPath.contains("gems")) return "gem";
        if (lowerPath.contains("level_type") || lowerPath.contains("types")) return "level_type";
        if (lowerPath.contains("gui")) return "gui_template";
        if (lowerPath.contains("actions")) return "action_type";
        String lowerValue = normalize(value);
        if (lowerValue.startsWith("emakiitem-") || lowerValue.startsWith("ei-")) return "emaki_item";
        if (lowerPath.contains("item_source")) return "item_source";
        return "";
    }

    private String inferIdType(String moduleId, String path) {
        String module = normalize(moduleId);
        String normalizedPath = normalize(path).replace('\\', '/');
        if (module.equals("emakiattribute") && normalizedPath.startsWith("attributes/")) return "attribute";
        if (module.equals("emakiitem") && normalizedPath.startsWith("items/")) return "emaki_item";
        if (module.equals("emakigem") && normalizedPath.startsWith("gems/")) return "gem";
        if (module.equals("emakiskills") && normalizedPath.startsWith("skills/")) return "skill";
        if (module.equals("emakilevel") && normalizedPath.startsWith("types/")) return "level_type";
        if (module.equals("emakiforge") && normalizedPath.startsWith("recipes/")) return "forge_recipe";
        if (module.equals("emakistrengthen") && normalizedPath.startsWith("recipes/")) return "strengthen_recipe";
        if (normalizedPath.startsWith("gui/")) return "gui_template";
        return "config_id";
    }

    private boolean isYamlFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".yml") || name.endsWith(".yaml");
    }

    private Path moduleRoot(String moduleId) {
        return plugin.getDataFolder().toPath().getParent().resolve(moduleId).toAbsolutePath().normalize();
    }

    private boolean contains(String value, String query) {
        return normalize(value).contains(query);
    }

    private String snippet(String value, String query) {
        String text = value == null ? "" : value.replace('\n', ' ').replace('\r', ' ');
        String lower = normalize(text);
        int index = lower.indexOf(query);
        if (index < 0 || text.length() <= 160) {
            return text.length() <= 180 ? text : text.substring(0, 177) + "...";
        }
        int start = Math.max(0, index - 60);
        int end = Math.min(text.length(), index + query.length() + 80);
        return (start > 0 ? "..." : "") + text.substring(start, end) + (end < text.length() ? "..." : "");
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
}
