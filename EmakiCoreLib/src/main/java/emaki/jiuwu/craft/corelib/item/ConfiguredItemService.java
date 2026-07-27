package emaki.jiuwu.craft.corelib.item;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.api.item.ConfiguredItemDefinition;
import emaki.jiuwu.craft.corelib.api.item.ItemBuildIssue;
import emaki.jiuwu.craft.corelib.api.item.ItemBuildIssueSeverity;
import emaki.jiuwu.craft.corelib.api.item.ItemBuildResult;
import emaki.jiuwu.craft.corelib.api.item.ItemComponentCapability;
import emaki.jiuwu.craft.corelib.api.item.ItemComponentPatch;
import emaki.jiuwu.craft.corelib.text.Texts;


public final class ConfiguredItemService {

    private final Plugin plugin;
    private final ItemSourceService itemSourceService;
    private final ConfiguredItemParser parser = new ConfiguredItemParser();
    private final LegacyConfiguredItemConverter legacyConverter = new LegacyConfiguredItemConverter(parser);
    private final MinecraftComponentValueCodec codec = new MinecraftComponentValueCodec();
    private final MinecraftItemComponentCatalog catalog = new MinecraftItemComponentCatalog();
    private final PaperItemComponentBridge paperBridge = new PaperItemComponentBridge();
    private final List<ItemComponentCapability> capabilities = paperBridge.capabilities(catalog);
    private final Set<String> loggedIssues = ConcurrentHashMap.newKeySet();

    public ConfiguredItemService(Plugin plugin, ItemSourceService itemSourceService) {
        this.plugin = plugin;
        this.itemSourceService = itemSourceService == null ? new ItemSourceService() : itemSourceService;
    }

    public ConfiguredItemParser parser() {
        return parser;
    }

    public LegacyConfiguredItemConverter legacyConverter() {
        return legacyConverter;
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
            return finish(null, List.of(ItemBuildIssue.error(null, "Configured item definition is null.")));
        }
        ConfiguredItemDefinition resolved = resolve(definition, replacements);
        ItemSource source = ItemSourceUtil.parse(resolved.source());
        if (source == null) {
            return finish(null, List.of(ItemBuildIssue.error(null, "Configured item source is missing or invalid.")));
        }

        ItemStack itemStack = source.getType() == ItemSourceType.VANILLA
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
            return finish(null, List.of(ItemBuildIssue.error(null, "Base item stack is null.")));
        }
        if (definition == null) {
            return finish(baseItem, List.of(ItemBuildIssue.error(null, "Configured item definition is null.")));
        }
        ConfiguredItemDefinition resolved = resolve(definition, replacements);
        ItemStack itemStack = baseItem.clone();
        applyGenericPatches(itemStack, resolved.components(), issues);
        int requestedAmount = resolved.source() == null ? baseItem.getAmount() : resolved.amount();
        clampAmount(itemStack, requestedAmount, issues);
        return finish(itemStack, issues);
    }

    private ItemStack createVanilla(ItemSource source,
            ConfiguredItemDefinition definition,
            List<ItemBuildIssue> issues) {
        String materialId = "minecraft:" + ItemSourceUtil.normalizeVanillaIdentifier(source.getIdentifier());
        Map<String, ItemComponentPatch> accepted = acceptedVanillaPatches(definition.components(), issues);
        if (accepted.isEmpty()) {
            ItemStack created = itemSourceService.createItem(source, definition.amount());
            if (created == null) {
                issues.add(ItemBuildIssue.error(null, "Vanilla item source could not be created: " + definition.source()));
            }
            return created;
        }

        String itemSyntax;
        try {
            itemSyntax = itemSyntax(materialId, accepted);
        } catch (IllegalArgumentException exception) {
            issues.add(ItemBuildIssue.error(null, "Component encoding failed: " + message(exception)));
            return itemSourceService.createItem(source, definition.amount());
        }
        try {
            ItemStack parsed = paperBridge.parseItemStack(itemSyntax);
            if (parsed == null) {
                issues.add(ItemBuildIssue.error(null, "Vanilla item parser returned no item for " + definition.source()));
            }
            return parsed;
        } catch (IllegalArgumentException exception) {
            diagnoseVanillaPatches(materialId, accepted, issues);
            if (issues.stream().noneMatch(issue -> issue.severity() == ItemBuildIssueSeverity.ERROR)) {
                issues.add(ItemBuildIssue.error(null, "Combined vanilla component patch is invalid: " + message(exception)));
            }
            return itemSourceService.createItem(source, definition.amount());
        } catch (RuntimeException | LinkageError exception) {
            issues.add(ItemBuildIssue.error(null, "Vanilla item parser failed: " + message(exception)));
            return itemSourceService.createItem(source, definition.amount());
        }
    }

    private ItemStack createThirdParty(ItemSource source,
            ConfiguredItemDefinition definition,
            List<ItemBuildIssue> issues) {
        ItemStack created = itemSourceService.createItem(source, definition.amount());
        if (created == null) {
            issues.add(ItemBuildIssue.error(null, "Item source resolver could not create: " + definition.source()));
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
            MinecraftItemComponentCatalog.Entry catalogEntry = catalog.entry(componentId);
            if (catalogEntry == null) {
                issues.add(ItemBuildIssue.error(componentId, "Unknown item component id."));
            } else if (catalog.isKnownFutureComponent(componentId)) {
                issues.add(ItemBuildIssue.warning(componentId,
                        "Component requires Minecraft " + catalogEntry.minimumMinecraftVersion()
                                + "; current server is " + catalog.serverVersion() + ". Patch was skipped."));
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
            MinecraftItemComponentCatalog.Entry catalogEntry = catalog.entry(componentId);
            if (catalogEntry == null) {
                issues.add(ItemBuildIssue.error(componentId, "Unknown item component id."));
            } else if (catalog.isKnownFutureComponent(componentId)) {
                issues.add(ItemBuildIssue.warning(componentId,
                        "Component requires Minecraft " + catalogEntry.minimumMinecraftVersion()
                                + "; current server is " + catalog.serverVersion() + ". Patch was skipped."));
            } else {
                issues.add(ItemBuildIssue.warning(componentId,
                        "Current Paper runtime does not expose this component through the generic bridge; patch was skipped to preserve source data."));
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
            builder.append(entry.getKey())
                    .append('=')
                    .append(codec.encode(entry.getKey(), patch.value(), nonValued));
        }
        return first ? materialId : builder.append(']').toString();
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
                issues.add(ItemBuildIssue.error(entry.getKey(), "Invalid component value: " + message(exception)));
            } catch (RuntimeException | LinkageError exception) {
                issues.add(ItemBuildIssue.error(entry.getKey(), "Component parser failed: " + message(exception)));
            }
        }
    }

    private ConfiguredItemDefinition resolve(ConfiguredItemDefinition definition, Map<String, ?> replacements) {
        Map<String, ?> safeReplacements = replacements == null ? Map.of() : replacements;
        Map<String, ItemComponentPatch> resolvedPatches = new LinkedHashMap<>();
        for (Map.Entry<String, ItemComponentPatch> entry : definition.components().entrySet()) {
            ItemComponentPatch patch = entry.getValue();
            resolvedPatches.put(entry.getKey(), patch.operation() == ItemComponentPatch.Operation.SET
                    ? ItemComponentPatch.set(replacePlain(patch.value(), safeReplacements))
                    : patch);
        }
        String source = definition.source() == null
                ? null
                : Texts.formatTemplate(definition.source(), safeReplacements);
        return new ConfiguredItemDefinition(source, definition.amount(), resolvedPatches);
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
                    "Requested amount " + requestedAmount + " was clamped to " + clamped + "."));
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
            if (!loggedIssues.add(key)) {
                continue;
            }
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
