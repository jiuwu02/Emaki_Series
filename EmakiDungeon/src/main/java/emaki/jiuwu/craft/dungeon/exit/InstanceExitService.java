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

/**
 * 离开副本的统一执行链：命令 {@code /edg leave} 与流程「离开副本」功能模块共用同一套语义。
 *
 * <p>负责把玩家从当前副本实例移出（恢复原位置与背包、退出队伍、通知挑战服务），
 * 并在实例内无其他在线玩家时销毁该实例及其克隆世界。</p>
 */
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

    /**
     * 流程模块触发入口：按模块的 {@code need_confirm} 决定是否先做二次确认。
     *
     * @param player      触发玩家
     * @param needConfirm {@code true} 时先等待聊天确认
     * @param reason      退出原因，写入恢复/审计流水
     */
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

    /**
     * 直接把玩家移出当前副本实例，并在实例无人在内时销毁实例。
     *
     * @param player 目标玩家
     * @param reason 退出原因
     * @return {@code true} 表示玩家确实位于某副本实例内并已完成退出
     */
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
