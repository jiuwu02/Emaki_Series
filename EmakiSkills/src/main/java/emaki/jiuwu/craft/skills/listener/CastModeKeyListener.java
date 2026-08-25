package emaki.jiuwu.craft.skills.listener;

import java.util.function.Supplier;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.skills.config.AppConfig;
import emaki.jiuwu.craft.skills.service.ActionBarService;
import emaki.jiuwu.craft.skills.service.CastModeService;

public final class CastModeKeyListener implements Listener {

    private final CastModeService castModeService;
    private final ActionBarService actionBarService;
    private final MessageService messageService;
    private final Supplier<AppConfig> configSupplier;

    public CastModeKeyListener(CastModeService castModeService,
            ActionBarService actionBarService,
            MessageService messageService,
            Supplier<AppConfig> configSupplier) {
        this.castModeService = castModeService;
        this.actionBarService = actionBarService;
        this.messageService = messageService;
        this.configSupplier = configSupplier;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onSwapHandItems(PlayerSwapHandItemsEvent event) {
        AppConfig config = configSupplier.get();
        if (config != null && !config.castMode().enabled()) {
            return;
        }
        event.setCancelled(true);

        Player player = event.getPlayer();
        castModeService.toggleCastMode(player);

        boolean enabled = castModeService.isCastModeEnabled(player);
        messageService.send(player, enabled ? "cast_mode.enabled" : "cast_mode.disabled");
        if (actionBarService != null) {
            actionBarService.refreshPlayer(player);
        }
    }
}
