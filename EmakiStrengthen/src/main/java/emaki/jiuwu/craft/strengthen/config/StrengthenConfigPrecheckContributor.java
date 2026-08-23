package emaki.jiuwu.craft.strengthen.config;

import static emaki.jiuwu.craft.corelib.api.config.precheck.ConfigPrecheckSeverity.ERROR;
import static emaki.jiuwu.craft.corelib.api.config.precheck.ConfigPrecheckSeverity.INFO;
import static emaki.jiuwu.craft.corelib.api.config.precheck.ConfigPrecheckSeverity.WARN;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import emaki.jiuwu.craft.corelib.CoreLibConfig;
import emaki.jiuwu.craft.corelib.config.precheck.AbstractModuleConfigPrecheckContributor;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckContext;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckIssue;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckResult;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.YamlDirectoryLoader;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;
import emaki.jiuwu.craft.strengthen.enhancement.EnhancementTargetVariables;
import emaki.jiuwu.craft.strengthen.enhancement.cost.MaterialSlotConfig;
import emaki.jiuwu.craft.strengthen.enhancement.cost.TargetCompareEnum;
import emaki.jiuwu.craft.strengthen.enhancement.pity.PityIsolationEnum;
import emaki.jiuwu.craft.strengthen.enhancement.pity.PityScopeEnum;
import emaki.jiuwu.craft.strengthen.enhancement.recipe.EnhancementRecipe;
import emaki.jiuwu.craft.strengthen.enhancement.recipe.EnhancementRecipeLoader;

public final class StrengthenConfigPrecheckContributor extends AbstractModuleConfigPrecheckContributor {

    private final EmakiStrengthenPlugin plugin;

    public StrengthenConfigPrecheckContributor(EmakiStrengthenPlugin plugin) {
        super("strengthen", plugin::messageService);
        this.plugin = plugin;
    }

    @Override
    public ConfigPrecheckResult check(CoreLibConfig config, ConfigPrecheckContext context) {
        List<ConfigPrecheckIssue> issues = new ArrayList<>();
        checkFile(new File(plugin.getDataFolder(), "config.yml"), "config.yml", issues);
        checkDirectory(new File(plugin.getDataFolder(), "recipes"), "recipes", issues);
        checkDirectory(new File(plugin.getDataFolder(), "enhancement_recipes"), "enhancement_recipes", issues);
        checkDirectory(new File(plugin.getDataFolder(), "gui"), "gui", issues);
        addLoaderIssues("recipes", plugin.recipeLoader() == null ? null : plugin.recipeLoader().issues(), issues);
        addLoaderIssues("enhancement_recipes",
                plugin.enhancementRecipeLoader() == null ? null : plugin.enhancementRecipeLoader().issues(), issues);
        checkEnhancementVariableContract(issues);
        checkForgeVariableKeys(issues);
        checkEnhancementRecipeContracts(issues);
        if (issues.isEmpty()) {
            addMessageIssue("config.yml", INFO, "passed", issues);
        }
        return new ConfigPrecheckResult(module(), issues);
    }

    private void checkEnhancementRecipeContracts(List<ConfigPrecheckIssue> issues) {
        EnhancementRecipeLoader loader = plugin.enhancementRecipeLoader();
        if (loader == null) {
            return;
        }
        for (Map.Entry<String, YamlDirectoryLoader.LoadedYamlEntry<EnhancementRecipe>> entry
                : loader.entries().entrySet()) {
            YamlDirectoryLoader.LoadedYamlEntry<EnhancementRecipe> loaded = entry.getValue();
            if (loaded == null || loaded.value() == null) {
                continue;
            }
            checkMaterialSlots(entry.getKey(), loaded, issues);
            checkPityTracks(entry.getKey(), loaded.value(), issues);
            checkModeProviderAgreement(entry.getKey(), loaded.value(), issues);
        }
    }

    private void checkMaterialSlots(String recipeId,
            YamlDirectoryLoader.LoadedYamlEntry<EnhancementRecipe> loaded,
            List<ConfigPrecheckIssue> issues) {
        List<Map<?, ?>> rawMaterials = loaded.configuration() == null
                ? null : loaded.configuration().getMapList("materials");
        if (rawMaterials == null) {
            return;
        }
        for (int index = 0; index < rawMaterials.size(); index++) {
            Map<?, ?> raw = rawMaterials.get(index);
            if (raw == null) {
                continue;
            }
            String slotLabel = "recipe '" + recipeId + "' material_" + (index + 1);
            Object compare = rawValue(raw, "target_compare");
            if (compare != null) {
                String token = Texts.toStringSafe(compare);
                if (!Texts.isBlank(token)
                        && TargetCompareEnum.fromStringOrDefault(token, TargetCompareEnum.NONE)
                                == TargetCompareEnum.NONE) {
                    addIssue("enhancement_recipes", ERROR, slotLabel + " declares unknown target_compare '"
                            + token + "'; legal values are " + TargetCompareEnum.legalTokens(), issues);
                }
            }
            if (rawValue(raw, "required") != null && rawValue(raw, "optional") != null) {
                addIssue("enhancement_recipes", ERROR, slotLabel
                        + " declares both 'required' and 'optional'; 'optional' wins and 'required' is ignored",
                        issues);
            }
            if (rawValue(raw, "matcher") == null) {
                addIssue("enhancement_recipes", WARN, slotLabel
                        + " declares no matcher, so every supplied item satisfies this slot", issues);
            }
        }
        List<MaterialSlotConfig> slots = loaded.value().materials();
        if (!slots.isEmpty() && slots.stream().noneMatch(MaterialSlotConfig::required)) {
            addIssue("enhancement_recipes", WARN, "recipe '" + recipeId
                    + "' has materials but none of them is required, so the slots never block an attempt",
                    issues);
        }
    }

    private void checkPityTracks(String recipeId,
            EnhancementRecipe recipe,
            List<ConfigPrecheckIssue> issues) {
        List<EnhancementRecipe.PityConfig> tracks = recipe.pityTracks();
        if (tracks.isEmpty()) {
            return;
        }
        Set<String> identities = new LinkedHashSet<>();
        for (EnhancementRecipe.PityConfig track : tracks) {
            String identity = track.counter().scope().name() + "|" + Texts.lower(track.counter().group());
            if (!identities.add(identity)) {
                addIssue("enhancement_recipes", ERROR, "recipe '" + recipeId
                        + "' declares duplicate pity track '" + identity
                        + "'; duplicate tracks share one counter and double-count the same attempt", issues);
            }
            if (track.isolate().contains(PityIsolationEnum.LEVEL)
                    && track.counter().scope() == PityScopeEnum.PLAYER) {
                addIssue("enhancement_recipes", WARN, "recipe '" + recipeId + "' pity track '"
                        + track.counter().group()
                        + "' isolates by level under player scope, so the counter resets whenever the"
                        + " target level changes", issues);
            }
        }
    }

    private void checkModeProviderAgreement(String recipeId,
            EnhancementRecipe recipe,
            List<ConfigPrecheckIssue> issues) {
        String provider = Texts.lower(recipe.target().provider());
        if (Texts.isBlank(provider) || provider.equals(recipe.mode())) {
            return;
        }
        addIssue("enhancement_recipes", WARN, "recipe '" + recipeId + "' declares mode '" + recipe.mode()
                + "' but target provider '" + provider
                + "'; dispatch follows the provider and the mode only feeds the enhancement_mode placeholder,"
                + " so the two disagreeing is usually a configuration mistake", issues);
    }

    private static Object rawValue(Map<?, ?> raw, String key) {
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() != null && key.equals(String.valueOf(entry.getKey()))) {
                return entry.getValue();
            }
        }
        return null;
    }

    private void checkEnhancementVariableContract(List<ConfigPrecheckIssue> issues) {
        for (String violation : EnhancementVariableContract.violations()) {
            addIssue("enhancement_variables", ERROR, violation, issues);
        }
    }

    private void checkForgeVariableKeys(List<ConfigPrecheckIssue> issues) {
        Map<String, List<String>> contract = EnhancementTargetVariables.forgeAliasContract();
        Set<String> declared = new LinkedHashSet<>();
        for (Map.Entry<String, List<String>> entry : contract.entrySet()) {
            String path = entry.getKey();

            if (!path.startsWith("forge_") && !path.startsWith("forge.")) {
                addIssue("enhancement_variables", ERROR,
                        "forge variable path '" + path + "' must live under the forge prefix", issues);
            }

            declared.addAll(entry.getValue());
        }
        for (String required : List.of("forge_quality_id", "forge_quality_display",
                "forge_quality_multiplier", "forge_recipe_id")) {
            if (!declared.contains(required)) {
                addIssue("enhancement_variables", ERROR,
                        "canonical forge variable '" + required + "' is not produced by any alias path", issues);
            }
        }
        for (String legacy : List.of("quality_id", "quality_display", "quality_multiplier")) {
            if (!declared.contains(legacy)) {
                addIssue("enhancement_variables", ERROR,
                        "legacy forge variable '" + legacy + "' is no longer produced, breaking existing recipes",
                        issues);
            }
        }
        if (!EnhancementTargetVariables.forgeNamespace().equals("emakiforge")) {
            addIssue("enhancement_variables", ERROR,
                    "forge pdc namespace expected 'emakiforge' but was "
                            + EnhancementTargetVariables.forgeNamespace(), issues);
        }
    }
}
