package emaki.jiuwu.craft.station.config;

import static emaki.jiuwu.craft.corelib.api.config.precheck.ConfigPrecheckSeverity.ERROR;
import static emaki.jiuwu.craft.corelib.api.config.precheck.ConfigPrecheckSeverity.INFO;

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

    /**
     * Validates every layout a station actually references, as the page it is referenced for.
     *
     * <p>A layout is only meaningful as one of the three pages, and which one it is comes from the station
     * that names it. Layout files nothing references are therefore left alone rather than validated against
     * a guessed page: that is what keeps a superseded file sitting in {@code gui/} from producing errors an
     * administrator cannot act on.
     *
     * @param issues the issue list to append to
     */
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
            reportMissingLayout(id, station.layoutId(), issues);
            reportMissingLayout(id, station.previewLayoutId(), issues);
            reportMissingLayout(id, station.queueLayoutId(), issues);
        });
    }

    private void reportMissingLayout(String stationId, String layoutId, List<ConfigPrecheckIssue> issues) {
        if (plugin.layoutLoader().get(layoutId) == null) {
            addIssue("stations/" + stationId + ".yml", ERROR, "missing_layout: " + layoutId, issues);
        }
    }
}
