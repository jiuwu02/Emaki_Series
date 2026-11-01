package emaki.jiuwu.craft.dungeon.flow;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.dungeon.flow.FlowModules.BossPointDef;
import emaki.jiuwu.craft.dungeon.flow.FlowModules.BlockInteractionDef;
import emaki.jiuwu.craft.dungeon.flow.FlowModules.BoundaryZoneDef;
import emaki.jiuwu.craft.dungeon.flow.FlowModules.CheckpointDef;
import emaki.jiuwu.craft.dungeon.flow.FlowModules.EffectZoneDef;
import emaki.jiuwu.craft.dungeon.flow.FlowModules.ExitPointDef;
import emaki.jiuwu.craft.dungeon.flow.FlowModules.FxDef;
import emaki.jiuwu.craft.dungeon.flow.FlowModules.GateDef;
import emaki.jiuwu.craft.dungeon.flow.FlowModules.HintDef;
import emaki.jiuwu.craft.dungeon.flow.FlowModules.ItemInteractionDef;
import emaki.jiuwu.craft.dungeon.flow.FlowModules.KillTargetDef;
import emaki.jiuwu.craft.dungeon.flow.FlowModules.MechanismDef;
import emaki.jiuwu.craft.dungeon.flow.FlowModules.OptionDef;
import emaki.jiuwu.craft.dungeon.flow.FlowModules.RespawnPointDef;
import emaki.jiuwu.craft.dungeon.flow.FlowModules.RewardDef;
import emaki.jiuwu.craft.dungeon.flow.FlowModules.RoomDef;
import emaki.jiuwu.craft.dungeon.flow.FlowModules.RoomTimerDef;
import emaki.jiuwu.craft.dungeon.flow.FlowModules.SpawnPointDef;
import emaki.jiuwu.craft.dungeon.flow.FlowModules.SpawnPointModuleDef;
import emaki.jiuwu.craft.dungeon.flow.FlowModules.TeleportZoneDef;
import emaki.jiuwu.craft.dungeon.flow.FlowModules.TempItemDef;
import emaki.jiuwu.craft.dungeon.flow.FlowModules.TriggerZoneDef;

public final class FlowDefinition {

    private SpawnPointDef spawnPoint;
    private final Map<String, CheckpointDef> checkpoints = new LinkedHashMap<>();
    private final Map<String, RespawnPointDef> respawnPoints = new LinkedHashMap<>();
    private final Map<String, ExitPointDef> exitPoints = new LinkedHashMap<>();
    private final Map<String, GateDef> gates = new LinkedHashMap<>();
    private final Map<String, SpawnPointModuleDef> spawnPoints = new LinkedHashMap<>();
    private final Map<String, TriggerZoneDef> triggerZones = new LinkedHashMap<>();
    private final Map<String, BlockInteractionDef> blockInteractions = new LinkedHashMap<>();
    private final Map<String, RewardDef> rewards = new LinkedHashMap<>();
    private final Map<String, BossPointDef> bossPoints = new LinkedHashMap<>();
    private final Map<String, OptionDef> options = new LinkedHashMap<>();
    private final Map<String, HintDef> hints = new LinkedHashMap<>();
    private final Map<String, TeleportZoneDef> teleportZones = new LinkedHashMap<>();
    private final Map<String, BoundaryZoneDef> boundaryZones = new LinkedHashMap<>();
    private final Map<String, EffectZoneDef> effectZones = new LinkedHashMap<>();
    private final Map<String, MechanismDef> mechanisms = new LinkedHashMap<>();
    private final Map<String, RoomTimerDef> roomTimers = new LinkedHashMap<>();
    private final Map<String, KillTargetDef> killTargets = new LinkedHashMap<>();
    private final Map<String, ItemInteractionDef> itemInteractions = new LinkedHashMap<>();
    private final Map<String, FxDef> fxPoints = new LinkedHashMap<>();
    private final Map<String, TempItemDef> tempItems = new LinkedHashMap<>();
    private final List<RoomDef> rooms = new ArrayList<>();

    public SpawnPointDef spawnPoint() {
        return spawnPoint;
    }

    public Map<String, CheckpointDef> checkpoints() {
        return checkpoints;
    }

    public Map<String, RespawnPointDef> respawnPoints() {
        return respawnPoints;
    }

    public Map<String, ExitPointDef> exitPoints() {
        return exitPoints;
    }

    public Map<String, GateDef> gates() {
        return gates;
    }

    public Map<String, SpawnPointModuleDef> spawnPoints() {
        return spawnPoints;
    }

    public Map<String, TriggerZoneDef> triggerZones() {
        return triggerZones;
    }

    public Map<String, BlockInteractionDef> blockInteractions() {
        return blockInteractions;
    }

    public Map<String, RewardDef> rewards() {
        return rewards;
    }

    public Map<String, BossPointDef> bossPoints() {
        return bossPoints;
    }

    public Map<String, OptionDef> options() {
        return options;
    }

    public Map<String, HintDef> hints() {
        return hints;
    }

    public Map<String, TeleportZoneDef> teleportZones() {
        return teleportZones;
    }

    public Map<String, BoundaryZoneDef> boundaryZones() {
        return boundaryZones;
    }

    public Map<String, EffectZoneDef> effectZones() {
        return effectZones;
    }

    public Map<String, MechanismDef> mechanisms() {
        return mechanisms;
    }

    public Map<String, RoomTimerDef> roomTimers() {
        return roomTimers;
    }

    public Map<String, KillTargetDef> killTargets() {
        return killTargets;
    }

    public Map<String, ItemInteractionDef> itemInteractions() {
        return itemInteractions;
    }

    public Map<String, FxDef> fxPoints() {
        return fxPoints;
    }

    public Map<String, TempItemDef> tempItems() {
        return tempItems;
    }

    public List<RoomDef> rooms() {
        return List.copyOf(rooms);
    }

    void setSpawnPoint(SpawnPointDef spawnPoint) {
        this.spawnPoint = spawnPoint;
    }

    void addRooms(List<RoomDef> roomDefs) {
        rooms.addAll(roomDefs);
    }

    public List<String> allModuleIds() {
        List<String> ids = new ArrayList<>();
        if (spawnPoint != null) {
            ids.add(spawnPoint.head().id());
        }
        addKeys(ids, checkpoints);
        addKeys(ids, respawnPoints);
        addKeys(ids, exitPoints);
        addKeys(ids, gates);
        addKeys(ids, spawnPoints);
        addKeys(ids, triggerZones);
        addKeys(ids, blockInteractions);
        addKeys(ids, rewards);
        addKeys(ids, bossPoints);
        addKeys(ids, options);
        addKeys(ids, hints);
        addKeys(ids, teleportZones);
        addKeys(ids, boundaryZones);
        addKeys(ids, effectZones);
        addKeys(ids, mechanisms);
        addKeys(ids, roomTimers);
        addKeys(ids, killTargets);
        addKeys(ids, itemInteractions);
        addKeys(ids, fxPoints);
        addKeys(ids, tempItems);
        for (RoomDef room : rooms) {
            ids.add(room.id());
        }
        return ids;
    }

    public boolean validate() {
        List<String> ids = allModuleIds();
        return ids.size() == ids.stream().distinct().count();
    }

    private static void addKeys(List<String> ids, Map<String, ?> source) {
        ids.addAll(source.keySet());
    }
}
