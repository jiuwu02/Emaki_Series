package emaki.jiuwu.craft.skills.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.skills.service.ActionBarService;
import emaki.jiuwu.craft.skills.service.CastModeService;

public final class CastModeKeyListener implements Listener {

    private final CastModeService castModeService;
    private final ActionBarService actionBarService;
    private final MessageService messageService;

    public CastModeKeyListener(CastModeService castModeService,
            ActionBarService actionBarService,
            MessageService messageService) {
        this.castModeService = castModeService;
        this.actionBarService = actionBarService;
        this.messageService = messageService;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onSwapHandItems(PlayerSwapHandItemsEvent event) {
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
