package emaki.jiuwu.craft.dungeon.recovery;

import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.dungeon.instance.DungeonInstance;
import emaki.jiuwu.craft.dungeon.orchestration.InstanceRegistry;
import emaki.jiuwu.craft.dungeon.party.Party;
import emaki.jiuwu.craft.dungeon.party.PartyService;
import emaki.jiuwu.craft.dungeon.snapshot.SnapshotManager;

public final class RecoveryService {

    private final SnapshotManager snapshots;
    private final InstanceRegistry registry;
    private final PartyService parties;
    private final MessageService messages;
    private final DebugLogger debug;
    private final Consumer<UUID> teleportPermit;
    private volatile emaki.jiuwu.craft.dungeon.action.ActionBridge actionBridge;
    private volatile emaki.jiuwu.craft.dungeon.tempitem.TempItemManager tempItems;
    private volatile BiFunction<String, UUID, Boolean> stagingClear;

    public void bindStaging(BiFunction<String, UUID, Boolean> stagingClear) {
        this.stagingClear = stagingClear;
    }

    public void bindTempItems(emaki.jiuwu.craft.dungeon.tempitem.TempItemManager tempItems) {
        this.tempItems = tempItems;
    }

    public String pointerOf(UUID playerId) {
        return snapshots.pointerOf(playerId);
    }

    public RecoveryService(SnapshotManager snapshots,
            InstanceRegistry registry,
            PartyService parties,
            MessageService messages,
            DebugLogger debug,
            Consumer<UUID> teleportPermit) {
        this.snapshots = snapshots;
        this.registry = registry;
        this.parties = parties;
        this.messages = messages;
        this.debug = debug;
        this.teleportPermit = teleportPermit;
    }

    public void bindActionBridge(emaki.jiuwu.craft.dungeon.action.ActionBridge actionBridge) {
        this.actionBridge = actionBridge;
    }

    public void onQuit(Player player) {
        String pointer = snapshots.pointerOf(player.getUniqueId());
        if (pointer == null) {
            return;
        }
        DungeonInstance instance = registry.get(pointer);
        if (instance == null) {
            return;
        }
        snapshots.repack(player, pointer);
        debug.log("dungeon", player, "snapshot_repacked", Map.of("instance", pointer));
    }

    public void onJoin(Player player) {
        UUID playerId = player.getUniqueId();
        String pointer = snapshots.pointerOf(playerId);
        if (pointer == null) {
            return;
        }
        DungeonInstance instance = registry.get(pointer);
        if (instance != null && belongsTo(playerId, instance)) {
            teleportPermit.accept(playerId);
            snapshots.restoreState(player, pointer, true);
            messages.send(player, "recovery.reconnected", Map.of("instance", pointer));
            debug.log("dungeon", player, "reconnected_instance", Map.of("instance", pointer));
            return;
        }
        teleportPermit.accept(playerId);
        if (tempItems != null) {
            tempItems.removeAll(pointer, player);
        }
        snapshots.restoreOrigin(player, pointer, true);
        snapshots.clearPointerAndSnapshot(playerId, pointer);
        messages.send(player, "recovery.orphan_restored", Map.of("instance", pointer));
        debug.log("dungeon", player, "orphan_restored", Map.of("instance", pointer));
    }

    public void onExit(Player player, String reason) {
        UUID playerId = player.getUniqueId();
        String pointer = snapshots.pointerOf(playerId);
        if (pointer == null) {
            return;
        }
        boolean rollbackInventory = true;
        BiFunction<String, UUID, Boolean> clear = stagingClear;
        if (clear != null && Boolean.TRUE.equals(clear.apply(pointer, playerId))) {
            messages.send(player, "drop.voided", Map.of());
        }
        if (tempItems != null) {
            tempItems.removeAll(pointer, player);
        }
        teleportPermit.accept(playerId);
        snapshots.restoreOrigin(player, pointer, rollbackInventory);
        snapshots.clearPointerAndSnapshot(playerId, pointer);
        messages.send(player, "recovery.exited", Map.of(
                "instance", pointer,
                "reason", reason
        ));
        debug.log("dungeon", player, "exit_restored", Map.of(
                "instance", pointer,
                "reason", reason
        ));
        if (actionBridge != null) {
            actionBridge.dispatch("dungeon_exit", playerId, Map.of(
                    "instanceId", pointer,
                    "player", player.getName(),
                    "reason", reason
            ));
        }
        org.bukkit.Bukkit.getPluginManager().callEvent(
                new emaki.jiuwu.craft.dungeon.api.event.DungeonExitEvent(
                        pointer,
                        registry.get(pointer) == null ? "" : registry.get(pointer).dungeonId(),
                        player.getName(), reason));
    }

    public void evacuate(DungeonInstance instance) {
        String instanceId = instance.instanceId();
        UUID partyId = instance.partyId();
        if (partyId == null) {
            return;
        }
        Party party = parties.partyById(partyId);
        if (party == null) {
            return;
        }
        for (UUID memberId : party.members()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member == null) {
                continue;
            }
            String pointer = snapshots.pointerOf(memberId);
            if (pointer == null || !pointer.equals(instanceId)) {
                continue;
            }
            boolean rollbackInventory = true;
            BiFunction<String, UUID, Boolean> clear = stagingClear;
            if (clear != null && Boolean.TRUE.equals(clear.apply(instanceId, memberId))) {
                messages.send(member, "drop.voided", Map.of());
            }
            if (tempItems != null) {
                tempItems.removeAll(instanceId, member);
            }
            teleportPermit.accept(memberId);
            snapshots.restoreOrigin(member, instanceId, rollbackInventory);
            snapshots.clearPointerAndSnapshot(memberId, instanceId);
            messages.send(member, "recovery.evacuated", Map.of("instance", instanceId));
            if (actionBridge != null) {
                actionBridge.dispatch("dungeon_exit", memberId, Map.of(
                        "instanceId", instanceId,
                        "player", member.getName(),
                        "reason", "dispose"
                ));
            }
        }
    }

    private boolean belongsTo(UUID playerId, DungeonInstance instance) {
        UUID partyId = instance.partyId();
        if (partyId == null) {
            return false;
        }
        Party party = partyId == null ? null : parties.partyById(partyId);
        return party != null && party.contains(playerId);
    }
}
