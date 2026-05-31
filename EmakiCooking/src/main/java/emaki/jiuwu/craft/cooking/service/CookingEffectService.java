package emaki.jiuwu.craft.cooking.service;

import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionExecutor;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;
import emaki.jiuwu.craft.cooking.EmakiCookingPlugin;
import emaki.jiuwu.craft.cooking.model.StationType;

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
