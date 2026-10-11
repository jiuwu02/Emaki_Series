package emaki.jiuwu.craft.structure.spawn;

import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.structure.EmakiStructurePlugin;
import emaki.jiuwu.craft.structure.config.AppConfig;
import emaki.jiuwu.craft.structure.template.StructureTemplate;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class SpawnSidecarRepository {

    private static final String DIRECTORY = "spawns";

    private final EmakiStructurePlugin plugin;
    private final Path directory;
    private final Supplier<AppConfig.SpawnSettings> settings;

    private final Map<String, SpawnSidecarParser.Result> cache = new ConcurrentHashMap<>();

    public SpawnSidecarRepository(EmakiStructurePlugin plugin, Path dataDirectory,
            Supplier<AppConfig.SpawnSettings> settings) {
        this.plugin = plugin;
        this.directory = dataDirectory.resolve(DIRECTORY);
        this.settings = settings;
    }

    public Path directory() {
        return directory;
    }

    public void reload() {
        cache.clear();
    }

    public SpawnSidecarParser.Result resolve(StructureTemplate template) {
        AppConfig.SpawnSettings current = settings.get();
        if (!current.enabled()) {
            return SpawnSidecarParser.emptyResult(template.id());
        }
        return cache.computeIfAbsent(template.id(), id -> load(id, template, current.maxPerGeneration()));
    }

    private SpawnSidecarParser.Result load(String templateId, StructureTemplate template,
            int maxPerGeneration) {
        File file = directory.resolve(templateId + ".yml").toFile();
        if (!file.isFile()) {
            return SpawnSidecarParser.emptyResult(templateId);
        }
        String fileName = file.getName();
        try {
            YamlSection section = YamlFiles.load(file);
            SpawnSidecarParser.Result result = SpawnSidecarParser.parse(section, fileName,
                    template.sizeX(), template.sizeY(), template.sizeZ(), maxPerGeneration);
            report(result, fileName);
            return result;
        } catch (RuntimeException exception) {
            plugin.messageService().warning("error.spawn.rejected",
                    Map.of("file", fileName, "reason", String.valueOf(exception.getMessage())));
            return SpawnSidecarParser.fileRejectedResult(templateId, "error.template.read_failed",
                    Map.of("file", fileName, "error", String.valueOf(exception.getMessage())));
        }
    }

    private void report(SpawnSidecarParser.Result result, String fileName) {
        if (result.fileRejected()) {
            plugin.messageService().warning("error.spawn.rejected", Map.of(
                    "file", fileName,
                    "reason", plugin.messageService().message(result.fileRejectKey(),
                            result.fileRejectPlaceholders())));
            return;
        }
        for (SpawnSidecarParser.Issue issue : result.issues()) {
            plugin.messageService().warning("console.spawn_entry_issue", Map.of(
                    "file", fileName,
                    "index", issue.index(),
                    "reason", plugin.messageService().message(issue.reasonKey(), issue.placeholders())));
        }
    }
}
