package emaki.jiuwu.craft.dungeon.command;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.api.chat.ChatInputRequest;
import emaki.jiuwu.craft.corelib.api.chat.ChatInputResult;
import emaki.jiuwu.craft.corelib.api.text.MiniMessages;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.chat.ChatInputService;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.dungeon.challenge.ChallengeService;
import emaki.jiuwu.craft.dungeon.instance.DungeonInstance;
import emaki.jiuwu.craft.dungeon.orchestration.InstanceCoordinator;
import emaki.jiuwu.craft.dungeon.orchestration.InstanceRegistry;
import emaki.jiuwu.craft.dungeon.party.Party;
import emaki.jiuwu.craft.dungeon.party.PartyService;
import emaki.jiuwu.craft.dungeon.recovery.RecoveryService;
import emaki.jiuwu.craft.dungeon.snapshot.SnapshotManager;
import emaki.jiuwu.craft.dungeon.world.TemplateRepository;

public final class DungeonCommandRouter {

    private static final List<String> ROOT_SUBS = List.of(
            "help", "list", "enter", "leave", "cancel", "ready",
            "party", "stats", "rank", "globalchat", "editor", "admin"
    );
    private static final List<String> PARTY_SUBS = List.of(
            "create", "invite", "accept", "deny", "kick", "leave", "disband", "chat"
    );

    private final org.bukkit.plugin.Plugin plugin;
    private final MessageService messages;
    private final ChallengeService challenge;
    private final PartyService parties;
    private final RecoveryService recovery;
    private final SnapshotManager snapshots;
    private final ChatInputService chatInput;
    private final TemplateRepository templates;
    private final InstanceCoordinator coordinator;
    private final InstanceRegistry registry;

    public DungeonCommandRouter(org.bukkit.plugin.Plugin plugin,
            MessageService messages,
            ChallengeService challenge,
            PartyService parties,
            RecoveryService recovery,
            SnapshotManager snapshots,
            ChatInputService chatInput,
            TemplateRepository templates,
            InstanceCoordinator coordinator,
            InstanceRegistry registry) {
        this.plugin = plugin;
        this.messages = messages;
        this.challenge = challenge;
        this.parties = parties;
        this.recovery = recovery;
        this.snapshots = snapshots;
        this.chatInput = chatInput;
        this.templates = templates;
        this.coordinator = coordinator;
        this.registry = registry;
    }

    public void route(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            messages.send(sender, "command.help");
            return;
        }
        String sub = args[0];
        if ("list".equalsIgnoreCase(sub)) {
            routeList(sender);
            return;
        }
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player_only");
            return;
        }
        switch (Texts.lower(sub)) {
            case "enter" -> routeEnter(player, args);
            case "leave" -> routeLeave(player);
            case "cancel" -> challenge.cancelBy(player, "member_cancel");
            case "ready" -> challenge.readyBy(player);
            case "party" -> routeParty(player, args);
            default -> messages.send(sender, "command.unknown", Map.of("command", sub));
        }
    }

    private void routeList(CommandSender sender) {
        List<String> dungeons = templates.discoverDungeons();
        if (dungeons.isEmpty()) {
            messages.send(sender, "command.list.empty");
            return;
        }
        messages.send(sender, "command.list.header");
        for (String dungeonId : dungeons) {
            messages.send(sender, "command.list.entry", Map.of("dungeon", dungeonId));
        }
    }

    private void routeEnter(Player player, String[] args) {
        if (args.length < 2 || Texts.isBlank(args[1])) {
            messages.send(player, "command.enter.usage");
            return;
        }
        challenge.startEnter(player, args[1]);
    }

    private void routeLeave(Player player) {
        String pointer = snapshots.pointerOf(player.getUniqueId());
        DungeonInstance instance = pointer == null ? null : registry.get(pointer);
        if (pointer == null || instance == null) {
            messages.send(player, "command.leave.no_instance");
            return;
        }
        String instanceId = pointer;
        messages.send(player, "command.leave.confirm_hint");
        chatInput.await(new ChatInputRequest(plugin, player, 30L, List.of("取消", "cancel"), result -> {
            if (!result.submitted()) {
                return;
            }
            String text = Texts.lower(MiniMessages.plainText(result.text()));
            if (!"确认".equals(text) && !"confirm".equals(text) && !"yes".equals(text) && !"y".equals(text)) {
                messages.send(player, "command.leave.aborted");
                return;
            }
            completeLeave(player, instanceId);
        }));
    }

    private void completeLeave(Player player, String instanceId) {
        UUID playerId = player.getUniqueId();
        Party party = parties.partyOf(playerId);
        boolean soleMember = party != null && party.size() == 1;
        UUID partyId = party == null ? null : party.id();
        recovery.onExit(player, "voluntary");
        if (party != null) {
            parties.leave(player);
        }
        if (partyId != null) {
            challenge.onPartyChanged(partyId);
        }
        DungeonInstance instance = registry.get(instanceId);
        if (instance != null && (soleMember || noOnlineMemberInside(instance))) {
            coordinator.dispose(instanceId, "all_left");
        }
    }

    private boolean noOnlineMemberInside(DungeonInstance instance) {
        UUID partyId = instance.partyId();
        if (partyId == null) {
            return true;
        }
        Party party = parties.partyById(partyId);
        if (party == null) {
            return true;
        }
        for (UUID memberId : party.members()) {
            if (Bukkit.getPlayer(memberId) == null) {
                continue;
            }
            String pointer = snapshots.pointerOf(memberId);
            if (instance.instanceId().equals(pointer)) {
                return false;
            }
        }
        return true;
    }

    private void routeParty(Player player, String[] args) {
        if (args.length < 2) {
            messages.send(player, "command.party.usage");
            return;
        }
        String sub = Texts.lower(args[1]);
        switch (sub) {
            case "create" -> sendPartyResult(player, parties.create(player));
            case "invite" -> routePartyInvite(player, args);
            case "accept" -> sendPartyResult(player, parties.accept(player));
            case "deny" -> sendPartyResult(player, parties.deny(player));
            case "kick" -> routePartyKick(player, args);
            case "leave" -> {
                Party party = parties.partyOf(player.getUniqueId());
                UUID partyId = party == null ? null : party.id();
                sendPartyResult(player, parties.leave(player));
                if (partyId != null) {
                    challenge.onPartyChanged(partyId);
                }
            }
            case "disband" -> sendPartyResult(player, parties.disband(player));
            case "chat" -> {
                boolean enabled = parties.toggleChatChannel(player);
                messages.send(player, enabled ? "party.chat_enabled" : "party.chat_disabled");
            }
            default -> messages.send(player, "command.party.usage");
        }
    }

    private void routePartyInvite(Player player, String[] args) {
        if (args.length < 3 || Texts.isBlank(args[2])) {
            messages.send(player, "command.party.usage");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            messages.send(player, "party.result.invalid_target", Map.of("player", args[2]));
            return;
        }
        PartyService.InviteResult result = parties.invite(player, target.getUniqueId());
        if (result == PartyService.InviteResult.OK) {
            messages.send(player, "party.invite_sent", Map.of("player", target.getName()));
            messages.send(target, "party.invite_received", Map.of("player", player.getName()));
            return;
        }
        sendPartyResult(player, result);
    }

    private void routePartyKick(Player player, String[] args) {
        if (args.length < 3 || Texts.isBlank(args[2])) {
            messages.send(player, "command.party.usage");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        UUID targetId = target != null ? target.getUniqueId() : parseUuid(args[2]);
        if (targetId == null) {
            messages.send(player, "party.result.invalid_target", Map.of("player", args[2]));
            return;
        }
        sendPartyResult(player, parties.kick(player, targetId));
    }

    private UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private void sendPartyResult(Player player, PartyService.InviteResult result) {
        if (result == PartyService.InviteResult.OK) {
            return;
        }
        String key = "party.result." + Texts.lower(result.name());
        messages.send(player, key, Map.of());
    }

    public @NotNull Collection<String> suggest(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length <= 1) {
            List<String> visible = new ArrayList<>(ROOT_SUBS.size());
            for (String sub : ROOT_SUBS) {
                if ("editor".equals(sub) && !sender.hasPermission("emakidungeon.editor")) {
                    continue;
                }
                if ("admin".equals(sub) && !sender.hasPermission("emakidungeon.admin")) {
                    continue;
                }
                visible.add(sub);
            }
            return visible;
        }
        String root = Texts.lower(args[0]);
        if ("enter".equals(root) && args.length == 2) {
            return templates.discoverDungeons();
        }
        if ("party".equals(root)) {
            if (args.length == 2) {
                return PARTY_SUBS;
            }
            String partySub = Texts.lower(args[1]);
            if (("invite".equals(partySub) || "kick".equals(partySub)) && args.length == 3) {
                List<String> names = new ArrayList<>();
                for (Player online : Bukkit.getOnlinePlayers()) {
                    names.add(online.getName());
                }
                return names;
            }
        }
        return List.of();
    }
}
