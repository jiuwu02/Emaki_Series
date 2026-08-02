package emaki.jiuwu.craft.gem.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;

public final class GemItemDefinition {

    private final String id;
    private final List<ItemSourceRef> itemSources;
    private final List<String> slotGroups;
    private final List<String> loreContains;
    private final List<SocketSlot> slots;
    private final Set<Integer> defaultOpenSlots;
    private final Set<String> allowedGemTypes;
    private final int maxSameType;
    private final int maxSameId;
    private final GemItemObtainConfig obtainConfig;
    private final GuiSettings guiSettings;

    public GemItemDefinition(String id,
            List<ItemSourceRef> itemSources,
            List<String> slotGroups,
            List<String> loreContains,
            List<SocketSlot> slots,
            Set<Integer> defaultOpenSlots,
            Set<String> allowedGemTypes,
            int maxSameType,
            int maxSameId,
            GemItemObtainConfig obtainConfig,
            GuiSettings guiSettings) {
        this.id = Texts.lower(id);
        this.itemSources = itemSources == null ? List.of() : List.copyOf(itemSources);
        this.slotGroups = slotGroups == null ? List.of() : List.copyOf(slotGroups);
        this.loreContains = loreContains == null ? List.of() : List.copyOf(loreContains);
        this.slots = slots == null ? List.of() : slots.stream()
                .filter(slot -> slot != null && slot.index() >= 0)
                .sorted(Comparator.comparingInt(SocketSlot::index))
                .toList();
        this.defaultOpenSlots = defaultOpenSlots == null ? Set.of() : Set.copyOf(defaultOpenSlots);
        this.allowedGemTypes = allowedGemTypes == null ? Set.of() : Set.copyOf(allowedGemTypes);
        this.maxSameType = Math.max(0, maxSameType);
        this.maxSameId = Math.max(1, maxSameId);
        this.obtainConfig = obtainConfig == null ? GemItemObtainConfig.empty() : obtainConfig;
        this.guiSettings = guiSettings == null ? GuiSettings.defaults() : guiSettings;
    }

    public String id() {
        return id;
    }

    public List<ItemSourceRef> itemSources() {
        return itemSources;
    }

    public List<String> slotGroups() {
        return slotGroups;
    }

    public List<String> loreContains() {
        return loreContains;
    }

    public List<SocketSlot> slots() {
        return slots;
    }

    public Set<Integer> defaultOpenSlots() {
        return defaultOpenSlots;
    }

    public Set<String> allowedGemTypes() {
        return allowedGemTypes;
    }

    public int maxSameType() {
        return maxSameType;
    }

    public int maxSameId() {
        return maxSameId;
    }

    public GemItemObtainConfig obtainConfig() {
        return obtainConfig;
    }

    public GuiSettings guiSettings() {
        return guiSettings;
    }

    public SocketSlot slot(int index) {
        return slots.stream().filter(slot -> slot.index() == index).findFirst().orElse(null);
    }

    public Set<Integer> defaultOpenedSlotIndexes() {
        Set<Integer> validSlotIndexes = new LinkedHashSet<>();
        for (SocketSlot slot : slots) {
            validSlotIndexes.add(slot.index());
        }
        Set<Integer> indexes = new LinkedHashSet<>();
        for (Integer idx : defaultOpenSlots) {
            if (idx != null && validSlotIndexes.contains(idx)) {
                indexes.add(idx);
            }
        }
        return Set.copyOf(indexes);
    }

    public boolean allowsGemType(String gemType) {
        if (allowedGemTypes.isEmpty()) {
            return true;
        }
        String normalized = Texts.lower(gemType);
        return allowedGemTypes.contains(normalized) || allowedGemTypes.contains("any");
    }

    public static GemItemDefinition fromConfig(YamlSection section) {
        if (section == null) {
            return null;
        }
        String id = Texts.lower(section.getString("id"));
        if (Texts.isBlank(id)) {
            return null;
        }
        YamlSection match = section.getSection("match");
        Object itemSourcesRaw = match == null ? null : match.get("item_sources");
        Object slotGroupsRaw = match == null ? null : match.get("slot_groups");
        Object loreContainsRaw = match == null ? null : match.get("lore_contains");
        List<ItemSourceRef> itemSources = new ArrayList<>();
        for (Object raw : ConfigNodes.asObjectList(itemSourcesRaw)) {
            ItemSourceRef itemSource = ItemSourceUtil.parse(raw);
            if (itemSource != null) {
                itemSources.add(itemSource);
            }
        }
        List<String> slotGroups = Texts.asStringList(slotGroupsRaw).stream()
                .filter(Texts::isNotBlank)
                .map(Texts::lower)
                .toList();
        List<String> loreContains = Texts.asStringList(loreContainsRaw);
        List<SocketSlot> slots = parseSlots(section);
        YamlSection gui = section.getSection("gui");
        Set<String> allowedGemTypes = new LinkedHashSet<>();
        for (String value : section.getStringList("allowed_gem_types")) {
            if (Texts.isNotBlank(value)) {
                allowedGemTypes.add(Texts.lower(value));
            }
        }
        Set<Integer> defaultOpenSlots = new LinkedHashSet<>();
        for (Object raw : ConfigNodes.asObjectList(section.get("default_open_slots"))) {
            int idx = Numbers.tryParseInt(raw, -1);
            if (idx >= 0) {
                defaultOpenSlots.add(idx);
            }
        }
        return new GemItemDefinition(
                id,
                itemSources,
                slotGroups,
                loreContains,
                slots,
                defaultOpenSlots,
                allowedGemTypes,
                section.getInt("max_same_type", Integer.MAX_VALUE),
                section.getInt("max_same_id", 1),
                GemItemObtainConfig.fromConfig(section.get("obtain")),
                new GuiSettings(
                        gui == null ? "" : gui.getString("gem_template", ""),
                        gui == null ? "" : gui.getString("open_template", "")
                )
        );
    }

    private static List<SocketSlot> parseSlots(YamlSection section) {
        List<SocketSlot> slots = new ArrayList<>();
        List<java.util.Map<?, ?>> mapList = section.getMapList("slots");
        if (!mapList.isEmpty()) {
            for (java.util.Map<?, ?> map : mapList) {
                SocketSlot slot = SocketSlot.fromConfig(map);
                if (slot != null) {
                    slots.add(slot);
                }
            }
            return slots;
        }
        YamlSection slotsSection = section.getSection("slots");
        if (slotsSection != null) {
            for (String key : slotsSection.getKeys(false)) {
                SocketSlot slot = SocketSlot.fromConfig(slotsSection.getSection(key));
                if (slot != null) {
                    slots.add(slot);
                }
            }
        }
        return slots;
    }

    public record SocketSlot(int index, String type, String displayName) {

        public SocketSlot {
            index = Math.max(0, index);
            type = Texts.isBlank(type) ? "universal" : Texts.lower(type);
            displayName = Texts.isBlank(displayName) ? type : displayName;
        }

        public static SocketSlot fromConfig(Object raw) {
            int index = Numbers.tryParseInt(ConfigNodes.get(raw, "index"), -1);
            if (index < 0) {
                return null;
            }
            return new SocketSlot(
                    index,
                    ConfigNodes.string(raw, "type", "universal"),
                    ConfigNodes.string(raw, "display_name", "")
            );
        }
    }

    public record GuiSettings(String gemTemplate, String openTemplate) {

        public GuiSettings {
            gemTemplate = Texts.toStringSafe(gemTemplate).trim();
            openTemplate = Texts.toStringSafe(openTemplate).trim();
        }

        public static GuiSettings defaults() {
            return new GuiSettings("", "");
        }
    }
}
