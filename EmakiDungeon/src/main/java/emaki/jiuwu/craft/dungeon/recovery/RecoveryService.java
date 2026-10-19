package emaki.jiuwu.craft.dungeon.recovery;

import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.dungeon.config.DungeonConfig;
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
    private final Function<String, DungeonConfig> configOf;
    private final Consumer<UUID> teleportPermit;

    public RecoveryService(SnapshotManager snapshots,
            InstanceRegistry registry,
            PartyService parties,
            MessageService messages,
            DebugLogger debug,
            Function<String, DungeonConfig> configOf,
            Consumer<UUID> teleportPermit) {
        this.snapshots = snapshots;
        this.registry = registry;
        this.parties = parties;
        this.messages = messages;
        this.debug = debug;
        this.configOf = configOf;
        this.teleportPermit = teleportPermit;
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
        boolean rollbackInventory = rollbackInventoryOf(pointer);
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
            boolean rollbackInventory = rollbackInventoryOf(instanceId);
            teleportPermit.accept(memberId);
            snapshots.restoreOrigin(member, instanceId, rollbackInventory);
            snapshots.clearPointerAndSnapshot(memberId, instanceId);
            messages.send(member, "recovery.evacuated", Map.of("instance", instanceId));
        }
    }

    private boolean belongsTo(UUID playerId, DungeonInstance instance) {
        UUID partyId = instance.partyId();
        if (partyId == null) {
            return false;
        }
        Party party = parties.partyById(partyId);
        return party != null && party.contains(playerId);
    }

    private boolean rollbackInventoryOf(String dungeonId) {
        DungeonInstance instance = registry.get(dungeonId);
        String dungeon = instance == null ? null : instance.dungeonId();
        if (dungeon == null) {
            return true;
        }
        DungeonConfig config = configOf.apply(dungeon);
        return config == null || config.inventoryIsolation();
    }
}
