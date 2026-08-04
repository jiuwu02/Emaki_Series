package emaki.jiuwu.craft.station.config;

import static emaki.jiuwu.craft.corelib.api.config.precheck.ConfigPrecheckSeverity.ERROR;
import static emaki.jiuwu.craft.corelib.api.config.precheck.ConfigPrecheckSeverity.INFO;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import emaki.jiuwu.craft.corelib.CoreLibConfig;
import emaki.jiuwu.craft.corelib.config.precheck.AbstractModuleConfigPrecheckContributor;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckContext;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckIssue;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckResult;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.station.EmakiStationPlugin;
import emaki.jiuwu.craft.station.gui.StationLayoutValidator;

/**
 * Reports EmakiStation's configuration health during CoreLib's precheck stage.
 *
 * <p>Layout validation runs here rather than at open time so an administrator learns about a broken layout at
 * startup, when they are looking at the console, instead of when a player clicks a station.
 */
public final class StationConfigPrecheckContributor extends AbstractModuleConfigPrecheckContributor {

    private final EmakiStationPlugin plugin;

    /**
     * Creates the contributor.
     *
     * @param plugin the owning plugin
     */
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
        checkDirectory(new File(dataFolder, "recipes"), "recipes", issues);
        checkDirectory(new File(dataFolder, "gui"), "gui", issues);
        addLoaderIssues("stations",
                plugin.stationLoader() == null ? null : plugin.stationLoader().issues(), issues);
        addLoaderIssues("recipes",
                plugin.recipeLoader() == null ? null : plugin.recipeLoader().issues(), issues);
        checkLayouts(issues);
        checkStationLayoutLinks(issues);
        if (issues.isEmpty()) {
            addMessageIssue("config.yml", INFO, "passed", issues);
        }
        return new ConfigPrecheckResult(module(), issues);
    }

    private void checkLayouts(List<ConfigPrecheckIssue> issues) {
        if (plugin.layoutLoader() == null) {
            return;
        }
        for (GuiTemplate template : plugin.layoutLoader().all().values()) {
            for (StationLayoutValidator.LayoutIssue issue : StationLayoutValidator.validate(template)) {
                addIssue("gui/" + issue.layoutId() + ".yml", ERROR,
                        issue.code() + (issue.detail().isEmpty() ? "" : ": " + issue.detail()), issues);
            }
        }
    }

    private void checkStationLayoutLinks(List<ConfigPrecheckIssue> issues) {
        if (plugin.stationLoader() == null || plugin.layoutLoader() == null) {
            return;
        }
        plugin.stationLoader().all().forEach((id, station) -> {
            if (plugin.layoutLoader().get(station.layoutId()) == null) {
                addIssue("stations/" + id + ".yml", ERROR,
                        "missing_layout: " + station.layoutId(), issues);
            }
        });
    }
}
