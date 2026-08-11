package emaki.jiuwu.craft.cooking;

import java.util.Set;

import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.cooking.model.StationCoordinates;
import emaki.jiuwu.craft.cooking.model.StationType;
import emaki.jiuwu.craft.cooking.service.StationStateStore;
import emaki.jiuwu.craft.cooking.service.StationStateStore.StationStorageBackend;

final class CookingStationStorageListener implements Listener {

    private final EmakiCookingPlugin plugin;
    private final StationStateStore stateStore;

    CookingStationStorageListener(EmakiCookingPlugin plugin) {
        this.plugin = plugin;
        this.stateStore = plugin == null ? null : plugin.stationStateStore();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (stateStore == null || event == null || event.getChunk() == null) {
            return;
        }
        World world = event.getChunk().getWorld();
        Set<StationCoordinates> coordinates = stateStore.indexedCoordinatesInChunk(
                world,
                event.getChunk().getX(),
                event.getChunk().getZ()
        );
        for (StationCoordinates coordinate : coordinates) {
            StationType indexedType = stateStore.indexedStationType(coordinate);
            StationStorageBackend indexedBackend = stateStore.indexedBackend(coordinate);
            YamlSection state = stateStore.load(coordinate);
            if (state == null) {
                reportMissingState(coordinate, indexedType, indexedBackend);
                continue;
            }
            restore(coordinate, state);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        if (stateStore == null || event == null || event.getChunk() == null) {
            return;
        }
        World world = event.getChunk().getWorld();
        Set<StationCoordinates> coordinates = stateStore.indexedCoordinatesInChunk(
                world,
                event.getChunk().getX(),
                event.getChunk().getZ()
        );
        for (StationCoordinates coordinate : coordinates) {
            StationType type = stateStore.indexedStationType(coordinate);
            unload(type, coordinate);
        }
    }

    private void restore(StationCoordinates coordinates, YamlSection state) {
        StationType type = stationType(state);
        if (type == null || coordinates == null || plugin == null) {
            return;
        }
        switch (type) {
            case CHOPPING_BOARD -> plugin.choppingBoardRuntimeService().restoreStoredState(coordinates, state);
            case WOK -> plugin.wokRuntimeService().restoreStoredState(coordinates, state);
            case GRINDER -> plugin.grinderRuntimeService().restoreStoredState(coordinates, state);
            case STEAMER -> plugin.steamerRuntimeService().restoreStoredState(coordinates, state);
            case OVEN -> plugin.ovenRuntimeService().restoreStoredState(coordinates, state);
            case JUICER -> plugin.juicerRuntimeService().restoreStoredState(coordinates, state);
            case FERMENTATION_BARREL -> plugin.fermentationBarrelRuntimeService().restoreStoredState(coordinates, state);
        }
    }

    private void unload(StationType type, StationCoordinates coordinates) {
        if (type == null || coordinates == null || plugin == null) {
            return;
        }
        switch (type) {
            case CHOPPING_BOARD -> plugin.choppingBoardRuntimeService().unloadStoredState(coordinates);
            case WOK -> plugin.wokRuntimeService().unloadStoredState(coordinates);
            case GRINDER -> plugin.grinderRuntimeService().unloadStoredState(coordinates);
            case STEAMER -> plugin.steamerRuntimeService().unloadStoredState(coordinates);
            case OVEN -> plugin.ovenRuntimeService().unloadStoredState(coordinates);
            case JUICER -> plugin.juicerRuntimeService().unloadStoredState(coordinates);
            case FERMENTATION_BARREL -> plugin.fermentationBarrelRuntimeService().unloadStoredState(coordinates);
        }
    }

    private void reportMissingState(StationCoordinates coordinates, StationType indexedType, StationStorageBackend indexedBackend) {
        if (coordinates == null || indexedBackend == null || plugin == null || stateStore == null) {
            return;
        }
        if (indexedBackend == StationStorageBackend.BLOCK_PDC
                && stateStore.backendFor(coordinates.block()) == StationStorageBackend.YAML_FALLBACK) {
            String type = indexedType == null ? "unknown" : indexedType.folderName();
            plugin.getLogger().warning("Station restore report: lost_block_entity_replaced type=" + type
                    + " coordinate=" + coordinates.runtimeKey());
        }
    }

    private StationType stationType(YamlSection state) {
        String raw = Texts.normalizeId(state == null ? "" : state.getString("station_type", ""));
        if (raw.isBlank()) {
            return null;
        }
        for (StationType type : StationType.values()) {
            if (raw.equals(Texts.normalizeId(type.folderName())) || raw.equals(Texts.normalizeId(type.name()))) {
                return type;
            }
        }
        return null;
    }
}
