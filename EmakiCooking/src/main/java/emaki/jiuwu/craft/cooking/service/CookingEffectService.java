package emaki.jiuwu.craft.cooking.service;

import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.cooking.EmakiCookingPlugin;
import emaki.jiuwu.craft.cooking.model.StationType;

public final class CookingEffectService {

    private final EmakiCookingPlugin plugin;
    private final CookingSettingsService settingsService;

    public CookingEffectService(EmakiCookingPlugin plugin,
            CookingSettingsService settingsService) {
        this.plugin = plugin;
        this.settingsService = settingsService;
    }

    public void playActions(StationType stationType, String operation, Player player) {
        playActions(stationType, operation, player, null);
    }

    public void playActions(StationType stationType, String operation, Player player, Map<String, ?> placeholders) {
        if (stationType == null || operation == null || player == null) {
            return;
        }
        List<String> actions = settingsService.getStationActions(stationType, operation);
        if (actions == null || actions.isEmpty()) {
            return;
        }
        plugin.actionLines().run(actions, player,
                "cooking." + stationType.folderName() + "." + operation, false, placeholders, false);
    }
}
