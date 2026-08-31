package emaki.jiuwu.craft.cooking.loader;

import java.io.File;
import java.util.List;
import java.util.Map;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.YamlDirectoryLoader;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.cooking.model.RecipeDocument;
import emaki.jiuwu.craft.cooking.model.StationType;

abstract class BaseRecipeLoader extends YamlDirectoryLoader<RecipeDocument> {

    private final StationType stationType;
    private final List<String> requiredPaths;

    protected BaseRecipeLoader(JavaPlugin plugin, StationType stationType, List<String> requiredPaths) {
        super(plugin);
        this.stationType = stationType;
        this.requiredPaths = requiredPaths == null ? List.of() : List.copyOf(requiredPaths);
    }

    @Override
    protected final String directoryName() {
        return "recipes/" + stationType.folderName();
    }

    @Override
    protected final String typeName() {
        return localized("loader.type.recipe", Map.of(
                "station", localized("loader.station." + stationType.folderName())
        ));
    }

    @Override
    protected final RecipeDocument parse(File file, YamlSection configuration) {
        if (configuration == null || configuration.isEmpty()) {
            issue("loader.load_failed", Map.of(
                    "type", typeName(),
                    "file", file.getName(),
                    "error", localized("loader.error.empty_document")
            ));
            return null;
        }
        for (String path : requiredPaths) {
            if (!containsAny(configuration, path)) {
                issue("loader.load_failed", Map.of(
                        "type", typeName(),
                        "file", file.getName(),
                        "error", localized("loader.error.missing_field",
                                Map.of("path", path.replace("|", " / ")))
                ));
                return null;
            }
        }
        String id = configuration.getString("id", "");
        if (Texts.isBlank(id)) {
            onBlankId(file);
            return null;
        }
        return new RecipeDocument(
                id,
                configuration.getString("display_name", id),
                stationType,
                configuration.copy()
        );
    }

    private boolean containsAny(YamlSection configuration, String path) {
        for (String candidate : path.split("\\|")) {
            String trimmed = candidate.trim();
            if (Texts.isNotBlank(trimmed) && configuration.contains(trimmed)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected final String idOf(RecipeDocument value) {
        return value == null ? "" : value.id();
    }
}
