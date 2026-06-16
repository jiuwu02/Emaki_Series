package emaki.jiuwu.craft.corelib.web.history;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.web.WebConsoleConfig;
import emaki.jiuwu.craft.corelib.web.WebConsoleRegistry;
import emaki.jiuwu.craft.corelib.web.WebFileRevisions;
import emaki.jiuwu.craft.corelib.web.WebPathSecurity;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;

public final class WebChangeHistoryService {

    private static final DateTimeFormatter SNAPSHOT_ID_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSS").withZone(ZoneOffset.UTC);
    private static final String SNAPSHOT_EXTENSION = ".snapshot";
    private static final String META_EXTENSION = ".meta.yml";

    private final JavaPlugin plugin;
    private final WebConsoleConfig config;
    private final Path historyRoot;

    public WebChangeHistoryService(JavaPlugin plugin, WebConsoleConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.historyRoot = plugin.getDataFolder().toPath().resolve("history").toAbsolutePath().normalize();
    }

    public HistoryTarget moduleTarget(String moduleId, String path, String kind) throws IOException {
        if (!WebConsoleRegistry.isModuleRegistered(moduleId)) {
            throw new IOException("模块未注册");
        }
        Path root = plugin.getDataFolder().toPath().getParent().resolve(moduleId).toAbsolutePath().normalize();
        Path target = WebPathSecurity.resolveInside(root, normalizeRelativePath(path));
        if (target == null) {
            throw new IOException("路径不合法");
        }
        return new HistoryTarget("modules", moduleId, normalizeRelativePath(path), normalizeKind(kind), root, target);
    }

    public HistoryTarget scriptTarget(String path) throws IOException {
        Path root = plugin.getDataFolder().toPath().resolve("scripts").toAbsolutePath().normalize();
        Path target = WebPathSecurity.resolveInside(root, normalizeRelativePath(path));
        if (target == null) {
            throw new IOException("脚本路径不合法");
        }
        return new HistoryTarget("scripts", plugin.getName(), normalizeRelativePath(path), "SCRIPT", root, target);
    }

    public void recordBeforeWrite(HistoryTarget target, String operation, String actor) throws IOException {
        if (!shouldRecordWrites() || target == null || !Files.isRegularFile(target.target())) {
            return;
        }
        record(target, operation, actor, Files.readString(target.target(), StandardCharsets.UTF_8), currentRevision(target));
    }

    public void recordCreate(HistoryTarget target, String actor) throws IOException {
        if (!shouldRecordWrites() || target == null || !Files.isRegularFile(target.target())) {
            return;
        }
        record(target, "create", actor, Files.readString(target.target(), StandardCharsets.UTF_8), currentRevision(target));
    }

    public void recordDeleteBackup(HistoryTarget target, String actor) throws IOException {
        if (!shouldRecordDelete() || target == null || !Files.isRegularFile(target.target())) {
            return;
        }
        record(target, "delete", actor, Files.readString(target.target(), StandardCharsets.UTF_8), currentRevision(target));
    }

    public List<Map<String, Object>> list(HistoryTarget target) throws IOException {
        Path directory = historyDirectory(target);
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        List<Map<String, Object>> entries = new ArrayList<>();
        try (Stream<Path> stream = Files.list(directory)) {
            stream.filter(path -> path.getFileName().toString().endsWith(META_EXTENSION))
                    .sorted(Comparator.comparing(Path::toString).reversed())
                    .forEach(path -> readMeta(path, entries));
        }
        return entries;
    }

    public Map<String, Object> snapshot(HistoryTarget target, String id) throws IOException {
        SnapshotFiles files = snapshotFiles(target, id);
        Map<String, Object> meta = metaMap(files.meta());
        String content = Files.exists(files.snapshot()) ? Files.readString(files.snapshot(), StandardCharsets.UTF_8) : "";
        boolean rollbackAllowed = booleanMeta(meta, "rollbackAllowed", false);
        return Map.of(
                "entry", meta,
                "content", rollbackAllowed ? content : redactedPreview(content),
                "rollbackAllowed", rollbackAllowed
        );
    }

    public Map<String, Object> diffCurrent(HistoryTarget target, String id) throws IOException {
        SnapshotFiles files = snapshotFiles(target, id);
        Map<String, Object> meta = metaMap(files.meta());
        if (!booleanMeta(meta, "rollbackAllowed", false)) {
            return Map.of("diff", "此历史记录包含敏感配置预览，不能进行整文件 diff 或回滚。", "rollbackAllowed", false);
        }
        String before = Files.exists(files.snapshot()) ? Files.readString(files.snapshot(), StandardCharsets.UTF_8) : "";
        String current = Files.exists(target.target()) ? Files.readString(target.target(), StandardCharsets.UTF_8) : "";
        return Map.of("diff", unifiedDiff(before, current, id, "current"), "rollbackAllowed", true);
    }

    public long rollback(HistoryTarget target, String id, String actor, Long expectedRevision) throws IOException {
        SnapshotFiles files = snapshotFiles(target, id);
        Map<String, Object> meta = metaMap(files.meta());
        if (!booleanMeta(meta, "rollbackAllowed", false)) {
            throw new RollbackForbiddenException("此历史记录包含敏感配置或无可回滚快照，不能整文件回滚。");
        }
        long currentRevision = WebFileRevisions.requireExpected(target.target(), expectedRevision, "文件已被其他管理员修改，请重载后再回滚。");
        if (Files.isRegularFile(target.target())) {
            record(target, "rollback_backup", actor, Files.readString(target.target(), StandardCharsets.UTF_8), currentRevision);
        }
        String content = Files.readString(files.snapshot(), StandardCharsets.UTF_8);
        Files.createDirectories(target.target().getParent());
        Files.writeString(target.target(), content, StandardCharsets.UTF_8);
        long nextRevision = WebFileRevisions.advance(target.target(), currentRevision);
        record(target, "rollback", actor, content, nextRevision);
        prune(target);
        return nextRevision;
    }

    public long currentRevision(HistoryTarget target) throws IOException {
        return WebFileRevisions.revision(target.target());
    }

    private void record(HistoryTarget target, String operation, String actor, String content, long sourceRevision) throws IOException {
        if (!historyEnabled()) {
            return;
        }
        Files.createDirectories(historyDirectory(target));
        String id = uniqueSnapshotId(target);
        boolean sensitive = isSensitive(target, content);
        String snapshotContent = sensitive ? redactedPreview(content) : (content == null ? "" : content);
        Path snapshot = historyDirectory(target).resolve(id + SNAPSHOT_EXTENSION);
        Path meta = historyDirectory(target).resolve(id + META_EXTENSION);
        Files.writeString(snapshot, snapshotContent, StandardCharsets.UTF_8);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("id", id);
        metadata.put("scope", target.scope());
        metadata.put("moduleId", target.moduleId());
        metadata.put("path", target.path());
        metadata.put("kind", target.kind());
        metadata.put("operation", safeOperation(operation));
        metadata.put("actor", actor == null || actor.isBlank() ? "web" : actor);
        metadata.put("beforeHash", sha256(content == null ? "" : content));
        metadata.put("afterHash", sha256(snapshotContent));
        metadata.put("createdAt", System.currentTimeMillis());
        metadata.put("sourceRevision", sourceRevision);
        metadata.put("size", content == null ? 0 : content.getBytes(StandardCharsets.UTF_8).length);
        metadata.put("rollbackAllowed", !sensitive);
        if (sensitive) {
            metadata.put("note", "sensitive_redacted");
        }
        YamlFiles.save(meta.toFile(), metadata);
        prune(target);
    }

    private void prune(HistoryTarget target) throws IOException {
        WebConsoleConfig.History history = config.history();
        if (history == null) {
            return;
        }
        Path directory = historyDirectory(target);
        if (!Files.isDirectory(directory)) {
            return;
        }
        List<Path> metas;
        try (Stream<Path> stream = Files.list(directory)) {
            metas = stream.filter(path -> path.getFileName().toString().endsWith(META_EXTENSION))
                    .sorted(Comparator.comparing(Path::toString).reversed())
                    .toList();
        }
        long cutoff = System.currentTimeMillis() - history.maxAgeDays() * 86_400_000L;
        for (int i = 0; i < metas.size(); i++) {
            Path meta = metas.get(i);
            Map<String, Object> data = metaMap(meta);
            long createdAt = longMeta(data, "createdAt", 0L);
            if (i >= history.maxSnapshotsPerFile() || (createdAt > 0 && createdAt < cutoff)) {
                String id = String.valueOf(data.getOrDefault("id", snapshotIdFromMeta(meta)));
                Files.deleteIfExists(directory.resolve(id + SNAPSHOT_EXTENSION));
                Files.deleteIfExists(meta);
            }
        }
    }

    private SnapshotFiles snapshotFiles(HistoryTarget target, String id) throws IOException {
        String safeId = sanitizeSnapshotId(id);
        if (safeId.isBlank()) {
            throw new IOException("缺少历史记录 ID");
        }
        Path directory = historyDirectory(target);
        Path meta = directory.resolve(safeId + META_EXTENSION).normalize();
        Path snapshot = directory.resolve(safeId + SNAPSHOT_EXTENSION).normalize();
        if (!meta.startsWith(directory) || !snapshot.startsWith(directory) || !Files.isRegularFile(meta)) {
            throw new IOException("历史记录不存在");
        }
        return new SnapshotFiles(snapshot, meta);
    }

    private Path historyDirectory(HistoryTarget target) {
        return historyRoot.resolve(target.scope()).resolve(encodeSegment(target.moduleId())).resolve(encodeSegment(target.path())).normalize();
    }

    private boolean historyEnabled() {
        return config != null && config.history() != null && config.history().enabled();
    }

    private boolean shouldRecordWrites() {
        return historyEnabled() && config.history().recordWebWrites();
    }

    private boolean shouldRecordDelete() {
        return historyEnabled() && config.history().recordDeleteBackup();
    }

    private boolean isSensitive(HistoryTarget target, String content) {
        if ("modules".equals(target.scope()) && plugin.getName().equals(target.moduleId()) && "config.yml".equalsIgnoreCase(target.path())) {
            return true;
        }
        String lower = content == null ? "" : content.toLowerCase(Locale.ROOT);
        return lower.contains("password:") || lower.contains("token:") || lower.contains("secret:") || lower.contains("api_key:");
    }

    private String redactedPreview(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        List<String> lines = content.lines().map(line -> {
            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.contains("password:") || lower.contains("token:") || lower.contains("secret:") || lower.contains("api_key:")) {
                int index = line.indexOf(':');
                return index >= 0 ? line.substring(0, index + 1) + " \"[redacted]\"" : "[redacted]";
            }
            return line;
        }).toList();
        return String.join("\n", lines);
    }

    private Map<String, Object> metaMap(Path meta) throws IOException {
        YamlSection section = YamlFiles.load(meta.toFile());
        return new LinkedHashMap<>(section.asMap());
    }

    private void readMeta(Path meta, List<Map<String, Object>> entries) {
        try {
            entries.add(metaMap(meta));
        } catch (IOException ignored) {
        }
    }

    private String uniqueSnapshotId(HistoryTarget target) throws IOException {
        String base = SNAPSHOT_ID_FORMAT.format(Instant.now());
        Path directory = historyDirectory(target);
        String candidate = base;
        int suffix = 1;
        while (Files.exists(directory.resolve(candidate + META_EXTENSION)) || Files.exists(directory.resolve(candidate + SNAPSHOT_EXTENSION))) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private String unifiedDiff(String before, String after, String beforeLabel, String afterLabel) {
        List<String> left = before == null || before.isEmpty() ? List.of() : before.lines().toList();
        List<String> right = after == null || after.isEmpty() ? List.of() : after.lines().toList();
        StringBuilder builder = new StringBuilder();
        builder.append("--- ").append(beforeLabel).append('\n');
        builder.append("+++ ").append(afterLabel).append('\n');
        int max = Math.max(left.size(), right.size());
        for (int i = 0; i < max; i++) {
            String oldLine = i < left.size() ? left.get(i) : null;
            String newLine = i < right.size() ? right.get(i) : null;
            if (oldLine != null && oldLine.equals(newLine)) {
                builder.append(' ').append(oldLine).append('\n');
            } else {
                if (oldLine != null) builder.append('-').append(oldLine).append('\n');
                if (newLine != null) builder.append('+').append(newLine).append('\n');
            }
        }
        return builder.toString();
    }

    private String encodeSegment(String value) {
        String safe = normalizeRelativePath(value).replace('/', '_').replace('\\', '_');
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < safe.length(); i++) {
            char c = safe.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == '.') {
                builder.append(c);
            } else {
                builder.append('_');
            }
        }
        return builder.isEmpty() ? "root" : builder.toString();
    }

    private String normalizeRelativePath(String path) {
        return path == null ? "" : path.trim().replace('\\', '/').replaceAll("^/+|/+$", "");
    }

    private String normalizeKind(String kind) {
        return kind == null || kind.isBlank() ? "CONFIG" : kind.trim().toUpperCase(Locale.ROOT);
    }

    private String safeOperation(String operation) {
        return operation == null || operation.isBlank() ? "save" : operation.trim().toLowerCase(Locale.ROOT);
    }

    private String sanitizeSnapshotId(String id) {
        if (id == null) {
            return "";
        }
        return id.trim().replaceAll("[^0-9A-Za-z_.-]", "");
    }

    private String snapshotIdFromMeta(Path meta) {
        String name = meta.getFileName().toString();
        return name.endsWith(META_EXTENSION) ? name.substring(0, name.length() - META_EXTENSION.length()) : name;
    }

    private boolean booleanMeta(Map<String, Object> meta, String key, boolean fallback) {
        Object value = meta.get(key);
        return value instanceof Boolean bool ? bool : value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }

    private long longMeta(Map<String, Object> meta, String key, long fallback) {
        Object value = meta.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return value == null ? fallback : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            return Integer.toHexString(content.hashCode());
        }
    }

    public record HistoryTarget(String scope, String moduleId, String path, String kind, Path root, Path target) {}

    private record SnapshotFiles(Path snapshot, Path meta) {}

    public static final class RollbackForbiddenException extends IOException {
        public RollbackForbiddenException(String message) {
            super(message);
        }
    }
}
