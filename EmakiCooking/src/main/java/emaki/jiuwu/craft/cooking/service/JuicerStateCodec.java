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

final class JuicerStateCodec {

    Map<String, Object> serializeState(StationCoordinates coordinates, JuicerState state) {
        Map<String, Object> root = CookingRuntimeUtil.buildStateRoot(StationType.JUICER, coordinates);
        Map<String, Object> juicer = new LinkedHashMap<>();
        if (state.playerUuid() != null) {
            juicer.put("player_uuid", state.playerUuid().toString());
        }
        if (Texts.isNotBlank(state.playerName())) {
            juicer.put("player_name", state.playerName());
        }
        if (state.hasFluid()) {
            juicer.put("fluid_id", state.fluidId());
            juicer.put("fluid_display_name", state.fluidDisplayName());
            juicer.put("fluid_amount_ml", state.fluidAmountMl());
        }
        root.put("juicer", juicer);

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

    JuicerState readState(emaki.jiuwu.craft.corelib.yaml.YamlSection section) {
        JuicerState state = new JuicerState();
        if (section == null || !StationType.JUICER.folderName().equalsIgnoreCase(section.getString("station_type", ""))) {
            return state;
        }
        state.setPlayerContext(CookingRuntimeUtil.parseUuid(section.getString("juicer.player_uuid", "")), section.getString("juicer.player_name", ""));
        state.setFluid(
                section.getString("juicer.fluid_id", ""),
                section.getString("juicer.fluid_display_name", ""),
                section.getInt("juicer.fluid_amount_ml", 0)
        );
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
        return StoredItemCodec.serialize(itemStack);
    }

    ItemStack deserializeItem(Map<String, Object> serializedItem) {
        return StoredItemCodec.deserialize(serializedItem);
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
