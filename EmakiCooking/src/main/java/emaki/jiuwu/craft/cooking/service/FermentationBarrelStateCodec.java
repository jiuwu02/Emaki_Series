package emaki.jiuwu.craft.cooking.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.api.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.MapYamlSection;
import emaki.jiuwu.craft.cooking.model.StationCoordinates;
import emaki.jiuwu.craft.cooking.model.StationType;
import org.bukkit.inventory.ItemStack;

final class FermentationBarrelStateCodec {

    Map<String, Object> serializeState(StationCoordinates coordinates, FermentationBarrelState state) {
        Map<String, Object> root = CookingRuntimeUtil.buildStateRoot(StationType.FERMENTATION_BARREL, coordinates);
        Map<String, Object> barrel = new LinkedHashMap<>();
        barrel.put("started_at_ms", state.startedAtMs());
        barrel.put("finish_at_ms", state.finishAtMs());
        barrel.put("fermenting", state.fermenting());
        barrel.put("completed", state.completed());
        barrel.put("active_recipe_id", state.activeRecipeId());
        if (state.playerUuid() != null) {
            barrel.put("player_uuid", state.playerUuid().toString());
        }
        if (Texts.isNotBlank(state.playerName())) {
            barrel.put("player_name", state.playerName());
        }
        root.put("fermentation_barrel", barrel);

        List<Map<String, Object>> guiSlots = new ArrayList<>();
        for (Map.Entry<Integer, String> entry : sortedSlots(state.slotSources()).entrySet()) {
            Map<String, Object> slot = new LinkedHashMap<>();
            slot.put("index", entry.getKey());
            slot.put("source", entry.getValue());
            slot.put("amount", Math.max(1, state.slotAmounts().getOrDefault(entry.getKey(), 1)));
            Map<String, Object> item = state.slotItemData(entry.getKey());
            if (item != null && !item.isEmpty()) {
                slot.put("item", item);
            }
            guiSlots.add(slot);
        }
        if (!guiSlots.isEmpty()) {
            root.put("gui_slots", guiSlots);
        }
        return root;
    }

    FermentationBarrelState readState(emaki.jiuwu.craft.corelib.api.yaml.YamlSection section) {
        FermentationBarrelState state = new FermentationBarrelState();
        if (section == null || !StationType.FERMENTATION_BARREL.folderName().equalsIgnoreCase(section.getString("station_type", ""))) {
            return state;
        }
        state.setStartedAtMs(CookingRuntimeUtil.parseLong(section.get("fermentation_barrel.started_at_ms"), 0L));
        state.setFinishAtMs(CookingRuntimeUtil.parseLong(section.get("fermentation_barrel.finish_at_ms"), 0L));
        state.setFermenting(section.getBoolean("fermentation_barrel.fermenting", false));
        state.setCompleted(section.getBoolean("fermentation_barrel.completed", false));
        state.setActiveRecipeId(section.getString("fermentation_barrel.active_recipe_id", ""));
        state.setPlayerContext(CookingRuntimeUtil.parseUuid(section.getString("fermentation_barrel.player_uuid", "")), section.getString("fermentation_barrel.player_name", ""));
        for (Map<?, ?> raw : section.getMapList("gui_slots")) {
            Map<String, Object> slot = MapYamlSection.normalizeMap(raw);
            int index = CookingRuntimeUtil.parseInteger(slot.get("index"), -1);
            String source = String.valueOf(slot.getOrDefault("source", ""));
            int amount = CookingRuntimeUtil.parseInteger(slot.get("amount"), 1);
            Map<String, Object> item = Map.of();
            Object rawItem = ConfigNodes.toPlainData(slot.get("item"));
            if (rawItem instanceof Map<?, ?> itemMap) {
                item = MapYamlSection.normalizeMap(itemMap);
            }
            state.setSlot(index, source, item, amount);
        }
        return state;
    }

    Map<String, Object> serializeItem(ItemStack itemStack) { return StoredItemCodec.serialize(itemStack); }
    ItemStack deserializeItem(Map<String, Object> serializedItem) { return StoredItemCodec.deserialize(serializedItem); }

    Map<Integer, String> sortedSlots(Map<Integer, String> slots) {
        Map<Integer, String> sorted = new LinkedHashMap<>();
        if (slots == null || slots.isEmpty()) {
            return sorted;
        }
        slots.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> sorted.put(entry.getKey(), entry.getValue()));
        return sorted;
    }
}
