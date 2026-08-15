package emaki.jiuwu.craft.item.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.file.FileRevisions;
import emaki.jiuwu.craft.corelib.api.file.SafePaths;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.item.EmakiItemPlugin;
import emaki.jiuwu.craft.item.model.EmakiItemAlias;

public final class EmakiItemMigrationService {

    private static final long MAX_FILE_BYTES = 512L * 1024L;
    private static final String ALIAS_FILE = "id_aliases.yml";
    private static final List<TargetSpec> TARGET_SPECS = List.of(
            file("EmakiAttribute", "config.yml", "CONFIG"),
            file("EmakiAttribute", "attribute_balance.yml", "CONFIG"),
            glob("EmakiAttribute", "attributes/**/*.yml", "CONFIG"),
            glob("EmakiAttribute", "damage_types/**/*.yml", "CONFIG"),
            glob("EmakiAttribute", "lore_formats/**/*.yml", "CONFIG"),
            glob("EmakiAttribute", "conditions/**/*.yml", "CONFIG"),
            file("EmakiCodex", "config.yml", "CONFIG"),
            glob("EmakiCodex", "advancements/**/*.yml", "CONFIG"),
            glob("EmakiCodex", "lang/**/*.yml", "CONFIG"),
            file("EmakiCooking", "config.yml", "CONFIG"),
            glob("EmakiCooking", "recipes/**/*.yml", "CONFIG"),
            glob("EmakiCooking", "item_adjustments/**/*.yml", "CONFIG"),
            glob("EmakiCooking", "gui/**/*.yml", "GUI"),
            file("EmakiCoreLib", "config.yml", "CONFIG"),
            file("EmakiForge", "config.yml", "CONFIG"),
            glob("EmakiForge", "recipes/**/*.yml", "CONFIG"),
            glob("EmakiForge", "gui/**/*.yml", "GUI"),
            file("EmakiGem", "config.yml", "CONFIG"),
            glob("EmakiGem", "conditions/**/*.yml", "CONFIG"),
            glob("EmakiGem", "resonances/**/*.yml", "CONFIG"),
            glob("EmakiGem", "gui/**/*.yml", "GUI"),
            glob("EmakiGem", "items/**/*.yml", "ITEM"),
            glob("EmakiGem", "gems/**/*.yml", "GEM"),
            file("EmakiItem", "config.yml", "CONFIG"),
            glob("EmakiItem", "items/**/*.yml", "ITEM"),
            glob("EmakiItem", "sets/**/*.yml", "SET"),
            glob("EmakiItem", "gui/**/*.yml", "GUI"),
            file("EmakiLevel", "config.yml", "CONFIG"),
            file("EmakiLevel", "requirements.yml", "CONFIG"),
            glob("EmakiLevel", "types/**/*.yml", "CONFIG"),
            glob("EmakiLevel", "sources/**/*.yml", "CONFIG"),
            glob("EmakiLevel", "gui/**/*.yml", "GUI"),
            glob("EmakiLevel", "lang/**/*.yml", "CONFIG"),
            file("EmakiSkills", "config.yml", "CONFIG"),
            glob("EmakiSkills", "skills/**/*.yml", "CONFIG"),
            glob("EmakiSkills", "resources/**/*.yml", "CONFIG"),
            glob("EmakiSkills", "gui/**/*.yml", "GUI"),
            file("EmakiStrengthen", "config.yml", "CONFIG"),
            glob("EmakiStrengthen", "recipes/**/*.yml", "CONFIG"),
            glob("EmakiStrengthen", "gui/**/*.yml", "GUI")
    );

    private final EmakiItemPlugin plugin;

    public EmakiItemMigrationService(EmakiItemPlugin plugin) {
        this.plugin = plugin;
    }

    public record OnlineMigrationResult(int changedItems, int skippedPlayers) {

        public OnlineMigrationResult {
            changedItems = Math.max(0, changedItems);
            skippedPlayers = Math.max(0, skippedPlayers);
        }
    }

    public static final class PartialMigrationException extends IOException {

        private final Map<String, Object> outcome;

        public PartialMigrationException(String message, Throwable cause, Map<String, Object> outcome) {
            super(message, cause);
            this.outcome = outcome == null ? Map.of() : Map.copyOf(outcome);
        }

        public Map<String, Object> outcome() {
            return outcome;
        }
    }

    public Map<String, Object> preview(String oldId, String newId) throws IOException {
        String oldNormalized = Texts.normalizeId(oldId);
        String newNormalized = Texts.normalizeId(newId);
        List<Map<String, Object>> files = new ArrayList<>();
        int replacements = 0;
        for (TargetFile target : targetFiles()) {
            String content = Files.readString(target.path(), StandardCharsets.UTF_8);
            Replacement replacement = replaceContent(content, oldNormalized, newNormalized);
            if (replacement.count() <= 0) {
                continue;
            }
            replacements += replacement.count();
            files.add(Map.of(
                    "moduleId", target.moduleId(),
                    "path", target.relativePath(),
                    "kind", target.kind(),
                    "replacements", replacement.count(),
                    "revision", FileRevisions.revision(target.path())
            ));
        }
        return Map.of(
                "oldId", oldNormalized,
                "newId", newNormalized,
                "oldExists", plugin.itemLoader().get(oldNormalized) != null,
                "newExists", plugin.itemLoader().get(newNormalized) != null,
                "aliasExists", plugin.aliasLoader().get(oldNormalized) != null,
                "aliasRevision", FileRevisions.revision(aliasPath()),
                "files", files,
                "replacementCount", replacements
        );
    }

    public Map<String, Object> apply(String oldId,
            String newId,
            boolean replaceReferences,
            boolean keepAlias) throws IOException {
        String oldNormalized = Texts.normalizeId(oldId);
        String newNormalized = Texts.normalizeId(newId);
        if (Texts.isBlank(oldNormalized) || Texts.isBlank(newNormalized) || oldNormalized.equals(newNormalized)) {
            throw new IOException("旧 ID 与新 ID 不合法");
        }
        if (plugin.itemLoader().get(newNormalized) == null) {
            throw new IOException("目标物品 ID 不存在：" + newNormalized);
        }

        List<PlannedWrite> plannedWrites = new ArrayList<>();
        int replacements = 0;
        if (replaceReferences) {
            for (TargetFile target : targetFiles()) {
                long revision = FileRevisions.revision(target.path());
                String content = Files.readString(target.path(), StandardCharsets.UTF_8);
                requireUnchanged(target.path(), revision);
                Replacement replacement = replaceContent(content, oldNormalized, newNormalized);
                if (replacement.count() <= 0 || replacement.content().equals(content)) {
                    continue;
                }
                validateYamlContent(replacement.content(), target.moduleId() + "/" + target.relativePath());
                plannedWrites.add(new PlannedWrite(target, replacement.content(), replacement.count(), revision));
                replacements += replacement.count();
            }
        }

        AliasWrite aliasWrite = null;
        if (keepAlias) {
            Map<String, EmakiItemAlias> aliases = new LinkedHashMap<>(plugin.aliasLoader().all());
            EmakiItemAlias alias = new EmakiItemAlias(oldNormalized, newNormalized, true, true, "never");
            if (!alias.valid()) {
                throw new IOException("无法创建 ID alias：" + oldNormalized + " -> " + newNormalized);
            }
            aliases.put(alias.oldId(), alias);
            String content = renderAliases(aliases);
            validateYamlContent(content, plugin.getName() + "/" + ALIAS_FILE);
            Path aliasPath = aliasPath();
            validateWritableYaml(aliasPath);
            aliasWrite = new AliasWrite(content, FileRevisions.revision(aliasPath));
        }

        List<Map<String, Object>> changed = new ArrayList<>();
        int appliedReplacements = 0;
        try {
            for (PlannedWrite planned : plannedWrites) {
                long nextRevision = saveDirect(planned.target(), planned.content(), planned.expectedRevision());
                changed.add(Map.of(
                        "moduleId", planned.target().moduleId(),
                        "path", planned.target().relativePath(),
                        "kind", planned.target().kind(),
                        "replacements", planned.replacements(),
                        "revision", nextRevision
                ));
                appliedReplacements += planned.replacements();
            }
        } catch (IOException exception) {
            if (changed.isEmpty()) {
                throw exception;
            }
            throw partialFailure(oldNormalized, newNormalized, changed, appliedReplacements,
                    false, currentAliasRevision(), exception);
        }

        Long aliasRevision = null;
        if (aliasWrite != null) {
            TargetFile aliasTarget = new TargetFile(plugin.getName(), ALIAS_FILE, "ALIAS", aliasPath());
            try {
                aliasRevision = saveDirect(aliasTarget, aliasWrite.content(), aliasWrite.expectedRevision());
            } catch (IOException exception) {
                if (changed.isEmpty()) {
                    throw exception;
                }
                throw partialFailure(oldNormalized, newNormalized, changed, appliedReplacements,
                        false, currentAliasRevision(), exception);
            }
            try {
                plugin.aliasLoader().load();
            } catch (RuntimeException exception) {
                throw partialFailure(oldNormalized, newNormalized, changed, appliedReplacements,
                        true, aliasRevision, exception);
            }
        }
        return applyOutcome(
                oldNormalized,
                newNormalized,
                changed,
                appliedReplacements,
                aliasWrite != null,
                aliasRevision == null ? currentAliasRevision() : aliasRevision
        );
    }

    private PartialMigrationException partialFailure(String oldId,
            String newId,
            List<Map<String, Object>> changed,
            int replacementCount,
            boolean aliasKept,
            long aliasRevision,
            Throwable cause) {
        return new PartialMigrationException(
                "迁移仅部分完成：" + oldId + " -> " + newId,
                cause,
                applyOutcome(oldId, newId, changed, replacementCount, aliasKept, aliasRevision));
    }

    private long currentAliasRevision() {
        try {
            return FileRevisions.revision(aliasPath());
        } catch (IOException ignored) {
            return 0L;
        }
    }

    private Map<String, Object> applyOutcome(String oldId,
            String newId,
            List<Map<String, Object>> changed,
            int replacementCount,
            boolean aliasKept,
            long aliasRevision) {
        return Map.of(
                "oldId", oldId,
                "newId", newId,
                "changedFiles", changed == null ? List.of() : List.copyOf(changed),
                "replacementCount", Math.max(0, replacementCount),
                "aliasKept", aliasKept,
                "aliasRevision", Math.max(0L, aliasRevision)
        );
    }

    public int migrateInventory(Player player) {
        if (player == null) {
            return 0;
        }
        int changed = 0;
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack original = player.getInventory().getItem(slot);
            ItemStack updated = plugin.updateService().forceUpdate(original);
            if (updated != original) {
                player.getInventory().setItem(slot, updated);
                changed++;
            }
        }
        return changed;
    }

    public int migrateAllOnlineInventories() {
        return migrateOwnedOnlineInventories().changedItems();
    }

    public OnlineMigrationResult migrateOwnedOnlineInventories() {
        int changed = 0;
        int skipped = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (plugin.scheduling() == null || !plugin.scheduling().ownsEntity(player)) {
                skipped++;
                continue;
            }
            changed += migrateInventory(player);
        }
        return new OnlineMigrationResult(changed, skipped);
    }

    private List<TargetFile> targetFiles() throws IOException {
        List<TargetFile> targets = new ArrayList<>();
        for (TargetSpec spec : TARGET_SPECS) {
            if (spec.glob()) {
                targets.addAll(globChildren(spec));
                continue;
            }
            Path path = resolve(spec.moduleId(), spec.relativePath());
            if (readableYaml(path) && !isAliasFile(spec.moduleId(), spec.relativePath())) {
                targets.add(new TargetFile(spec.moduleId(), spec.relativePath(), spec.kind(), path));
            }
        }
        targets.sort(Comparator.comparing(TargetFile::moduleId).thenComparing(TargetFile::relativePath));
        return targets;
    }

    private List<TargetFile> globChildren(TargetSpec spec) throws IOException {
        Path root = moduleRoot(spec.moduleId());
        if (root == null) {
            return List.of();
        }
        String baseDir = extractBaseDir(spec.relativePath());
        Path dir = SafePaths.resolveInside(root, baseDir);
        if (dir == null || !Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }
        List<TargetFile> result = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(dir)) {
            stream.filter(this::readableYaml)
                    .sorted(Comparator.comparing(path -> normalizeRelativePath(root.relativize(path).toString())))
                    .forEach(path -> {
                        String relative = normalizeRelativePath(root.relativize(path).toString());
                        if (!isAliasFile(spec.moduleId(), relative)) {
                            result.add(new TargetFile(spec.moduleId(), relative, spec.kind(), path));
                        }
                    });
        }
        return result;
    }

    private Replacement replaceContent(String content, String oldId, String newId) {
        if (Texts.isBlank(content) || Texts.isBlank(oldId) || Texts.isBlank(newId)) {
            return new Replacement(content, 0);
        }
        String next = content;
        int count = 0;
        for (String prefix : List.of("emakiitem-", "ei-")) {
            ReplaceResult result = replaceToken(next, prefix + oldId, prefix + newId);
            next = result.content();
            count += result.count();
        }
        ReplaceResult idResult = replaceYamlIdLine(next, oldId, newId);
        next = idResult.content();
        count += idResult.count();
        return new Replacement(next, count);
    }

    private ReplaceResult replaceToken(String content, String oldToken, String newToken) {
        Pattern pattern = Pattern.compile("(?i)(?<![a-z0-9_.:-])" + Pattern.quote(oldToken) + "(?![a-z0-9_.:-])");
        Matcher matcher = pattern.matcher(content);
        String replaced = matcher.replaceAll(Matcher.quoteReplacement(newToken));
        return new ReplaceResult(replaced, countMatches(matcher));
    }

    private ReplaceResult replaceYamlIdLine(String content, String oldId, String newId) {
        Pattern pattern = Pattern.compile("(?m)^(\\s*id\\s*:\\s*)([\\\"']?)" + Pattern.quote(oldId) + "\\2(\\s*)$");
        Matcher matcher = pattern.matcher(content);
        String replaced = matcher.replaceAll("$1$2" + Matcher.quoteReplacement(newId) + "$2$3");
        return new ReplaceResult(replaced, countMatches(matcher));
    }

    private int countMatches(Matcher matcher) {
        matcher.reset();
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private long saveDirect(TargetFile target, String content, long expectedRevision) throws IOException {
        validateYamlContent(content, target.moduleId() + "/" + target.relativePath());
        validateWritableYaml(target.path());
        String previousContent = Files.exists(target.path())
                ? Files.readString(target.path(), StandardCharsets.UTF_8)
                : "";
        long previousRevision = requireUnchanged(target.path(), expectedRevision);
        writeBackup(target, previousContent);
        Files.createDirectories(target.path().getParent());
        Files.writeString(target.path(), content == null ? "" : content, StandardCharsets.UTF_8);
        return FileRevisions.advance(target.path(), previousRevision);
    }

    private long requireUnchanged(Path path, long expectedRevision) throws IOException {
        long currentRevision = FileRevisions.revision(path);
        if (currentRevision != expectedRevision) {
            throw new IOException("文件已被其他进程修改，请重新执行迁移：" + path.getFileName());
        }
        return FileRevisions.requireExpected(path, expectedRevision);
    }

    private void validateYamlContent(String content, String label) throws IOException {
        String safeContent = content == null ? "" : content;
        if (safeContent.getBytes(StandardCharsets.UTF_8).length > MAX_FILE_BYTES) {
            throw new IOException("YAML 文件超过 512 KiB 限制：" + label);
        }
        try {
            YamlFiles.load(safeContent);
        } catch (RuntimeException exception) {
            throw new IOException("YAML 校验失败：" + label, exception);
        }
    }

    private void validateWritableYaml(Path path) throws IOException {
        if (path == null || !isYamlPath(path.getFileName().toString())) {
            throw new IOException("目标路径不是 YAML 文件");
        }
        if (!Files.exists(path)) {
            return;
        }
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("目标不是普通文件：" + path.getFileName());
        }
        if (Files.size(path) > MAX_FILE_BYTES) {
            throw new IOException("YAML 文件超过 512 KiB 限制：" + path.getFileName());
        }
    }

    private void writeBackup(TargetFile target, String content) throws IOException {
        Path dataRoot = moduleRoot(plugin.getName());
        requireSafeDirectory(dataRoot, "EmakiItem 数据目录");
        Path backupRoot = SafePaths.resolveInside(dataRoot, "migration-backups");
        requireSafeDirectory(backupRoot, "迁移备份根目录");
        Path snapshotRoot = SafePaths.resolveInside(backupRoot, Long.toString(Instant.now().toEpochMilli()));
        requireSafeDirectory(snapshotRoot, "迁移备份快照目录");
        Path backup = SafePaths.resolveInside(snapshotRoot, target.moduleId() + "/" + target.relativePath());
        if (backup == null || backup.getParent() == null) {
            throw new IOException("无法创建迁移备份路径：" + target.moduleId() + "/" + target.relativePath());
        }
        createSafeDirectories(snapshotRoot, backup.getParent());
        if (Files.exists(backup) && !Files.isRegularFile(backup, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("迁移备份目标不是普通文件：" + backup.getFileName());
        }
        Files.writeString(backup, content == null ? "" : content, StandardCharsets.UTF_8);
    }

    private void createSafeDirectories(Path root, Path directory) throws IOException {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalizedDirectory = directory.toAbsolutePath().normalize();
        if (!normalizedDirectory.startsWith(normalizedRoot)) {
            throw new IOException("迁移备份目录超出允许范围");
        }
        Path current = normalizedRoot;
        for (Path segment : normalizedRoot.relativize(normalizedDirectory)) {
            current = SafePaths.resolveInside(current, segment.toString());
            requireSafeDirectory(current, "迁移备份目录");
        }
    }

    private void requireSafeDirectory(Path path, String label) throws IOException {
        if (path == null) {
            throw new IOException("无法解析" + label);
        }
        if (Files.exists(path) && !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException(label + "不是普通目录");
        }
        Files.createDirectories(path);
    }

    private String renderAliases(Map<String, EmakiItemAlias> source) {
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> aliasMap = new LinkedHashMap<>();
        for (EmakiItemAlias alias : source.values()) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("target", alias.targetId());
            data.put("migrate_pdc", alias.migratePdc());
            data.put("rewrite_display", alias.rewriteDisplay());
            data.put("expires_after", Texts.isBlank(alias.expiresAfter()) ? "never" : alias.expiresAfter());
            aliasMap.put(alias.oldId(), data);
        }
        root.put("aliases", aliasMap);
        return YamlFiles.dump(root);
    }

    private boolean readableYaml(Path path) {
        try {
            return path != null
                    && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                    && isYamlPath(path.getFileName().toString())
                    && Files.size(path) <= MAX_FILE_BYTES;
        } catch (IOException ignored) {
            return false;
        }
    }

    private Path resolve(String moduleId, String relativePath) {
        Path root = moduleRoot(moduleId);
        return root == null ? null : SafePaths.resolveInside(root, normalizeRelativePath(relativePath));
    }

    private Path moduleRoot(String moduleId) {
        Path dataFolder = plugin.getDataFolder().toPath().toAbsolutePath().normalize();
        Path pluginsRoot = dataFolder.getParent();
        Path root = pluginsRoot == null ? null : SafePaths.resolveInside(pluginsRoot, moduleId);
        if (root == null || (Files.exists(root) && !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS))) {
            return null;
        }
        return root;
    }

    private Path aliasPath() throws IOException {
        Path dataRoot = moduleRoot(plugin.getName());
        if (dataRoot == null) {
            throw new IOException("无法解析 EmakiItem 数据目录");
        }
        Path path = SafePaths.resolveInside(dataRoot, ALIAS_FILE);
        if (path == null) {
            throw new IOException("无法解析 ID alias 文件路径");
        }
        return path;
    }

    private boolean isYamlPath(String path) {
        String lower = Texts.toStringSafe(path).toLowerCase(Locale.ROOT);
        return lower.endsWith(".yml") || lower.endsWith(".yaml");
    }

    private boolean isAliasFile(String moduleId, String path) {
        return plugin.getName().equalsIgnoreCase(Texts.toStringSafe(moduleId))
                && ALIAS_FILE.equalsIgnoreCase(normalizeRelativePath(path));
    }

    private String extractBaseDir(String globPath) {
        int starIndex = globPath.indexOf('*');
        if (starIndex <= 0) {
            return normalizeRelativePath(globPath);
        }
        String before = globPath.substring(0, starIndex);
        if (before.endsWith("/")) {
            before = before.substring(0, before.length() - 1);
        }
        return normalizeRelativePath(before);
    }

    private String normalizeRelativePath(String path) {
        return Texts.toStringSafe(path).replace('\\', '/').replaceAll("^/+", "");
    }

    private static TargetSpec file(String moduleId, String relativePath, String kind) {
        return new TargetSpec(moduleId, relativePath, kind, false);
    }

    private static TargetSpec glob(String moduleId, String relativePath, String kind) {
        return new TargetSpec(moduleId, relativePath, kind, true);
    }

    private record TargetSpec(String moduleId, String relativePath, String kind, boolean glob) {}
    private record TargetFile(String moduleId, String relativePath, String kind, Path path) {}
    private record Replacement(String content, int count) {}
    private record ReplaceResult(String content, int count) {}
    private record PlannedWrite(TargetFile target, String content, int replacements, long expectedRevision) {}
    private record AliasWrite(String content, long expectedRevision) {}
}
