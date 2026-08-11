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

    /**
     * Runs the action lines configured for one station operation.
     *
     * @param stationType the station whose settings hold the lines
     * @param operation the operation key under that station
     * @param player the acting player
     */
    public void playActions(StationType stationType, String operation, Player player) {
        playActions(stationType, operation, player, null);
    }

    /**
     * Runs the action lines configured for one station operation, with extra placeholders.
     *
     * @param stationType the station whose settings hold the lines
     * @param operation the operation key under that station
     * @param player the acting player
     * @param placeholders values readable as {@code %var.name%}, may be {@code null}
     */
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
