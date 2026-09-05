package emaki.jiuwu.craft.station.config;

import static emaki.jiuwu.craft.corelib.api.config.precheck.ConfigPrecheckSeverity.ERROR;
import static emaki.jiuwu.craft.corelib.api.config.precheck.ConfigPrecheckSeverity.INFO;
import static emaki.jiuwu.craft.corelib.api.config.precheck.ConfigPrecheckSeverity.WARN;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.CoreLibConfig;
import emaki.jiuwu.craft.corelib.config.precheck.AbstractModuleConfigPrecheckContributor;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckContext;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckIssue;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckResult;
import emaki.jiuwu.craft.corelib.config.precheck.ItemRequirementSchemaValidator;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.station.EmakiStationPlugin;
import emaki.jiuwu.craft.station.gui.StationLayoutValidator;
import emaki.jiuwu.craft.station.recipe.MaterialRequirement;
import emaki.jiuwu.craft.station.recipe.RecipeDefinition;

public final class StationConfigPrecheckContributor extends AbstractModuleConfigPrecheckContributor {

    private final EmakiStationPlugin plugin;

    public StationConfigPrecheckContributor(EmakiStationPlugin plugin) {
        super("station", plugin::messageService);
        this.plugin = plugin;
    }

    @Override
    public ConfigPrecheckResult check(CoreLibConfig config, ConfigPrecheckContext context) {
        List<ConfigPrecheckIssue> issues = new ArrayList<>();
        File dataFolder = plugin.getDataFolder();
        checkFile(new File(dataFolder, "config.yml"), "config.yml", issues);
        checkDirectory(new File(dataFolder, "stations"), "stations", issues);
        checkDirectory(new File(dataFolder, "stations_dismantle"), "stations_dismantle", issues);
        checkDirectory(new File(dataFolder, "recipes"), "recipes", issues);
        checkDirectory(new File(dataFolder, "recipes_dismantle"), "recipes_dismantle", issues);
        checkDirectory(new File(dataFolder, "gui"), "gui", issues);
        addLoaderIssues("stations",
                plugin.stationLoader() == null ? null : plugin.stationLoader().issues(), issues);
        addLoaderIssues("stations_dismantle",
                plugin.dismantleStationLoader() == null ? null : plugin.dismantleStationLoader().issues(),
                issues);
        addLoaderIssues("recipes",
                plugin.recipeLoader() == null ? null : plugin.recipeLoader().issues(), issues);
        addLoaderIssues("recipes_dismantle",
                plugin.dismantleRecipeLoader() == null ? null : plugin.dismantleRecipeLoader().issues(), issues);
        issues.addAll(ItemRequirementSchemaValidator.validateDirectory(module(),
                new File(dataFolder, "recipes"), "recipes"));
        issues.addAll(ItemRequirementSchemaValidator.validateDirectory(module(),
                new File(dataFolder, "recipes_dismantle"), "recipes_dismantle"));
        issues.addAll(ItemRequirementSchemaValidator.validateDirectory(module(),
                new File(dataFolder, "stations_dismantle"), "stations_dismantle"));
        checkLayouts(issues);
        checkStationLayoutLinks(issues);
        checkDismantleStationLayoutLinks(issues);
        checkStorageUnreachableMaterials(issues);
        if (issues.isEmpty()) {
            addMessageIssue("config.yml", INFO, "passed", issues);
        }
        return new ConfigPrecheckResult(module(), issues);
    }

    private void checkLayouts(List<ConfigPrecheckIssue> issues) {
        if (plugin.layoutLoader() == null || plugin.stationLoader() == null) {
            return;
        }
        Map<String, StationLayoutValidator.Page> pages = new LinkedHashMap<>();
        plugin.stationLoader().all().values().forEach(station -> {
            pages.putIfAbsent(station.layoutId(), StationLayoutValidator.Page.CATALOG);
            pages.putIfAbsent(station.previewLayoutId(), StationLayoutValidator.Page.PREVIEW);
            pages.putIfAbsent(station.queueLayoutId(), StationLayoutValidator.Page.QUEUE);
        });
        if (plugin.dismantleStationLoader() != null) {
            plugin.dismantleStationLoader().all().values().forEach(station ->
                    pages.putIfAbsent(station.layoutId(), StationLayoutValidator.Page.DISMANTLE));
        }
        pages.forEach((layoutId, page) -> {
            GuiTemplate template = plugin.layoutLoader().get(layoutId);
            if (template == null) {
                return;
            }
            for (StationLayoutValidator.LayoutIssue issue
                    : StationLayoutValidator.validate(template, page)) {
                addIssue("gui/" + issue.layoutId() + ".yml", ERROR,
                        issue.code() + (issue.detail().isEmpty() ? "" : ": " + issue.detail()), issues);
            }
        });
    }

    private void checkStationLayoutLinks(List<ConfigPrecheckIssue> issues) {
        if (plugin.stationLoader() == null || plugin.layoutLoader() == null) {
            return;
        }
        plugin.stationLoader().all().forEach((id, station) -> {
            reportMissingLayout("stations", id, station.layoutId(), issues);
            reportMissingLayout("stations", id, station.previewLayoutId(), issues);
            reportMissingLayout("stations", id, station.queueLayoutId(), issues);
        });
    }

    private void checkDismantleStationLayoutLinks(List<ConfigPrecheckIssue> issues) {
        if (plugin.dismantleStationLoader() == null || plugin.layoutLoader() == null) {
            return;
        }
        plugin.dismantleStationLoader().all().forEach((id, station) ->
                reportMissingLayout("stations_dismantle", id, station.layoutId(), issues));
    }

    private void reportMissingLayout(String dir, String stationId, String layoutId,
            List<ConfigPrecheckIssue> issues) {
        if (plugin.layoutLoader().get(layoutId) == null) {
            addIssue(dir + "/" + stationId + ".yml", ERROR, "missing_layout: " + layoutId, issues);
        }
    }

    private void checkStorageUnreachableMaterials(List<ConfigPrecheckIssue> issues) {
        if (plugin.recipeLoader() == null || plugin.stationLoader() == null) {
            return;
        }
        if (plugin.appConfig() == null || !plugin.appConfig().storageSettings().enabled()) {
            return;
        }
        for (RecipeDefinition recipe : plugin.recipeLoader().all().values()) {
            if (recipe == null || !storageBackedStation(recipe)) {
                continue;
            }
            for (int index = 0; index < recipe.requirements().size(); index++) {
                MaterialRequirement requirement = recipe.requirements().get(index);
                if (requirement != null && (requirement.hasMatcher() || requirement.sources().isEmpty())) {
                    addMessageIssue("recipes/" + recipe.id() + ".yml", WARN,
                            "storage_unreachable_material",
                            Map.of("recipe", recipe.id(), "index", index), issues);
                }
            }
        }
    }

    private boolean storageBackedStation(RecipeDefinition recipe) {
        return plugin.stationLoader().all().values().stream()
                .anyMatch(station -> station != null
                        && station.storageChannel()
                        && recipe.belongsTo(station.id()));
    }
}
