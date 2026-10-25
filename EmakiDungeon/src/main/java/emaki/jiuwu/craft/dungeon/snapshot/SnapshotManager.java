package emaki.jiuwu.craft.dungeon.snapshot;

import java.util.UUID;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.service.MessageService;
import java.util.Map;

public final class SnapshotManager {

    private final SnapshotsStore snapshots;
    private final PlayersDataStore playersData;
    private final MessageService messages;

    public SnapshotManager(SnapshotsStore snapshots, PlayersDataStore playersData, MessageService messages) {
        this.snapshots = snapshots;
        this.playersData = playersData;
        this.messages = messages;
    }

    public void captureInitial(Player player, String instanceId, String dungeonId, String partyId) {
        PlayerSnapshot snapshot = PlayerSnapshot.capture(player, instanceId, dungeonId, partyId);
        UUID playerId = player.getUniqueId();
        snapshots.writeAsync(snapshot, playerId);
        playersData.updateAsync(playerId, data -> {
            data.setRestoreInstance(instanceId);
            data.setPartyId(partyId);
        });
    }

    public void repack(Player player, String instanceId) {
        UUID playerId = player.getUniqueId();
        if (!snapshots.exists(instanceId, playerId)) {
            return;
        }
        PlayerSnapshot snapshot = snapshots.read(instanceId, playerId);
        if (snapshot == null) {
            return;
        }
        snapshot.captureState(player);
        snapshots.writeAsync(snapshot, playerId);
    }

    public PlayerSnapshot read(String instanceId, UUID playerId) {
        return snapshots.read(instanceId, playerId);
    }

    public boolean exists(String instanceId, UUID playerId) {
        return snapshots.exists(instanceId, playerId);
    }

    public void restoreOrigin(Player player, String instanceId, boolean rollbackInventory) {
        apply(player, instanceId, rollbackInventory, true);
    }

    public void restoreState(Player player, String instanceId, boolean rollbackInventory) {
        apply(player, instanceId, rollbackInventory, false);
    }

    public void clearPointer(UUID playerId) {
        playersData.updateAsync(playerId, data -> data.setRestoreInstance(""));
    }

    public void clearPointerAndSnapshot(UUID playerId, String instanceId) {
        clearPointer(playerId);
        snapshots.delete(instanceId, playerId);
    }

    public String pointerOf(UUID playerId) {
        String instanceId = playersData.read(playerId).restoreInstance();
        return instanceId == null || instanceId.isEmpty() ? null : instanceId;
    }

    private void apply(Player player, String instanceId, boolean rollbackInventory, boolean originLocation) {
        UUID playerId = player.getUniqueId();
        PlayerSnapshot snapshot = snapshots.read(instanceId, playerId);
        if (snapshot == null) {
            messages.send(player, "snapshot.missing", Map.of("instance", instanceId));
            return;
        }
        if (rollbackInventory) {
            snapshot.applyStateTo(player);
        } else {
            snapshot.applyVitalsTo(player);
        }
        if (originLocation) {
            snapshot.applyOriginLocation(player);
        } else {
            snapshot.applyStateLocation(player);
        }
    }
}
