package emaki.jiuwu.craft.structure.furniture;

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

public final class FurnitureSidecarRepository {

    private static final String DIRECTORY = "furniture";

    private final EmakiStructurePlugin plugin;
    private final Path directory;
    private final Supplier<AppConfig.FurnitureSettings> settings;

    private final Map<String, FurnitureSidecarParser.Result> cache = new ConcurrentHashMap<>();

    public FurnitureSidecarRepository(EmakiStructurePlugin plugin, Path dataDirectory,
            Supplier<AppConfig.FurnitureSettings> settings) {
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

    public FurnitureSidecarParser.Result resolve(StructureTemplate template) {
        if (!settings.get().enabled()) {
            return FurnitureSidecarParser.emptyResult(template.id());
        }
        return cache.computeIfAbsent(template.id(), id -> load(id, template));
    }

    private FurnitureSidecarParser.Result load(String templateId, StructureTemplate template) {
        File file = directory.resolve(templateId + ".yml").toFile();
        if (!file.isFile()) {
            return FurnitureSidecarParser.emptyResult(templateId);
        }
        String fileName = file.getName();
        try {
            YamlSection section = YamlFiles.load(file);
            FurnitureSidecarParser.Result result = FurnitureSidecarParser.parse(section, fileName,
                    templateId, template.sizeX(), template.sizeY(), template.sizeZ());
            report(result, fileName);
            return result;
        } catch (RuntimeException exception) {
            plugin.messageService().warning("error.furniture.rejected",
                    Map.of("file", fileName, "reason", String.valueOf(exception.getMessage())));
            return FurnitureSidecarParser.fileRejectedResult(templateId,
                    "error.furniture.read_failed",
                    Map.of("file", fileName, "error", String.valueOf(exception.getMessage())));
        }
    }

    private void report(FurnitureSidecarParser.Result result, String fileName) {
        if (result.fileRejected()) {
            plugin.messageService().warning("error.furniture.rejected", Map.of(
                    "file", fileName,
                    "reason", plugin.messageService().message(result.fileRejectKey(),
                            result.fileRejectPlaceholders())));
            return;
        }
        for (FurnitureSidecarParser.Issue issue : result.issues()) {
            plugin.messageService().warning("console.furniture_entry_issue", Map.of(
                    "file", fileName,
                    "index", issue.index(),
                    "reason", plugin.messageService().message(issue.reasonKey(),
                            issue.placeholders())));
        }
    }
}
