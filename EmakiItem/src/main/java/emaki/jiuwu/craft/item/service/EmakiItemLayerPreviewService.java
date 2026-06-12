package emaki.jiuwu.craft.item.service;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;
import emaki.jiuwu.craft.item.EmakiItemPlugin;
import emaki.jiuwu.craft.item.model.EmakiItemDefinition;
import emaki.jiuwu.craft.item.model.EmakiItemDefinitionParser;

public final class EmakiItemLayerPreviewService {

    private final EmakiItemPlugin plugin;
    private final EmakiItemDefinitionParser parser;

    public EmakiItemLayerPreviewService(EmakiItemPlugin plugin) {
        this.plugin = plugin;
        this.parser = new EmakiItemDefinitionParser(plugin.getLogger());
    }

    public Map<String, Object> preview(String content, String fallbackId) {
        List<Map<String, Object>> warnings = new ArrayList<>();
        EmakiItemDefinition definition = parseDefinition(content, fallbackId, warnings);
        ItemStack base = definition == null ? null : plugin.itemFactory().rebuildBase(definition, 1);
        Map<String, Object> basePreview = itemPreview("base", true, "", base, Map.of("itemId", definition == null ? Texts.normalizeId(fallbackId) : definition.id()));
        Map<String, Object> strengthen = strengthenLayer(base);
        Map<String, Object> gem = gemLayer(base);
        List<Map<String, Object>> layers = List.of(strengthen, gem);
        return mapOf(
                "ok", true,
                "itemId", definition == null ? Texts.normalizeId(fallbackId) : definition.id(),
                "base", basePreview,
                "layers", layers,
                "final", basePreview,
                "warnings", warnings
        );
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

    private Map<String, Object> strengthenLayer(ItemStack base) {
        Plugin strengthen = Bukkit.getPluginManager().getPlugin("EmakiStrengthen");
        if (strengthen == null || !strengthen.isEnabled()) {
            return unavailable("strengthen", "EmakiStrengthen 未加载。");
        }
        if (base == null || base.getType().isAir()) {
            return unavailable("strengthen", "基础物品不可用。");
        }
        try {
            Method recipeResolverMethod = strengthen.getClass().getMethod("recipeResolver");
            Object resolver = recipeResolverMethod.invoke(strengthen);
            Object resolved = resolver.getClass().getMethod("resolve", ItemStack.class, String.class).invoke(resolver, base, null);
            String recipeId = Texts.toStringSafe(resolved.getClass().getMethod("resolvedRecipeId").invoke(resolved));
            String source = Texts.toStringSafe(resolved.getClass().getMethod("baseSourceSignature").invoke(resolved));
            if (Texts.isBlank(recipeId)) {
                return unavailable("strengthen", "EmakiStrengthen 已加载，但没有任何强化配方匹配当前 EmakiItem。", Map.of("source", source));
            }
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("recipeId", recipeId);
            details.put("source", source);
            try {
                Object routePreviewService = strengthen.getClass().getMethod("routePreviewService").invoke(strengthen);
                details.put("route", routePreviewService.getClass().getMethod("preview", String.class).invoke(routePreviewService, recipeId));
            } catch (ReflectiveOperationException ignored) {
                // 旧构建没有暴露 routePreviewService 时仍可返回匹配状态。
            }
            return available("strengthen", "已匹配强化配方。", details);
        } catch (ReflectiveOperationException exception) {
            return unavailable("strengthen", "Strengthen 预览桥接失败：" + exception.getMessage());
        }
    }

    private Map<String, Object> gemLayer(ItemStack base) {
        Plugin gem = Bukkit.getPluginManager().getPlugin("EmakiGem");
        if (gem == null || !gem.isEnabled()) {
            return unavailable("gem", "EmakiGem 未加载。");
        }
        if (base == null || base.getType().isAir()) {
            return unavailable("gem", "基础物品不可用。");
        }
        try {
            Object matcher = gem.getClass().getMethod("itemMatcher").invoke(gem);
            Object definition = matcher.getClass().getMethod("matchEquipment", ItemStack.class).invoke(matcher, base);
            if (definition == null) {
                return unavailable("gem", "EmakiGem 已加载，但没有任何宝石模板匹配当前 EmakiItem。");
            }
            String templateId = Texts.toStringSafe(definition.getClass().getMethod("id").invoke(definition));
            Object slots = definition.getClass().getMethod("slots").invoke(definition);
            Object defaultOpenSlots = definition.getClass().getMethod("defaultOpenedSlotIndexes").invoke(definition);
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("templateId", templateId);
            if (slots instanceof List<?> list) {
                details.put("slotCount", list.size());
            }
            if (defaultOpenSlots instanceof java.util.Set<?> set) {
                details.put("defaultOpenSlotCount", set.size());
            }
            return available("gem", "已匹配宝石模板。", details);
        } catch (ReflectiveOperationException exception) {
            return unavailable("gem", "Gem 预览桥接失败：" + exception.getMessage());
        }
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

    private Map<String, Object> available(String id, String reason, Map<String, ?> details) {
        Map<String, Object> map = itemPreview(id, true, reason, null, details);
        map.put("status", "available");
        return map;
    }

    private Map<String, Object> unavailable(String id, String reason) {
        return unavailable(id, reason, Map.of());
    }

    private Map<String, Object> unavailable(String id, String reason, Map<String, ?> details) {
        Map<String, Object> map = itemPreview(id, false, reason, null, details);
        map.put("status", "unavailable");
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

    private Map<String, String> warning(String type, String message) {
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
