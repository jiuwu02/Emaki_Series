package emaki.jiuwu.craft.strengthen.loader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.configuration.file.YamlConfiguration;

import emaki.jiuwu.craft.corelib.text.LogMessages;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;
import emaki.jiuwu.craft.strengthen.model.StrengthenRecipe;

public final class StrengthenRecipeLoader {

    private final EmakiStrengthenPlugin plugin;
    private final Object stateLock = new Object();
    private final Map<String, StrengthenRecipe> recipes = new LinkedHashMap<>();
    private final Map<String, String> materialCatalog = new LinkedHashMap<>();
    private final List<String> issues = new ArrayList<>();

    public StrengthenRecipeLoader(EmakiStrengthenPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        synchronized (stateLock) {
            recipes.clear();
            materialCatalog.clear();
            issues.clear();
            File directory = plugin.dataPath("recipes").toFile();
            if (!directory.exists()) {
                try {
                    YamlFiles.ensureDirectory(directory.toPath());
                } catch (IOException exception) {
                    issue("loader.directory_create_failed", Map.of(
                            "type", "strengthen-recipe",
                            "path", directory.getAbsolutePath()
                    ));
                    return;
                }
            }
            File[] files = directory.listFiles((dir, name) -> {
                String lower = Texts.lower(name);
                return lower.endsWith(".yml") || lower.endsWith(".yaml");
            });
            if (files == null || files.length == 0) {
                return;
            }
            java.util.Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
            for (File file : files) {
                loadFile(file);
            }
        }
    }

    public StrengthenRecipe get(String id) {
        synchronized (stateLock) {
            return Texts.isBlank(id) ? null : recipes.get(Texts.lower(id));
        }
    }

    public Map<String, StrengthenRecipe> all() {
        synchronized (stateLock) {
            return Map.copyOf(new LinkedHashMap<>(recipes));
        }
    }

    public List<StrengthenRecipe> ordered() {
        synchronized (stateLock) {
            return List.copyOf(recipes.values());
        }
    }

    public List<String> issues() {
        synchronized (stateLock) {
            return List.copyOf(issues);
        }
    }

    public Map<String, String> materialCatalog() {
        synchronized (stateLock) {
            return Map.copyOf(new LinkedHashMap<>(materialCatalog));
        }
    }

    public String resolveMaterialToken(String id) {
        synchronized (stateLock) {
            return Texts.isBlank(id) ? null : materialCatalog.get(Texts.lower(id));
        }
    }

    private void loadFile(File file) {
        try {
            YamlConfiguration configuration = YamlFiles.load(file);
            StrengthenRecipe recipe = StrengthenRecipe.fromConfig(configuration);
            if (recipe == null || Texts.isBlank(recipe.id())) {
                issue("loader.invalid_blank_id", Map.of(
                        "type", "strengthen-recipe",
                        "file", file.getName()
                ));
                return;
            }
            String recipeId = Texts.lower(recipe.id());
            if (recipes.containsKey(recipeId)) {
                issue("loader.duplicate_id", Map.of(
                        "type", "strengthen-recipe",
                        "id", recipe.id(),
                        "file", file.getName()
                ));
                return;
            }
            recipes.put(recipeId, recipe);
            indexMaterials(recipe);
        } catch (Exception exception) {
            issue("loader.load_failed", Map.of(
                    "type", "strengthen-recipe",
                    "file", file.getName(),
                    "error", String.valueOf(exception.getMessage())
            ));
        }
    }

    private void indexMaterials(StrengthenRecipe recipe) {
        if (recipe == null) {
            return;
        }
        for (StrengthenRecipe.StarStage stage : recipe.stars().values()) {
            if (stage == null) {
                continue;
            }
            for (StrengthenRecipe.StarStageMaterial material : stage.materials()) {
                if (material == null || Texts.isBlank(material.item())) {
                    continue;
                }
                materialCatalog.putIfAbsent(Texts.lower(material.item()), material.item());
            }
        }
    }

    private void issue(String key, Map<String, ?> replacements) {
        LogMessages messages = plugin.messageService();
        if (messages != null) {
            String rendered = messages.message(key, replacements);
            issues.add(rendered);
            messages.warning(key, replacements);
            return;
        }
        issues.add(key + " " + replacements);
    }
}
