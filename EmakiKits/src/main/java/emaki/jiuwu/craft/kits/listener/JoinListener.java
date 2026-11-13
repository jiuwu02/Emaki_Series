package emaki.jiuwu.craft.kits.listener;

import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.kits.config.AppConfig;
import emaki.jiuwu.craft.kits.join.InviteCodeService;
import emaki.jiuwu.craft.kits.model.KitDefinition;
import emaki.jiuwu.craft.kits.model.KitType;
import emaki.jiuwu.craft.kits.service.KitService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.function.Supplier;

public final class JoinListener implements Listener {

    private final KitService service;
    private final InviteCodeService inviteCodes;
    private final MessageService messages;
    private final Supplier<AppConfig> config;

    public JoinListener(KitService service, InviteCodeService inviteCodes,
                        MessageService messages, Supplier<AppConfig> config) {
        this.service = service;
        this.inviteCodes = inviteCodes;
        this.messages = messages;
        this.config = config;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        boolean firstJoin = !event.getPlayer().hasPlayedBefore();
        String world = event.getPlayer().getWorld().getName();
        KitDefinition joinKit = findJoinKit();
        if (firstJoin && joinKit != null) {
            inviteCodes.prompt(event.getPlayer(), joinKit);
            return;
        }
        service.onJoin(event.getPlayer(), firstJoin, world, null);
    }

    private KitDefinition findJoinKit() {
        for (KitDefinition def : config.get().kits().values()) {
            if (def.type() == KitType.JOIN && def.enabled()) {
                return def;
            }
        }
        return null;
    }
}
