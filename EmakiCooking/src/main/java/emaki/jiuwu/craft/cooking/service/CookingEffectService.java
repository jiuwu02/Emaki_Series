package emaki.jiuwu.craft.cooking.service;

import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionExecutor;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;
import emaki.jiuwu.craft.cooking.EmakiCookingPlugin;
import emaki.jiuwu.craft.cooking.model.StationType;

/**
 * 工位操作效果服务。
 * <p>
 * 从 config.yml 的 {@code stations.<type>.actions.<operation>} 读取动作列表，
 * 通过 CoreLib 的 ActionExecutor 执行（支持 playsound、particle 等所有内置动作）。
 * </p>
 */
public final class CookingEffectService {

    private final EmakiCookingPlugin plugin;
    private final ActionExecutor actionExecutor;
    private final CookingSettingsService settingsService;

    public CookingEffectService(EmakiCookingPlugin plugin,
            ActionExecutor actionExecutor,
            CookingSettingsService settingsService) {
        this.plugin = plugin;
        this.actionExecutor = actionExecutor;
        this.settingsService = settingsService;
    }

    /**
     * 播放指定工位操作的效果动作。
     *
     * @param stationType 工位类型
     * @param operation   操作名（如 "stir", "cut", "complete" 等）
     * @param player      触发操作的玩家
     */
    public void playActions(StationType stationType, String operation, Player player) {
        if (stationType == null || operation == null || player == null || actionExecutor == null) {
            return;
        }
        List<String> actions = settingsService.getStationActions(stationType, operation);
        if (actions == null || actions.isEmpty()) {
            return;
        }
        ActionContext context = ActionContext.create(plugin, player, "cooking." + stationType.folderName() + "." + operation, false);
        actionExecutor.executeAll(context, actions, false);
    }

    /**
     * 播放指定工位操作的效果动作，附带额外占位符。
     *
     * @param stationType  工位类型
     * @param operation    操作名
     * @param player       触发操作的玩家
     * @param placeholders 额外占位符
     */
    public void playActions(StationType stationType, String operation, Player player, Map<String, ?> placeholders) {
        if (stationType == null || operation == null || player == null || actionExecutor == null) {
            return;
        }
        List<String> actions = settingsService.getStationActions(stationType, operation);
        if (actions == null || actions.isEmpty()) {
            return;
        }
        ActionContext context = ActionContext.create(plugin, player, "cooking." + stationType.folderName() + "." + operation, false);
        if (placeholders != null && !placeholders.isEmpty()) {
            context = context.withPlaceholders(placeholders);
        }
        actionExecutor.executeAll(context, actions, false);
    }
}
