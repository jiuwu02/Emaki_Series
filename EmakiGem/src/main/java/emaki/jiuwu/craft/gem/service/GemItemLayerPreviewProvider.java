package emaki.jiuwu.craft.gem.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.web.preview.WebItemLayerPreviewProvider;
import emaki.jiuwu.craft.corelib.web.preview.WebItemLayerPreviewRequest;
import emaki.jiuwu.craft.corelib.web.preview.WebItemLayerPreviewResult;
import emaki.jiuwu.craft.gem.EmakiGemPlugin;
import emaki.jiuwu.craft.gem.model.GemDefinition;
import emaki.jiuwu.craft.gem.model.GemItemDefinition;
import emaki.jiuwu.craft.gem.model.GemItemInstance;
import emaki.jiuwu.craft.gem.model.GemResonanceDefinition;
import emaki.jiuwu.craft.gem.model.GemState;
import emaki.jiuwu.craft.gem.model.ResonancePatternEntry;

public final class GemItemLayerPreviewProvider implements WebItemLayerPreviewProvider {

    private static final String LAYER_ID = "gem";

    private final EmakiGemPlugin plugin;

    public GemItemLayerPreviewProvider(EmakiGemPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return LAYER_ID;
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public WebItemLayerPreviewResult preview(WebItemLayerPreviewRequest request) {
        ItemStack input = request == null ? null : request.currentItem();
        if (input == null || input.getType().isAir()) {
            return WebItemLayerPreviewResult.unavailable(LAYER_ID, "基础物品不可用。", Map.of(), Map.of());
        }
        GemItemDefinition itemDefinition = plugin.stateService().resolveItemDefinition(input);
        if (itemDefinition == null) {
            return WebItemLayerPreviewResult.unavailable(LAYER_ID, "没有任何宝石模板匹配当前 EmakiItem。", Map.of(), Map.of());
        }
        GemState state = plugin.stateService().resolveState(input, itemDefinition);
        Map<String, Object> requestOptions = request == null || request.options() == null ? Map.of() : request.options();
        PreviewSelection selection = resolveSelection(requestOptions, itemDefinition, state);
        GemState previewState = selection.gem() == null
                ? state
                : state.withAssignment(selection.slotIndex(), new GemItemInstance(selection.gem().id(), selection.level(), System.currentTimeMillis()));
        ItemStack preview = plugin.stateService().applyState(input.clone(), itemDefinition, previewState);
        if (preview == null || preview.getType().isAir()) {
            return WebItemLayerPreviewResult.unavailable(LAYER_ID, "宝石层预览重建失败。", details(itemDefinition, previewState, selection), options(itemDefinition, state, selection));
        }
        String reason = selection.gem() == null
                ? "已按真实宝石模板重建预览。"
                : "已按真实宝石镶嵌层重建预览。";
        return WebItemLayerPreviewResult.available(
                LAYER_ID,
                reason,
                preview,
                details(itemDefinition, previewState, selection),
                options(itemDefinition, state, selection),
                selected(selection)
        );
    }

    private PreviewSelection resolveSelection(Map<String, Object> requestOptions, GemItemDefinition itemDefinition, GemState state) {
        int slotIndex = Numbers.tryParseInt(requestOptions.get("slot"), -1);
        if (slotIndex < 0 || itemDefinition.slot(slotIndex) == null) {
            slotIndex = defaultSlot(itemDefinition, state);
        }
        GemDefinition gem = resolveGem(requestOptions, itemDefinition, slotIndex);
        boolean inlay = parseBoolean(requestOptions.get("inlay"), gem != null);
        if (!inlay) {
            return new PreviewSelection(slotIndex, null, 1, false);
        }
        int level = gem == null ? 1 : Numbers.clamp(Numbers.tryParseInt(requestOptions.get("level"), gem.level()), 1, gem.upgrade().maxLevel());
        return new PreviewSelection(slotIndex, gem, level, gem != null);
    }

    private boolean parseBoolean(Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        String normalized = Texts.lower(value).trim();
        if (normalized.isEmpty()) {
            return fallback;
        }
        if (List.of("true", "1", "yes", "y", "on", "inlay", "enabled").contains(normalized)) {
            return true;
        }
        if (List.of("false", "0", "no", "n", "off", "none", "disabled").contains(normalized)) {
            return false;
        }
        return fallback;
    }

    private int defaultSlot(GemItemDefinition itemDefinition, GemState state) {
        for (GemItemDefinition.SocketSlot slot : itemDefinition.slots()) {
            if (state != null && state.isOpened(slot.index()) && state.assignment(slot.index()) == null) {
                return slot.index();
            }
        }
        for (GemItemDefinition.SocketSlot slot : itemDefinition.slots()) {
            if (state == null || state.assignment(slot.index()) == null) {
                return slot.index();
            }
        }
        return itemDefinition.slots().isEmpty() ? -1 : itemDefinition.slots().getFirst().index();
    }

    private GemDefinition resolveGem(Map<String, Object> requestOptions, GemItemDefinition itemDefinition, int slotIndex) {
        String requestedGemId = Texts.lower(requestOptions.get("gemId"));
        if (Texts.isNotBlank(requestedGemId)) {
            GemDefinition requested = plugin.gemLoader().get(requestedGemId);
            if (compatible(itemDefinition, slotIndex, requested)) {
                return requested;
            }
        }
        return plugin.gemLoader().all().values().stream()
                .filter(gem -> compatible(itemDefinition, slotIndex, gem))
                .findFirst()
                .orElse(null);
    }

    private boolean compatible(GemItemDefinition itemDefinition, int slotIndex, GemDefinition gem) {
        if (itemDefinition == null || gem == null || !itemDefinition.allowsGemType(gem.gemType())) {
            return false;
        }
        GemItemDefinition.SocketSlot slot = itemDefinition.slot(slotIndex);
        return slot != null && gem.supportsSocketType(slot.type());
    }

    private Map<String, Object> details(GemItemDefinition itemDefinition, GemState state, PreviewSelection selection) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("templateId", itemDefinition.id());
        details.put("slotCount", itemDefinition.slots().size());
        details.put("defaultOpenSlotCount", itemDefinition.defaultOpenedSlotIndexes().size());
        details.put("openedSlotCount", state == null ? 0 : state.openedSlotIndexes().size());
        details.put("inlaidSlotCount", state == null ? 0 : state.socketAssignments().size());
        details.put("allowedGemTypes", itemDefinition.allowedGemTypes());
        details.put("activeResonances", activeResonances(state));
        if (selection != null && selection.gem() != null) {
            details.put("gemId", selection.gem().id());
            details.put("gemDisplayName", selection.gem().displayNameForLevel(selection.level()));
            details.put("gemType", selection.gem().gemType());
            details.put("gemLevel", selection.level());
            details.put("slot", selection.slotIndex());
        }
        return details;
    }

    private Map<String, Object> options(GemItemDefinition itemDefinition, GemState state, PreviewSelection selection) {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("templateId", itemDefinition.id());
        options.put("selectedSlot", selection == null ? -1 : selection.slotIndex());
        options.put("selectedGemId", selection == null || selection.gem() == null ? "" : selection.gem().id());
        options.put("selectedLevel", selection == null ? 1 : selection.level());
        options.put("selectedInlay", selection != null && selection.inlay());
        options.put("slots", slots(itemDefinition, state));
        options.put("gems", gems(itemDefinition, selection == null ? -1 : selection.slotIndex()));
        return options;
    }

    private List<Map<String, Object>> slots(GemItemDefinition itemDefinition, GemState state) {
        List<Map<String, Object>> slots = new ArrayList<>();
        for (GemItemDefinition.SocketSlot slot : itemDefinition.slots()) {
            slots.add(Map.of(
                    "index", slot.index(),
                    "type", slot.type(),
                    "displayName", slot.displayName(),
                    "opened", state != null && state.isOpened(slot.index()),
                    "assigned", state != null && state.assignment(slot.index()) != null
            ));
        }
        return slots;
    }

    private List<Map<String, Object>> gems(GemItemDefinition itemDefinition, int slotIndex) {
        List<Map<String, Object>> gems = new ArrayList<>();
        for (GemDefinition gem : plugin.gemLoader().all().values()) {
            if (!compatible(itemDefinition, slotIndex, gem)) {
                continue;
            }
            Map<String, Object> gemMap = new LinkedHashMap<>();
            gemMap.put("id", gem.id());
            gemMap.put("displayName", gem.displayName());
            gemMap.put("type", gem.gemType());
            gemMap.put("level", gem.level());
            gemMap.put("maxLevel", gem.upgrade().maxLevel());
            gemMap.put("actionCount", gem.inlaySuccessActions().size());
            gemMap.put("nameActionCount", actionCount(gem.nameActionsForLevel(gem.level())));
            gemMap.put("loreActionCount", actionCount(gem.loreActionsForLevel(gem.level())));
            gems.add(gemMap);
        }
        return gems;
    }

    private List<Map<String, Object>> activeResonances(GemState state) {
        GemResonanceService resonanceService = plugin.resonanceService();
        if (state == null || resonanceService == null || state.socketAssignments().isEmpty()) {
            return List.of();
        }
        List<GemResonanceService.GemEntry> entries = new ArrayList<>();
        for (GemItemInstance instance : state.socketAssignments().values()) {
            if (instance == null || Texts.isBlank(instance.gemId())) {
                continue;
            }
            GemDefinition gem = plugin.gemLoader().get(instance.gemId());
            if (gem != null) {
                entries.add(new GemResonanceService.GemEntry(gem, instance.level()));
            }
        }
        if (entries.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> summaries = new ArrayList<>();
        for (GemResonanceDefinition resonance : resonanceService.evaluateWithLevels(entries)) {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("id", resonance.id());
            summary.put("displayName", resonance.displayName());
            summary.put("exclusiveGroup", resonance.exclusiveGroup());
            summary.put("priority", resonance.priority());
            summary.put("mode", resonance.chain().mode());
            summary.put("patternText", patternText(resonance.chain().pattern()));
            summary.put("actionCount", resonance.effects().actions().size());
            summary.put("nameActionCount", actionCount(resonance.effects().nameActions()));
            summary.put("loreActionCount", actionCount(resonance.effects().loreActions()));
            summaries.add(summary);
        }
        return summaries;
    }

    private String patternText(List<ResonancePatternEntry> pattern) {
        if (pattern == null || pattern.isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (ResonancePatternEntry entry : pattern) {
            if (entry == null) {
                continue;
            }
            String target = Texts.isNotBlank(entry.id()) ? entry.id() : Texts.isNotBlank(entry.type()) ? entry.type() : "任意宝石";
            parts.add(entry.minLevel() > 0 ? target + "≥Lv." + entry.minLevel() : target);
        }
        return String.join(" + ", parts);
    }

    private int actionCount(Object actions) {
        if (actions == null) {
            return 0;
        }
        if (actions instanceof List<?> list) {
            return list.size();
        }
        if (actions instanceof Map<?, ?> map) {
            return map.isEmpty() ? 0 : 1;
        }
        return Texts.isBlank(actions) ? 0 : 1;
    }

    private Map<String, Object> selected(PreviewSelection selection) {
        if (selection == null || selection.gem() == null) {
            return Map.of("slot", selection == null ? -1 : selection.slotIndex(), "gemId", "", "level", 1, "inlay", false);
        }
        return Map.of("slot", selection.slotIndex(), "gemId", selection.gem().id(), "level", selection.level(), "inlay", selection.inlay());
    }

    private record PreviewSelection(int slotIndex, GemDefinition gem, int level, boolean inlay) {}
}
