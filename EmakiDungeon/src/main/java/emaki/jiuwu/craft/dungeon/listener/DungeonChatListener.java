package emaki.jiuwu.craft.dungeon.listener;

import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import io.papermc.paper.event.player.AsyncChatEvent;

import emaki.jiuwu.craft.corelib.api.text.MiniMessages;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.dungeon.party.Party;
import emaki.jiuwu.craft.dungeon.party.PartyService;

public final class DungeonChatListener implements Listener {

    private final PartyService parties;
    private final String format;

    public DungeonChatListener(PartyService parties, String format) {
        this.parties = parties;
        this.format = format;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!parties.inChatChannel(player.getUniqueId())) {
            return;
        }
        Party party = parties.partyOf(player.getUniqueId());
        if (party == null) {
            return;
        }
        event.setCancelled(true);
        String rendered = Texts.formatTemplate(format, Map.of(
                "player", player.getName(),
                "message", MiniMessages.plainText(event.message())
        ));
        var component = MiniMessages.parse(rendered);
        for (UUID memberId : party.members()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null) {
                member.sendMessage(component);
            }
        }
    }
}
