package emaki.jiuwu.craft.cooking.service.display;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.cooking.service.CookingSettingsService;
import emaki.jiuwu.craft.corelib.display.DisplayRuntimeSettings;
import emaki.jiuwu.craft.corelib.display.DisplayServiceFactory;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;

public final class CookingDisplayServiceFactory {

    private CookingDisplayServiceFactory() {
    }

    public static CookingDisplayService create(JavaPlugin plugin,
            CookingSettingsService settingsService,
            ExecutionDispatcher executionDispatcher) {
        DisplayRuntimeSettings settings = DisplayRuntimeSettings.of(
                settingsService.displayEntitiesViewDistanceBlocks(),
                settingsService.displayEntitiesRefreshIntervalTicks());
        return new CookingDisplayService(DisplayServiceFactory.createItemService(
                plugin,
                settingsService.displayEntitiesBackend(),
                settings,
                executionDispatcher));
    }
}
