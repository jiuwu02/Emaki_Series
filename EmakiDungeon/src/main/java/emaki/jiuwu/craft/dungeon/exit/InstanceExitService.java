package emaki.jiuwu.craft.dungeon.exit;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.api.chat.ChatInputRequest;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.chat.ChatInputService;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.dungeon.challenge.ChallengeService;
import emaki.jiuwu.craft.dungeon.instance.DungeonInstance;
import emaki.jiuwu.craft.dungeon.orchestration.InstanceCoordinator;
import emaki.jiuwu.craft.dungeon.orchestration.InstanceRegistry;
import emaki.jiuwu.craft.dungeon.party.Party;
import emaki.jiuwu.craft.dungeon.party.PartyService;
import emaki.jiuwu.craft.dungeon.recovery.RecoveryService;
import emaki.jiuwu.craft.dungeon.snapshot.SnapshotManager;

import org.bukkit.plugin.Plugin;

public final class InstanceExitService {

    private static final List<String> CANCEL_KEYWORDS = List.of("取消", "cancel");

    private final Plugin plugin;
    private final ExecutionDispatcher dispatcher;
    private final MessageService messages;
    private final ChatInputService chatInput;
    private final ChallengeService challenge;
    private final PartyService parties;
    private final RecoveryService recovery;
    private final SnapshotManager snapshots;
    private final InstanceCoordinator coordinator;
    private final InstanceRegistry registry;

    public InstanceExitService(Plugin plugin,
            ExecutionDispatcher dispatcher,
            MessageService messages,
            ChatInputService chatInput,
            ChallengeService challenge,
            PartyService parties,
            RecoveryService recovery,
            SnapshotManager snapshots,
            InstanceCoordinator coordinator,
            InstanceRegistry registry) {
        this.plugin = plugin;
        this.dispatcher = dispatcher;
        this.messages = messages;
        this.chatInput = chatInput;
        this.challenge = challenge;
        this.parties = parties;
        this.recovery = recovery;
        this.snapshots = snapshots;
        this.coordinator = coordinator;
        this.registry = registry;
    }

    public void requestLeaveFromFlow(Player player, boolean needConfirm, String reason) {
        if (player == null) {
            return;
        }
        if (!needConfirm) {
            leave(player, reason);
            return;
        }
        String instanceId = snapshots.pointerOf(player.getUniqueId());
        if (instanceId == null) {
            messages.send(player, "command.leave.no_instance", Map.of());
            return;
        }
        messages.send(player, "command.leave.confirm_hint", Map.of());
        chatInput.await(new ChatInputRequest(plugin, player, 30L, CANCEL_KEYWORDS, result -> {
            if (!result.submitted() || !accepts(result.text())) {
                messages.send(player, "command.leave.aborted", Map.of());
                return;
            }
            leave(player, reason);
        }));
    }

    public boolean leave(Player player, String reason) {
        if (player == null) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        String instanceId = snapshots.pointerOf(playerId);
        if (instanceId == null) {
            messages.send(player, "command.leave.no_instance", Map.of());
            return false;
        }
        awaitConfirmThenLeave(player, instanceId, reason);
        return true;
    }

    private void awaitConfirmThenLeave(Player player, String instanceId, String reason) {
        UUID playerId = player.getUniqueId();
        Party party = parties.partyOf(playerId);
        boolean soleMember = party != null && party.size() == 1;
        UUID partyId = party == null ? null : party.id();
        recovery.onExit(player, reason);
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

    private boolean accepts(String raw) {
        String input = Texts.lower(Texts.trim(raw));
        return "确认".equals(input) || "confirm".equals(input)
                || "yes".equals(input) || "y".equals(input);
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
            if (plugin.getServer().getPlayer(memberId) == null) {
                continue;
            }
            if (instance.instanceId().equals(snapshots.pointerOf(memberId))) {
                return false;
            }
        }
        return true;
    }
}
