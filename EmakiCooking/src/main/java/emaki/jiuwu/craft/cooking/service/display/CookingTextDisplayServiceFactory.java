package emaki.jiuwu.craft.cooking.service.display;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.cooking.service.CookingSettingsService;
import emaki.jiuwu.craft.corelib.display.DisplayRuntimeSettings;
import emaki.jiuwu.craft.corelib.display.DisplayServiceFactory;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;

/**
 * 创建工位文本展示服务。
 *
 * <p>后端与可见距离仍由本模块的 {@code display_entities.*} 决定，
 * 底层实现来自 CoreLib，因此本模块不再自带任何实体或封包代码。
 */
public final class CookingTextDisplayServiceFactory {

    private CookingTextDisplayServiceFactory() {
    }

    public static CookingTextDisplayService create(JavaPlugin plugin,
            CookingSettingsService settingsService,
            ExecutionDispatcher executionDispatcher) {
        DisplayRuntimeSettings settings = DisplayRuntimeSettings.of(
                settingsService.displayEntitiesViewDistanceBlocks(),
                settingsService.displayEntitiesRefreshIntervalTicks());
        return new CookingTextDisplayService(DisplayServiceFactory.createTextService(
                plugin,
                settingsService.displayEntitiesBackend(),
                settings,
                executionDispatcher));
    }
}
