package emaki.jiuwu.craft.item.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.web.WebConsoleRegistry;
import emaki.jiuwu.craft.corelib.web.WebConsoleYamlRegistrar;
import emaki.jiuwu.craft.corelib.web.WebFileRevisions;
import emaki.jiuwu.craft.corelib.web.WebPathSecurity;
import emaki.jiuwu.craft.corelib.web.WebPluginApiRequest;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;
import emaki.jiuwu.craft.item.EmakiItemPlugin;
import emaki.jiuwu.craft.item.model.EmakiItemAlias;

public final class EmakiItemMigrationService {

    private static final long MAX_FILE_BYTES = 512L * 1024L;
    private static final String ALIAS_FILE = "id_aliases.yml";

    private final EmakiItemPlugin plugin;

    public EmakiItemMigrationService(EmakiItemPlugin plugin) {
        this.plugin = plugin;
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
                    "revision", WebFileRevisions.revision(target.path())
            ));
        }
        return Map.of(
                "oldId", oldNormalized,
                "newId", newNormalized,
                "oldExists", plugin.itemLoader().get(oldNormalized) != null,
                "newExists", plugin.itemLoader().get(newNormalized) != null,
                "aliasExists", plugin.aliasLoader().get(oldNormalized) != null,
                "aliasRevision", WebFileRevisions.revision(aliasPath()),
                "files", files,
                "replacementCount", replacements
        );
    }

    public Map<String, Object> apply(String oldId,
            String newId,
            boolean replaceReferences,
            boolean keepAlias,
            Map<String, Long> expectedRevisions,
            WebPluginApiRequest request) throws IOException {
        String oldNormalized = Texts.normalizeId(oldId);
        String newNormalized = Texts.normalizeId(newId);
        if (Texts.isBlank(oldNormalized) || Texts.isBlank(newNormalized) || oldNormalized.equals(newNormalized)) {
            throw new IOException("旧 ID 与新 ID 不合法");
        }
        if (plugin.itemLoader().get(newNormalized) == null) {
            throw new IOException("目标物品 ID 不存在：" + newNormalized);
        }
        if (request == null) {
            throw new IOException("Web 写入上下文不可用");
        }

        List<PlannedWrite> plannedWrites = new ArrayList<>();
        int replacements = 0;
        if (replaceReferences) {
            for (TargetFile target : targetFiles()) {
                String content = Files.readString(target.path(), StandardCharsets.UTF_8);
                Replacement replacement = replaceContent(content, oldNormalized, newNormalized);
                if (replacement.count() <= 0 || replacement.content().equals(content)) {
                    continue;
                }
                YamlFiles.load(replacement.content());
                Long expectedRevision = expectedRevision(expectedRevisions, target.moduleId(), target.relativePath());
                WebFileRevisions.requireExpected(target.path(), expectedRevision);
                plannedWrites.add(new PlannedWrite(target, replacement.content(), replacement.count(), expectedRevision));
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
            YamlFiles.load(content);
            Long expectedRevision = expectedRevision(expectedRevisions, plugin.getName(), ALIAS_FILE);
            WebFileRevisions.requireExpected(aliasPath(), expectedRevision);
            aliasWrite = new AliasWrite(content, expectedRevision);
        }

        List<Map<String, Object>> changed = new ArrayList<>();
        for (PlannedWrite planned : plannedWrites) {
            writeBackup(planned.target(), Files.readString(planned.target().path(), StandardCharsets.UTF_8));
            long nextRevision = request.saveModuleConfig(
                    planned.target().moduleId(),
                    planned.target().relativePath(),
                    planned.target().kind(),
                    planned.content(),
                    planned.expectedRevision(),
                    "item_rename_references"
            );
            changed.add(Map.of(
                    "moduleId", planned.target().moduleId(),
                    "path", planned.target().relativePath(),
                    "kind", planned.target().kind(),
                    "replacements", planned.replacements(),
                    "revision", nextRevision
            ));
        }
        Long aliasRevision = null;
        if (aliasWrite != null) {
            aliasRevision = request.saveModuleConfig(
                    plugin.getName(),
                    ALIAS_FILE,
                    "ALIAS",
                    aliasWrite.content(),
                    aliasWrite.expectedRevision(),
                    "item_rename_alias"
            );
            plugin.aliasLoader().load();
        }
        return Map.of(
                "oldId", oldNormalized,
                "newId", newNormalized,
                "changedFiles", changed,
                "replacementCount", replacements,
                "aliasKept", keepAlias,
                "aliasRevision", aliasRevision == null ? WebFileRevisions.revision(aliasPath()) : aliasRevision
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
        ItemStack offhand = player.getInventory().getItemInOffHand();
        ItemStack updatedOffhand = plugin.updateService().forceUpdate(offhand);
        if (updatedOffhand != offhand) {
            player.getInventory().setItemInOffHand(updatedOffhand);
            changed++;
        }
        return changed;
    }

    public int migrateAllOnlineInventories() {
        int changed = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            changed += migrateInventory(player);
        }
        return changed;
    }

    private List<TargetFile> targetFiles() throws IOException {
        WebConsoleYamlRegistrar.scanAll();
        List<TargetFile> targets = new ArrayList<>();
        for (WebConsoleRegistry.WebRegisteredFileEntry entry : WebConsoleRegistry.registeredFileEntries()) {
            if (!isYamlPath(entry.relativePath()) || isAliasFile(entry.moduleId(), entry.relativePath())) {
                continue;
            }
            if (isGlobPath(entry.relativePath())) {
                targets.addAll(globChildren(entry));
            } else {
                Path path = resolve(entry.moduleId(), entry.relativePath());
                if (readableYaml(path)) {
                    targets.add(new TargetFile(entry.moduleId(), entry.relativePath(), entry.kind(), path));
                }
            }
        }
        return targets;
    }

    private List<TargetFile> globChildren(WebConsoleRegistry.WebRegisteredFileEntry entry) throws IOException {
        String baseDir = extractBaseDir(entry.relativePath());
        String extension = extractExtension(entry.relativePath());
        Path root = moduleRoot(entry.moduleId());
        Path dir = WebPathSecurity.resolveInside(root, baseDir);
        if (dir == null || !Files.isDirectory(dir)) {
            return List.of();
        }
        List<TargetFile> result = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(dir)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> extension.isBlank() || path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(extension))
                    .filter(this::readableYaml)
                    .sorted(Comparator.comparing(Path::toString))
                    .forEach(path -> {
                        String relative = root.relativize(path).toString().replace('\\', '/');
                        if (!isAliasFile(entry.moduleId(), relative)) {
                            result.add(new TargetFile(entry.moduleId(), relative, entry.kind(), path));
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
        java.util.regex.Matcher matcher = pattern.matcher(content);
        String replaced = matcher.replaceAll(java.util.regex.Matcher.quoteReplacement(newToken));
        return new ReplaceResult(replaced, countMatches(matcher));
    }

    private ReplaceResult replaceYamlIdLine(String content, String oldId, String newId) {
        Pattern pattern = Pattern.compile("(?m)^(\\s*id\\s*:\\s*)([\\\"']?)" + Pattern.quote(oldId) + "\\2(\\s*)$");
        java.util.regex.Matcher matcher = pattern.matcher(content);
        String replaced = matcher.replaceAll("$1$2" + java.util.regex.Matcher.quoteReplacement(newId) + "$2$3");
        return new ReplaceResult(replaced, countMatches(matcher));
    }

    private int countMatches(java.util.regex.Matcher matcher) {
        matcher.reset();
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private void writeBackup(TargetFile target, String content) throws IOException {
        Path backupRoot = plugin.getDataFolder().toPath().resolve("migration-backups").resolve(Long.toString(Instant.now().toEpochMilli()));
        Path backup = backupRoot.resolve(target.moduleId()).resolve(target.relativePath()).normalize();
        Files.createDirectories(backup.getParent());
        Files.writeString(backup, content == null ? "" : content, StandardCharsets.UTF_8);
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

    private Long expectedRevision(Map<String, Long> expectedRevisions, String moduleId, String path) {
        if (expectedRevisions == null || expectedRevisions.isEmpty()) {
            return null;
        }
        String normalizedModule = Texts.toStringSafe(moduleId);
        String normalizedPath = normalizeRelativePath(path);
        for (String key : List.of(
                normalizedModule + ":" + normalizedPath,
                normalizedModule + "/" + normalizedPath,
                normalizedModule.toLowerCase(Locale.ROOT) + ":" + normalizedPath,
                normalizedModule.toLowerCase(Locale.ROOT) + "/" + normalizedPath,
                normalizedPath
        )) {
            Long revision = expectedRevisions.get(key);
            if (revision != null) {
                return revision;
            }
        }
        return null;
    }

    private boolean readableYaml(Path path) {
        try {
            return path != null && Files.isRegularFile(path) && isYamlPath(path.getFileName().toString()) && Files.size(path) <= MAX_FILE_BYTES;
        } catch (IOException ignored) {
            return false;
        }
    }

    private Path resolve(String moduleId, String relativePath) {
        return WebPathSecurity.resolveInside(moduleRoot(moduleId), normalizeRelativePath(relativePath));
    }

    private Path moduleRoot(String moduleId) {
        return plugin.getDataFolder().toPath().getParent().resolve(moduleId).toAbsolutePath().normalize();
    }

    private Path aliasPath() {
        return plugin.getDataFolder().toPath().resolve(ALIAS_FILE).toAbsolutePath().normalize();
    }

    private boolean isYamlPath(String path) {
        String lower = Texts.toStringSafe(path).toLowerCase(Locale.ROOT);
        return lower.endsWith(".yml") || lower.endsWith(".yaml") || lower.contains("*.yml") || lower.contains("*.yaml");
    }

    private boolean isAliasFile(String moduleId, String path) {
        return plugin.getName().equalsIgnoreCase(Texts.toStringSafe(moduleId))
                && ALIAS_FILE.equalsIgnoreCase(normalizeRelativePath(path));
    }

    private boolean isGlobPath(String path) {
        return path != null && (path.contains("*") || path.contains("?"));
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

    private String extractExtension(String globPath) {
        int dotIndex = globPath.lastIndexOf("*.");
        return dotIndex < 0 ? "" : globPath.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private String normalizeRelativePath(String path) {
        return Texts.toStringSafe(path).replace('\\', '/').replaceAll("^/+", "");
    }

    private record TargetFile(String moduleId, String relativePath, String kind, Path path) {}
    private record Replacement(String content, int count) {}
    private record ReplaceResult(String content, int count) {}
    private record PlannedWrite(TargetFile target, String content, int replacements, Long expectedRevision) {}
    private record AliasWrite(String content, Long expectedRevision) {}
}
