package emaki.jiuwu.craft.item.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import emaki.jiuwu.craft.corelib.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.web.preview.WebItemLayerPreviewProvider;
import emaki.jiuwu.craft.corelib.web.preview.WebItemLayerPreviewRegistry;
import emaki.jiuwu.craft.corelib.web.preview.WebItemLayerPreviewRequest;
import emaki.jiuwu.craft.corelib.web.preview.WebItemLayerPreviewResult;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;
import emaki.jiuwu.craft.item.EmakiItemPlugin;
import emaki.jiuwu.craft.item.model.EmakiItemDefinition;
import emaki.jiuwu.craft.item.model.EmakiItemDefinitionParser;
import emaki.jiuwu.craft.item.model.EquippedSetState;
import emaki.jiuwu.craft.item.model.ItemSetDefinition;
import emaki.jiuwu.craft.item.model.ItemSetMembership;

public final class EmakiItemLayerPreviewService {

    private static final List<String> BUILTIN_LAYER_IDS = List.of("strengthen", "gem");

    private final EmakiItemPlugin plugin;
    private final EmakiItemDefinitionParser parser;

    public EmakiItemLayerPreviewService(EmakiItemPlugin plugin) {
        this.plugin = plugin;
        this.parser = new EmakiItemDefinitionParser(plugin.getLogger());
    }

    public Map<String, Object> preview(String content, String fallbackId, Map<String, Object> layerOptions) {
        List<Map<String, Object>> warnings = new ArrayList<>();
        EmakiItemDefinition definition = parseDefinition(content, fallbackId, warnings);
        String itemId = definition == null ? Texts.normalizeId(fallbackId) : definition.id();
        ItemStack base = definition == null ? null : plugin.itemFactory().rebuildBase(definition, 1);
        Map<String, Object> setPreview = applySetPreview(definition, base);
        ItemStack current = base;
        Map<String, Object> basePreview = itemPreview("base", base != null, "", base, Map.of("itemId", itemId, "setPreview", setPreview));
        List<Map<String, Object>> layers = new ArrayList<>();
        Map<String, WebItemLayerPreviewProvider> providers = providersById();
        for (String id : BUILTIN_LAYER_IDS) {
            WebItemLayerPreviewProvider provider = providers.remove(id);
            if (provider == null) {
                layers.add(unregisteredLayer(id));
                continue;
            }
            WebItemLayerPreviewResult result = previewProvider(provider, itemId, base, current, layerOptionsFor(layerOptions, id));
            if (result.available() && result.itemStack() != null) {
                current = result.itemStack();
            }
            layers.add(result.toLayerMap(itemPreview(id, result.available(), result.reason(), result.itemStack(), result.details())));
        }
        for (WebItemLayerPreviewProvider provider : providers.values()) {
            WebItemLayerPreviewResult result = previewProvider(provider, itemId, base, current, layerOptionsFor(layerOptions, provider.id()));
            if (result.available() && result.itemStack() != null) {
                current = result.itemStack();
            }
            layers.add(result.toLayerMap(itemPreview(provider.id(), result.available(), result.reason(), result.itemStack(), result.details())));
        }
        return mapOf(
                "ok", true,
                "itemId", itemId,
                "base", basePreview,
                "layers", layers,
                "availableLayers", layers.stream().filter(layer -> Boolean.TRUE.equals(layer.get("available"))).map(layer -> Texts.toStringSafe(layer.get("id"))).toList(),
                "setPreview", setPreview,
                "final", itemPreview("final", current != null, "", current, Map.of("itemId", itemId, "setPreview", setPreview)),
                "warnings", warnings
        );
    }

    private Map<String, Object> applySetPreview(EmakiItemDefinition definition, ItemStack itemStack) {
        if (definition == null || itemStack == null || itemStack.getType().isAir()) {
            return mapOf("available", false, "reason", "当前物品无法生成套装预览。");
        }
        ItemSetMembership membership = definition.setMembership();
        if (membership == null || !membership.configured()) {
            return mapOf("available", false, "reason", "当前物品未绑定套装。");
        }
        ItemSetDefinition setDefinition = plugin.setLoader().get(membership.setId());
        if (setDefinition == null) {
            return mapOf(
                    "available", false,
                    "setId", membership.setId(),
                    "pieceId", membership.effectivePieceId(definition.id()),
                    "reason", "未找到套装定义。"
            );
        }
        String pieceId = membership.effectivePieceId(definition.id());
        EquippedSetState state = new EquippedSetState(setDefinition, java.util.Set.of(pieceId));
        List<String> setLore = new ItemSetLoreRenderer().render(state);
        appendSetLore(itemStack, setLore);
        return mapOf(
                "available", true,
                "setId", membership.setId(),
                "pieceId", pieceId,
                "active", state.activeCount(),
                "total", setDefinition.totalPieces(),
                "lore", setLore,
                "reason", ""
        );
    }

    private void appendSetLore(ItemStack itemStack, List<String> setLore) {
        if (itemStack == null || itemStack.getType().isAir() || setLore == null || setLore.isEmpty()) {
            return;
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null) {
            return;
        }
        List<String> mergedLore = new ArrayList<>(ItemTextBridge.loreLines(itemMeta));
        if (!mergedLore.isEmpty()) {
            mergedLore.add("");
        }
        mergedLore.addAll(setLore);
        ItemTextBridge.setLoreLines(itemMeta, mergedLore);
        itemStack.setItemMeta(itemMeta);
    }

    private WebItemLayerPreviewResult previewProvider(WebItemLayerPreviewProvider provider, String itemId, ItemStack base, ItemStack current, Map<String, Object> options) {
        try {
            return provider.preview(new WebItemLayerPreviewRequest(itemId, base, current == null ? base : current, options));
        } catch (RuntimeException exception) {
            return WebItemLayerPreviewResult.unavailable(provider.id(), provider.id() + " 预览失败：" + exception.getMessage(), Map.of(), Map.of());
        }
    }

    private Map<String, WebItemLayerPreviewProvider> providersById() {
        Map<String, WebItemLayerPreviewProvider> providers = new LinkedHashMap<>();
        for (WebItemLayerPreviewProvider provider : WebItemLayerPreviewRegistry.providers()) {
            providers.put(Texts.lower(provider.id()), provider);
        }
        return providers;
    }

    private Map<String, Object> layerOptionsFor(Map<String, Object> layerOptions, String id) {
        if (layerOptions == null || id == null) {
            return Map.of();
        }
        Object value = layerOptions.get(id);
        if (!(value instanceof Map<?, ?>)) {
            value = layerOptions.get(Texts.lower(id));
        }
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return result;
    }

    private Map<String, Object> unregisteredLayer(String id) {
        String pluginName = switch (id) {
            case "strengthen" -> "EmakiStrengthen";
            case "gem" -> "EmakiGem";
            default -> id;
        };
        return WebItemLayerPreviewResult.unavailable(id, pluginName + " 未加载。", Map.of(), Map.of())
                .toLayerMap(itemPreview(id, false, pluginName + " 未加载。", null, Map.of()));
    }

    private EmakiItemDefinition parseDefinition(String content, String fallbackId, List<Map<String, Object>> warnings) {
        try {
            EmakiItemDefinition parsed = parser.parse(YamlFiles.load(content == null ? "" : content), "web-preview");
            if (parsed != null) {
                return parsed;
            }
        } catch (RuntimeException exception) {
            warnings.add(warning("yaml_parse_failed", exception.getMessage()));
        }
        String id = Texts.normalizeId(fallbackId);
        return Texts.isBlank(id) ? null : plugin.itemLoader().get(id);
    }

    private Map<String, Object> itemPreview(String id, boolean available, String reason, ItemStack itemStack, Map<String, ?> details) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("available", available);
        map.put("reason", reason == null ? "" : reason);
        map.put("displayName", displayName(itemStack));
        map.put("lore", lore(itemStack));
        map.put("details", details == null ? Map.of() : details);
        return map;
    }

    private String displayName(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return "";
        }
        String text = ItemTextBridge.effectiveNameText(itemStack);
        return Texts.isBlank(text) ? MiniMessages.serialize(ItemTextBridge.effectiveName(itemStack)) : text;
    }

    private List<String> lore(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return List.of();
        }
        return ItemTextBridge.loreLines(itemStack.getItemMeta());
    }

    private Map<String, Object> warning(String type, String message) {
        return Map.of("type", Texts.toStringSafe(type), "message", Texts.toStringSafe(message));
    }

    private Map<String, Object> mapOf(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            result.put(String.valueOf(values[index]), values[index + 1]);
        }
        return result;
    }
}
