package emaki.jiuwu.craft.cooking.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.MapYamlSection;
import emaki.jiuwu.craft.cooking.model.StationCoordinates;
import emaki.jiuwu.craft.cooking.model.StationType;
import org.bukkit.inventory.ItemStack;

final class SteamerStateCodec {

    SteamerStateCodec() {
    }

    Map<String, Object> serializeState(StationCoordinates coordinates, SteamerState state) {
        Map<String, Object> root = CookingRuntimeUtil.buildStateRoot(StationType.STEAMER, coordinates);

        Map<String, Object> steamer = new LinkedHashMap<>();
        steamer.put("burning_until_ms", state.burningUntilMs());
        steamer.put("moisture", state.moisture());
        steamer.put("steam", state.steam());
        if (state.playerUuid() != null) {
            steamer.put("player_uuid", state.playerUuid().toString());
        }
        if (Texts.isNotBlank(state.playerName())) {
            steamer.put("player_name", state.playerName());
        }
        root.put("steamer", steamer);

        List<Map<String, Object>> guiSlots = new ArrayList<>();
        for (Map.Entry<Integer, String> entry : sortedSlots(state.slotSources()).entrySet()) {
            if (Texts.isBlank(entry.getValue())) {
                continue;
            }
            Map<String, Object> slot = new LinkedHashMap<>();
            slot.put("index", entry.getKey());
            slot.put("source", entry.getValue());
            Map<String, Object> item = state.slotItemData(entry.getKey());
            if (item != null && !item.isEmpty()) {
                slot.put("item", item);
            }
            guiSlots.add(slot);
        }
        if (!guiSlots.isEmpty()) {
            root.put("gui_slots", guiSlots);
        }

        List<Map<String, Object>> slotProgress = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : sortedProgress(state.slotProgress()).entrySet()) {
            Map<String, Object> progress = new LinkedHashMap<>();
            progress.put("index", entry.getKey());
            progress.put("progress", Math.max(0, entry.getValue()));
            slotProgress.add(progress);
        }
        if (!slotProgress.isEmpty()) {
            root.put("slot_progress", slotProgress);
        }
        return root;
    }

    SteamerState readState(emaki.jiuwu.craft.corelib.yaml.YamlSection section) {
        SteamerState state = new SteamerState();
        if (section == null || !StationType.STEAMER.folderName().equalsIgnoreCase(section.getString("station_type", ""))) {
            return state;
        }
        state.setBurningUntilMs(CookingRuntimeUtil.parseLong(section.get("steamer.burning_until_ms"), 0L));
        state.setMoisture(section.getInt("steamer.moisture", 0));
        state.setSteam(section.getInt("steamer.steam", 0));
        state.setPlayerContext(CookingRuntimeUtil.parseUuid(section.getString("steamer.player_uuid", "")), section.getString("steamer.player_name", ""));
        for (Map<?, ?> raw : section.getMapList("gui_slots")) {
            Map<String, Object> slot = MapYamlSection.normalizeMap(raw);
            int index = CookingRuntimeUtil.parseInteger(slot.get("index"), -1);
            String source = String.valueOf(slot.getOrDefault("source", ""));
            if (index >= 0 && Texts.isNotBlank(source)) {
                state.setSlotSource(index, source);
                Object rawItem = ConfigNodes.toPlainData(slot.get("item"));
                if (rawItem instanceof Map<?, ?> itemMap) {
                    state.setSlotItem(index, MapYamlSection.normalizeMap(itemMap));
                }
            }
        }
        for (Map<?, ?> raw : section.getMapList("slot_progress")) {
            Map<String, Object> progress = MapYamlSection.normalizeMap(raw);
            int index = CookingRuntimeUtil.parseInteger(progress.get("index"), -1);
            int value = CookingRuntimeUtil.parseInteger(progress.get("progress"), 0);
            if (index >= 0) {
                state.setProgress(index, value);
            }
        }
        return state;
    }

    Map<String, Object> serializeItem(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return Map.of();
        }
        Object plain = ConfigNodes.toPlainData(itemStack.serialize());
        if (!(plain instanceof Map<?, ?> itemMap)) {
            return Map.of();
        }
        return Map.copyOf(MapYamlSection.normalizeMap(itemMap));
    }

    ItemStack deserializeItem(Map<String, Object> serializedItem) {
        if (serializedItem == null || serializedItem.isEmpty()) {
            return null;
        }
        try {
            return ItemStack.deserialize(new LinkedHashMap<>(serializedItem));
        } catch (Exception _) {
            return null;
        }
    }

    Map<Integer, String> sortedSlots(Map<Integer, String> slots) {
        Map<Integer, String> sorted = new LinkedHashMap<>();
        if (slots == null || slots.isEmpty()) {
            return sorted;
        }
        slots.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> sorted.put(entry.getKey(), entry.getValue()));
        return sorted;
    }

    Map<Integer, Integer> sortedProgress(Map<Integer, Integer> progress) {
        Map<Integer, Integer> sorted = new LinkedHashMap<>();
        if (progress == null || progress.isEmpty()) {
            return sorted;
        }
        progress.entrySet().stream()
                .sorted(Comparator.comparingInt(Map.Entry::getKey))
                .forEach(entry -> sorted.put(entry.getKey(), entry.getValue()));
        return sorted;
    }
}
