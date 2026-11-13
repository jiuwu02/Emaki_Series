package emaki.jiuwu.craft.kits.join;

import emaki.jiuwu.craft.corelib.api.chat.ChatInputRequest;
import emaki.jiuwu.craft.corelib.api.chat.ChatInputResult;
import emaki.jiuwu.craft.corelib.chat.ChatInputService;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.kits.config.AppConfig;
import emaki.jiuwu.craft.kits.model.KitDefinition;
import emaki.jiuwu.craft.kits.service.KitService;
import emaki.jiuwu.craft.kits.store.KitStore;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

public final class InviteCodeService {

    private final Plugin plugin;
    private final ChatInputService chatInput;
    private final MessageService messages;
    private final KitService kits;
    private final KitStore store;
    private final Supplier<AppConfig> config;

    public InviteCodeService(Plugin plugin, ChatInputService chatInput, MessageService messages,
                             KitService kits, KitStore store, Supplier<AppConfig> config) {
        this.plugin = plugin;
        this.chatInput = chatInput;
        this.messages = messages;
        this.kits = kits;
        this.store = store;
        this.config = config;
    }

    public void prompt(Player newcomer, KitDefinition joinKit) {
        if (!joinKit.join().requireInviteCode()) {
            kits.onJoin(newcomer, true, newcomer.getWorld().getName(), null);
            return;
        }
        long window = joinKit.join().missedWindowSeconds();
        messages.send(newcomer, "join.invite_prompt", Map.of(
                "seconds", window,
                "kit", joinKit.displayName()));
        ChatInputRequest request = new ChatInputRequest(plugin, newcomer,
                Math.max(1L, window), List.of("cancel", "取消"), result -> handle(newcomer, joinKit, result));
        chatInput.await(request);
    }

    private void handle(Player newcomer, KitDefinition joinKit, ChatInputResult result) {
        if (!result.submitted()) {
            messages.send(newcomer, "join.invite_cancelled");
            return;
        }
        long window = joinKit.join().missedWindowSeconds();
        if (PlaytimeTracker.reached(newcomer, window)) {
            messages.send(newcomer, "join.missed_window", Map.of("seconds", window));
            return;
        }
        String raw = result.text() == null ? "" : result.text().trim();
        UUID inviterId = resolveInviter(raw, joinKit.join().inviteCodeType());
        if (inviterId == null) {
            messages.send(newcomer, "join.invalid_invite_code", Map.of("code", raw));
            return;
        }
        if (inviterId.equals(newcomer.getUniqueId())) {
            messages.send(newcomer, "join.invite_self");
            return;
        }
        boolean bound = store.read(s -> s.inviteBinds().containsKey(newcomer.getUniqueId()));
        if (bound) {
            messages.send(newcomer, "join.already_bound");
            return;
        }
        kits.onJoin(newcomer, true, newcomer.getWorld().getName(), inviterId);
        messages.send(newcomer, "join.invite_accepted", Map.of("kit", joinKit.displayName()));
    }

    private UUID resolveInviter(String raw, String codeType) {
        if ("name".equalsIgnoreCase(codeType)) {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(raw);
            return offline.hasPlayedBefore() ? offline.getUniqueId() : null;
        }
        try {
            return UUID.fromString(raw.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
