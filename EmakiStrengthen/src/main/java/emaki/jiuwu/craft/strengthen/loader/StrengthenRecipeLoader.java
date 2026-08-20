package emaki.jiuwu.craft.strengthen.loader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.text.LogMessages;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;
import emaki.jiuwu.craft.strengthen.api.model.StrengthenRecipe;
import emaki.jiuwu.craft.strengthen.model.StarStageMaterialRule;
import emaki.jiuwu.craft.strengthen.model.StrengthenRecipeParser;

public final class StrengthenRecipeLoader {

    private final EmakiStrengthenPlugin plugin;
    private final Object stateLock = new Object();
    private final Map<String, StrengthenRecipe> recipes = new LinkedHashMap<>();
    private final Map<String, String> materialCatalog = new LinkedHashMap<>();
    private final Map<String, Map<String, StarStageMaterialRule>> materialRules = new LinkedHashMap<>();
    private final List<String> issues = new ArrayList<>();

    public StrengthenRecipeLoader(EmakiStrengthenPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        synchronized (stateLock) {
            recipes.clear();
            materialCatalog.clear();
            materialRules.clear();
            issues.clear();
            File directory = plugin.dataPath("recipes").toFile();
            if (!directory.exists()) {
                try {
                    YamlFiles.ensureDirectory(directory.toPath());
                } catch (IOException exception) {
                    issue("loader.directory_create_failed", Map.of(
                            "type", recipeType(),
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
            Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
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

    public @NotNull StarStageMaterialRule materialRule(String recipeId, int targetStar, String itemToken) {
        synchronized (stateLock) {
            Map<String, StarStageMaterialRule> rules = materialRules.get(Texts.lower(recipeId));
            if (rules == null) {
                return StarStageMaterialRule.inert();
            }
            StarStageMaterialRule rule = rules.get(StarStageMaterialRule.key(targetStar, itemToken));
            return rule == null ? StarStageMaterialRule.inert() : rule;
        }
    }

    public boolean hasMaterialRules() {
        synchronized (stateLock) {
            return !materialRules.isEmpty();
        }
    }

    private void loadFile(File file) {
        try {
            YamlSection configuration = YamlFiles.load(file);
            StrengthenRecipe recipe = StrengthenRecipeParser.parse(configuration);
            if (recipe == null || Texts.isBlank(recipe.id())) {
                issue("loader.invalid_blank_id", Map.of(
                        "type", recipeType(),
                        "file", file.getName()
                ));
                return;
            }
            String recipeId = Texts.lower(recipe.id());
            if (recipes.containsKey(recipeId)) {
                issue("loader.duplicate_id", Map.of(
                        "type", recipeType(),
                        "id", recipe.id(),
                        "file", file.getName()
                ));
                return;
            }
            recipes.put(recipeId, recipe);
            Map<String, StarStageMaterialRule> rules =
                    StrengthenRecipeParser.parseStageMaterialRules(configuration);
            if (!rules.isEmpty()) {
                materialRules.put(recipeId, rules);
            }
            indexMaterials(recipe);
        } catch (Exception exception) {
            issue("loader.load_failed", Map.of(
                    "type", recipeType(),
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

    private String recipeType() {
        return localized("loader.type.recipe", Map.of());
    }

    private void issue(String key, Map<String, ?> replacements) {
        Map<String, ?> safeReplacements = replacements == null ? Map.of() : replacements;
        issues.add(localized(key, safeReplacements));
        LogMessages messages = plugin.messageService();
        if (messages != null) {
            messages.warning(key, safeReplacements);
        }
    }

    private String localized(String key, Map<String, ?> replacements) {
        Map<String, ?> safeReplacements = replacements == null ? Map.of() : replacements;
        LogMessages messages = plugin.messageService();
        if (messages != null) {
            String rendered = messages.message(key, safeReplacements);
            if (!Texts.isBlank(rendered) && !key.equals(rendered.trim())) {
                return rendered;
            }
        }
        String safeKey = Texts.toStringSafe(key);
        int separator = safeKey.lastIndexOf('.');
        String token = (separator < 0 ? safeKey : safeKey.substring(separator + 1)).replace('_', ' ').trim();
        String label = Texts.isBlank(token)
                ? "Configuration loader issue"
                : Character.toUpperCase(token.charAt(0)) + token.substring(1);
        if (safeReplacements.isEmpty()) {
            return label;
        }
        StringBuilder builder = new StringBuilder(label).append(": ");
        boolean first = true;
        for (Map.Entry<String, ?> entry : safeReplacements.entrySet()) {
            if (!first) {
                builder.append(", ");
            }
            builder.append(entry.getKey()).append('=').append(Texts.toStringSafe(entry.getValue()));
            first = false;
        }
        return builder.toString();
    }
}
