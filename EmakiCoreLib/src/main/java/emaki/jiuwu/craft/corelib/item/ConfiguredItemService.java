package emaki.jiuwu.craft.corelib.item;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.api.item.ConfiguredItemDefinition;
import emaki.jiuwu.craft.corelib.api.item.ItemBuildIssue;
import emaki.jiuwu.craft.corelib.api.item.ItemBuildIssueSeverity;
import emaki.jiuwu.craft.corelib.api.item.ItemBuildResult;
import emaki.jiuwu.craft.corelib.api.item.ItemComponentCapability;
import emaki.jiuwu.craft.corelib.api.item.ItemComponentPatch;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.cache.CacheManager;

public final class ConfiguredItemService {

    private static final String LORE_COMPONENT_ID = "minecraft:lore";

    private static final int LOGGED_ISSUES_LIMIT = 1024;

    private final Plugin plugin;
    private final ItemSourceService itemSourceService;
    private final ConfiguredItemParser parser = new ConfiguredItemParser();
    private final MinecraftComponentValueCodec codec = new MinecraftComponentValueCodec();
    private final MinecraftItemComponentCatalog catalog = new MinecraftItemComponentCatalog();
    private final PaperItemComponentBridge paperBridge = new PaperItemComponentBridge();
    private final List<ItemComponentCapability> capabilities = paperBridge.capabilities(catalog);
    private final CacheManager<String, Boolean> loggedIssues = new CacheManager<>(LOGGED_ISSUES_LIMIT, 0L);

    public ConfiguredItemService(Plugin plugin, ItemSourceService itemSourceService) {
        this.plugin = plugin;
        this.itemSourceService = itemSourceService == null ? new ItemSourceService() : itemSourceService;
    }

    public ConfiguredItemParser parser() {
        return parser;
    }

    public List<ItemComponentCapability> capabilities() {
        return capabilities;
    }

    public ItemBuildResult create(ConfiguredItemDefinition definition) {
        return create(definition, Map.of());
    }

    public ItemBuildResult create(ConfiguredItemDefinition definition, Map<String, ?> replacements) {
        List<ItemBuildIssue> issues = new ArrayList<>();
        if (definition == null) {
            return finish(null, List.of(ItemBuildIssue.error(null, "配置物品定义为 null。")));
        }
        ConfiguredItemDefinition resolved = resolve(definition, replacements);
        ItemSourceRef source = ItemSourceUtil.parse(resolved.source());
        if (source == null) {
            return finish(null, List.of(ItemBuildIssue.error(null, "配置物品来源缺失或无效。")));
        }

        ItemStack itemStack = source.vanilla()
                ? createVanilla(source, resolved, issues)
                : createThirdParty(source, resolved, issues);
        clampAmount(itemStack, resolved.amount(), issues);
        return finish(itemStack, issues);
    }

    public ItemBuildResult apply(ItemStack baseItem, ConfiguredItemDefinition definition) {
        return apply(baseItem, definition, Map.of());
    }

    public ItemBuildResult apply(ItemStack baseItem,
            ConfiguredItemDefinition definition,
            Map<String, ?> replacements) {
        List<ItemBuildIssue> issues = new ArrayList<>();
        if (baseItem == null) {
            return finish(null, List.of(ItemBuildIssue.error(null, "基础物品堆为 null。")));
        }
        if (definition == null) {
            return finish(baseItem, List.of(ItemBuildIssue.error(null, "配置物品定义为 null。")));
        }
        ConfiguredItemDefinition resolved = resolve(definition, replacements);
        ItemStack itemStack = baseItem.clone();
        applyGenericPatches(itemStack, resolved.components(), issues);
        int requestedAmount = resolved.source() == null ? baseItem.getAmount() : resolved.amount();
        clampAmount(itemStack, requestedAmount, issues);
        return finish(itemStack, issues);
    }

    private ItemStack createVanilla(ItemSourceRef source,
            ConfiguredItemDefinition definition,
            List<ItemBuildIssue> issues) {
        String materialId = "minecraft:" + ItemSourceUtil.normalizeVanillaIdentifier(source.identifier());
        Map<String, ItemComponentPatch> accepted = acceptedVanillaPatches(definition.components(), issues);
        if (accepted.isEmpty()) {
            ItemStack created = itemSourceService.createItem(source, definition.amount());
            if (created == null) {
                issues.add(ItemBuildIssue.error(null, "无法创建原版物品来源: " + definition.source()));
            }
            return created;
        }

        String itemSyntax;
        try {
            itemSyntax = itemSyntax(materialId, accepted);
        } catch (IllegalArgumentException exception) {
            issues.add(ItemBuildIssue.error(null, "组件编码失败: " + message(exception)));
            return itemSourceService.createItem(source, definition.amount());
        }
        try {
            ItemStack parsed = paperBridge.parseItemStack(itemSyntax);
            if (parsed == null) {
                issues.add(ItemBuildIssue.error(null, "原版物品解析器未返回物品: " + definition.source()));
            }
            return parsed;
        } catch (IllegalArgumentException exception) {
            diagnoseVanillaPatches(materialId, accepted, issues);
            if (issues.stream().noneMatch(issue -> issue.severity() == ItemBuildIssueSeverity.ERROR)) {
                issues.add(ItemBuildIssue.error(null, "合并后的原版组件补丁无效: " + message(exception)));
            }
            return itemSourceService.createItem(source, definition.amount());
        } catch (RuntimeException | LinkageError exception) {
            issues.add(ItemBuildIssue.error(null, "原版物品解析器失败: " + message(exception)));
            return itemSourceService.createItem(source, definition.amount());
        }
    }

    private ItemStack createThirdParty(ItemSourceRef source,
            ConfiguredItemDefinition definition,
            List<ItemBuildIssue> issues) {
        ItemStack created = itemSourceService.createItem(source, definition.amount());
        if (created == null) {
            issues.add(ItemBuildIssue.error(null, "物品来源解析器无法创建: " + definition.source()));
            return null;
        }
        ItemStack itemStack = created.clone();
        applyGenericPatches(itemStack, definition.components(), issues);
        return itemStack;
    }

    private Map<String, ItemComponentPatch> acceptedVanillaPatches(Map<String, ItemComponentPatch> patches,
            List<ItemBuildIssue> issues) {
        Map<String, ItemComponentPatch> accepted = new LinkedHashMap<>();
        for (Map.Entry<String, ItemComponentPatch> entry : patches.entrySet()) {
            String componentId = entry.getKey();
            if (paperBridge.supports(componentId)) {
                accepted.put(componentId, entry.getValue());
                continue;
            }
            if (catalog.entry(componentId) == null) {
                issues.add(ItemBuildIssue.error(componentId, "未知的物品组件 id。"));
            } else {

                accepted.put(componentId, entry.getValue());
            }
        }
        return accepted;
    }

    private void applyGenericPatches(ItemStack itemStack,
            Map<String, ItemComponentPatch> patches,
            List<ItemBuildIssue> issues) {
        for (Map.Entry<String, ItemComponentPatch> entry : patches.entrySet()) {
            String componentId = entry.getKey();
            if (paperBridge.supports(componentId)) {
                paperBridge.apply(itemStack, componentId, entry.getValue(), codec, issues);
                continue;
            }
            if (catalog.entry(componentId) == null) {
                issues.add(ItemBuildIssue.error(componentId, "未知的物品组件 id。"));
            } else {
                issues.add(ItemBuildIssue.warning(componentId,
                        "当前 Paper 运行时未通过通用桥接暴露该组件；为保留来源数据已跳过补丁。"));
            }
        }
    }

    private String itemSyntax(String materialId, Map<String, ItemComponentPatch> patches) {
        StringBuilder builder = new StringBuilder(materialId).append('[');
        boolean first = true;
        for (Map.Entry<String, ItemComponentPatch> entry : patches.entrySet()) {
            ItemComponentPatch patch = entry.getValue();
            if (patch.operation() == ItemComponentPatch.Operation.RESET) {
                continue;
            }
            if (!first) {
                builder.append(',');
            }
            first = false;
            if (patch.operation() == ItemComponentPatch.Operation.UNSET) {
                builder.append('!').append(entry.getKey());
                continue;
            }
            MinecraftItemComponentCatalog.Entry catalogEntry = catalog.entry(entry.getKey());
            boolean nonValued = paperBridge.isNonValued(entry.getKey())
                    || catalogEntry != null && catalogEntry.nonValued();
            warnUnsupportedVersion(entry.getKey(), catalogEntry);
            builder.append(entry.getKey())
                    .append('=')
                    .append(codec.encode(entry.getKey(), patch.value(), nonValued));
        }
        return first ? materialId : builder.append(']').toString();
    }

    private void warnUnsupportedVersion(String componentId, MinecraftItemComponentCatalog.Entry catalogEntry) {
        if (catalogEntry == null || plugin == null) {
            return;
        }
        String requirement = catalogEntry.versionRequirement();
        if (requirement.isBlank()) {
            return;
        }
        String server = MinecraftServerVersions.currentServerVersion();
        if (MinecraftServerVersions.satisfies(requirement, server)) {
            return;
        }
        String issueKey = "component_version:" + componentId + "@" + server;
        if (loggedIssues.get(issueKey) == null) {
            loggedIssues.put(issueKey, Boolean.TRUE);
            plugin.getLogger().warning("物品组件 " + componentId + " 需要 Minecraft " + requirement
                    + "，但当前服务器为 " + server + "；该组件不会生效。");
        }
    }

    private void diagnoseVanillaPatches(String materialId,
            Map<String, ItemComponentPatch> patches,
            List<ItemBuildIssue> issues) {
        for (Map.Entry<String, ItemComponentPatch> entry : patches.entrySet()) {
            if (entry.getValue().operation() == ItemComponentPatch.Operation.RESET) {
                continue;
            }
            try {
                paperBridge.parseItemStack(itemSyntax(materialId, Map.of(entry.getKey(), entry.getValue())));
            } catch (IllegalArgumentException exception) {
                issues.add(ItemBuildIssue.error(entry.getKey(), "组件值无效: " + message(exception)));
            } catch (RuntimeException | LinkageError exception) {
                issues.add(ItemBuildIssue.error(entry.getKey(), "组件解析器失败: " + message(exception)));
            }
        }
    }

    private ConfiguredItemDefinition resolve(ConfiguredItemDefinition definition, Map<String, ?> replacements) {
        Map<String, ?> safeReplacements = replacements == null ? Map.of() : replacements;
        Map<String, ItemComponentPatch> resolvedPatches = new LinkedHashMap<>();
        for (Map.Entry<String, ItemComponentPatch> entry : definition.components().entrySet()) {
            ItemComponentPatch patch = entry.getValue();
            if (patch.operation() != ItemComponentPatch.Operation.SET) {
                resolvedPatches.put(entry.getKey(), patch);
                continue;
            }
            Object resolvedValue = LORE_COMPONENT_ID.equals(entry.getKey())
                    ? expandLoreValue(patch.value(), safeReplacements)
                    : replacePlain(patch.value(), safeReplacements);
            resolvedPatches.put(entry.getKey(), ItemComponentPatch.set(resolvedValue));
        }
        String source = definition.source() == null
                ? null
                : Texts.formatTemplate(definition.source(), safeReplacements);
        return new ConfiguredItemDefinition(source, definition.amount(), resolvedPatches);
    }

    private Object expandLoreValue(Object value, Map<String, ?> replacements) {
        if (!(value instanceof Collection<?> lines)) {
            return replacePlain(value, replacements);
        }
        List<Object> result = new ArrayList<>(lines.size());
        for (Object line : lines) {
            if (line instanceof String text) {
                result.addAll(Texts.expandTemplateLines(text, replacements));
            } else {
                result.add(replacePlain(line, replacements));
            }
        }
        return result;
    }

    private Object replacePlain(Object value, Map<String, ?> replacements) {
        if (value instanceof String text) {
            return Texts.formatTemplate(text, replacements);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    result.put(String.valueOf(entry.getKey()), replacePlain(entry.getValue(), replacements));
                }
            }
            return result;
        }
        if (value instanceof Collection<?> collection) {
            List<Object> result = new ArrayList<>(collection.size());
            for (Object entry : collection) {
                result.add(replacePlain(entry, replacements));
            }
            return result;
        }
        return value;
    }

    private void clampAmount(ItemStack itemStack, int requestedAmount, List<ItemBuildIssue> issues) {
        if (itemStack == null) {
            return;
        }
        int maximum = Math.max(1, itemStack.getMaxStackSize());
        int clamped = Math.max(1, Math.min(requestedAmount, maximum));
        if (clamped != requestedAmount) {
            issues.add(ItemBuildIssue.warning("minecraft:max_stack_size",
                    "请求数量 " + requestedAmount + " 已被裁剪为 " + clamped + "。"));
        }
        itemStack.setAmount(clamped);
    }

    private ItemBuildResult finish(ItemStack itemStack, List<ItemBuildIssue> issues) {
        List<ItemBuildIssue> safeIssues = issues == null ? List.of() : List.copyOf(issues);
        logIssues(safeIssues);
        return new ItemBuildResult(itemStack, safeIssues);
    }

    private void logIssues(List<ItemBuildIssue> issues) {
        if (plugin == null) {
            return;
        }
        for (ItemBuildIssue issue : issues) {
            String key = issue.severity() + "|" + issue.componentId() + "|" + issue.message();
            if (loggedIssues.get(key) != null) {
                continue;
            }
            loggedIssues.put(key, Boolean.TRUE);
            String prefix = issue.componentId() == null ? "" : "[" + issue.componentId() + "] ";
            if (issue.severity() == ItemBuildIssueSeverity.ERROR) {
                plugin.getLogger().severe(prefix + issue.message());
            } else {
                plugin.getLogger().warning(prefix + issue.message());
            }
        }
    }

    private String message(Throwable throwable) {
        String message = throwable == null ? null : throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }
}
