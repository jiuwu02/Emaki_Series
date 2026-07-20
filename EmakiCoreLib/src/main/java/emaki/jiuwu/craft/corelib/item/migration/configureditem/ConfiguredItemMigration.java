package emaki.jiuwu.craft.corelib.item.migration.configureditem;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import org.bukkit.plugin.java.JavaPlugin;

import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.settings.dumper.DumperSettings;
import dev.dejvokep.boostedyaml.settings.general.GeneralSettings;
import dev.dejvokep.boostedyaml.settings.loader.LoaderSettings;
import dev.dejvokep.boostedyaml.settings.updater.UpdaterSettings;
import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.yaml.BoostedYamlSection;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.MapYamlSection;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;








public final class ConfiguredItemMigration {
    private static final DateTimeFormatter BACKUP_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
    private static final GeneralSettings GENERAL_SETTINGS = GeneralSettings.builder()
            .setUseDefaults(false)
            .build();
    private static final LoaderSettings LOADER_SETTINGS = LoaderSettings.builder()
            .setCreateFileIfAbsent(true)
            .setAutoUpdate(false)
            .build();
    private static final DumperSettings DUMPER_SETTINGS = DumperSettings.builder()
            .setIndentation(2)
            .build();
    private static final UpdaterSettings UPDATER_SETTINGS = UpdaterSettings.builder()
            .setAutoSave(false)
            .setKeepAll(true)
            .setOptionSorting(UpdaterSettings.OptionSorting.SORT_BY_DEFAULTS)
            .build();
    private static final Set<String> GUI_NODE_MAP_KEYS = Set.of("slots", "buttons", "content_templates", "virtual_items");
    private static final Set<String> GUI_SINGLE_NODE_KEYS = Set.of("content_item");
    private static final Set<String> LEGACY_COMPONENT_ALIASES = Set.of(
            "display_name", "displayname", "item-model", "custommodeldata",
            "hidden_components", "hide_tooltip", "hide-tooltip", "item_flags"
    );
    private static final Set<String> LEGACY_NODE_KEYS = Set.of(
            "item_source", "item_sources", "material", "components",
            "display_name", "displayname", "item_name", "itemname", "lore",
            "item_model", "item-model", "custom_model_data", "custommodeldata",
            "enchantments", "hidden_components", "hide_tooltip", "hide-tooltip",
            "tooltip_display", "item_flags"
    );

    private ConfiguredItemMigration() {
    }


    public static void migrateGuiFiles(JavaPlugin plugin, Collection<File> files) {
        Objects.requireNonNull(plugin, "plugin");
        MigrationReport report = migrateFiles(plugin.getDataFolder().toPath(), files, ConfiguredItemMigration::convertGui);
        for (FileIssue issue : report.skipped()) {
            plugin.getLogger().warning("Skipped configured-item YAML migration for "
                    + issue.file().getPath() + ": " + issue.message());
        }
        for (FileIssue issue : report.failures()) {
            plugin.getLogger().warning("Configured-item YAML migration failed for "
                    + issue.file().getPath() + ": " + issue.message()
                    + "; the original file will be loaded through legacy compatibility.");
        }
        if (report.changedFiles() > 0) {
            plugin.getLogger().info("Migrated " + report.changedFiles() + " GUI YAML file(s), "
                    + report.changedNodes() + " configured-item node(s). Backups: " + report.backupRoot());
        }
    }


    public static MigrationReport migrateLegacyItemFiles(Path dataFolder, Collection<File> files) {
        return migrateFiles(dataFolder, files, ConfiguredItemMigration::convertLegacyItemRoot);
    }

    private static MigrationReport migrateFiles(Path dataFolder,
            Collection<File> files,
            Function<Map<String, Object>, Conversion> converter) {
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(converter, "converter");
        List<File> orderedFiles = new ArrayList<>(files == null ? List.of() : files);
        orderedFiles.removeIf(file -> file == null || !file.isFile());
        orderedFiles.sort((left, right) -> left.getPath().compareToIgnoreCase(right.getPath()));

        Path normalizedDataFolder = dataFolder.toAbsolutePath().normalize();
        Path backupRoot = null;
        int changedFiles = 0;
        int changedNodes = 0;
        List<FileIssue> failures = new ArrayList<>();
        List<FileIssue> skipped = new ArrayList<>();

        for (File file : orderedFiles) {
            try {
                EditableYaml yamlFile = openEditable(file);
                Map<String, Object> original = yamlFile.values();
                Conversion converted = converter.apply(MapYamlSection.normalizeMap(original));
                if (converted == null) {
                    continue;
                }
                if (Texts.isNotBlank(converted.skipReason())) {
                    skipped.add(new FileIssue(file, converted.skipReason()));
                    continue;
                }
                Map<String, Object> migrated = converted.values();
                if (Objects.equals(original, migrated)) {
                    continue;
                }
                validateInMemory(migrated);
                if (backupRoot == null) {
                    backupRoot = normalizedDataFolder
                            .resolve("migration-backups")
                            .resolve("configured-item-format")
                            .resolve(LocalDateTime.now().format(BACKUP_TIMESTAMP));
                }
                Path backup = createBackup(normalizedDataFolder, backupRoot, file.toPath());
                applyTopLevelChanges(yamlFile.document(), original, migrated);
                saveAtomic(file, yamlFile.document(), backup, migrated);
                changedFiles++;
                changedNodes += Math.max(1, converted.changedNodes());
            } catch (Exception exception) {
                failures.add(new FileIssue(file, safeMessage(exception)));
            }
        }
        return new MigrationReport(
                orderedFiles.size(),
                changedFiles,
                changedNodes,
                backupRoot,
                List.copyOf(failures),
                List.copyOf(skipped)
        );
    }

    private static Conversion convertLegacyItemRoot(Map<String, Object> root) {
        String itemId = Texts.normalizeId(Texts.toStringSafe(root.get("id")));
        NodeMigration migrated = convertLegacyItemNode(root, itemId);
        if (Texts.isNotBlank(migrated.skipReason())) {
            return Conversion.skipped(root, migrated.skipReason());
        }
        return migrated.changed()
                ? Conversion.changed(migrated.values(), 1)
                : Conversion.unchanged(root);
    }


    private static Conversion convertGui(Map<String, Object> root) {
        Map<String, Object> original = MapYamlSection.normalizeMap(root);
        Map<String, Object> migrated = MapYamlSection.normalizeMap(original);
        Counter counter = new Counter();
        String skipReason = migrateGuiContainers(migrated, counter);
        if (Texts.isNotBlank(skipReason)) {
            return Conversion.skipped(original, skipReason);
        }
        return Objects.equals(original, migrated)
                ? Conversion.unchanged(original)
                : Conversion.changed(migrated, counter.value);
    }





    private static NodeMigration convertLegacyItemNode(Map<String, ?> node, String itemId) {
        Map<String, Object> original = MapYamlSection.normalizeMap(node);
        Map<String, Object> migrated = MapYamlSection.normalizeMap(original);
        String skipReason = migrateLegacyItemNodeInPlace(migrated, itemId);
        if (Texts.isNotBlank(skipReason)) {
            return NodeMigration.skipped(original, skipReason);
        }
        return Objects.equals(original, migrated)
                ? NodeMigration.unchanged(original)
                : NodeMigration.changed(migrated);
    }

    private static String migrateGuiContainers(Map<String, Object> container, Counter counter) {
        for (Map.Entry<String, Object> entry : new ArrayList<>(container.entrySet())) {
            String key = Texts.toStringSafe(entry.getKey()).toLowerCase(Locale.ROOT);
            Object raw = entry.getValue();
            if (GUI_NODE_MAP_KEYS.contains(key)) {
                Map<String, Object> nodes = mutableMap(raw);
                if (nodes == null) {
                    continue;
                }
                for (Map.Entry<String, Object> nodeEntry : new ArrayList<>(nodes.entrySet())) {
                    Map<String, Object> node = mutableMap(nodeEntry.getValue());
                    if (node == null) {
                        continue;
                    }
                    String skipReason = migrateLegacyItemNodeInPlace(node, nodeEntry.getKey());
                    if (Texts.isNotBlank(skipReason)) {
                        return skipReason;
                    }
                    boolean itemChanged = !Objects.equals(nodeEntry.getValue(), node);
                    skipReason = migrateGuiContainers(node, counter);
                    if (Texts.isNotBlank(skipReason)) {
                        return skipReason;
                    }
                    nodes.put(nodeEntry.getKey(), node);
                    if (itemChanged) {
                        counter.value++;
                    }
                }
                container.put(entry.getKey(), nodes);
                continue;
            }
            if (GUI_SINGLE_NODE_KEYS.contains(key)) {
                Map<String, Object> node = mutableMap(raw);
                if (node == null) {
                    continue;
                }
                String skipReason = migrateLegacyItemNodeInPlace(node, key);
                if (Texts.isNotBlank(skipReason)) {
                    return skipReason;
                }
                boolean itemChanged = !Objects.equals(raw, node);
                skipReason = migrateGuiContainers(node, counter);
                if (Texts.isNotBlank(skipReason)) {
                    return skipReason;
                }
                container.put(entry.getKey(), node);
                if (itemChanged) {
                    counter.value++;
                }
                continue;
            }
            if ("pages".equals(key)) {
                Map<String, Object> pages = mutableMap(raw);
                if (pages == null) {
                    continue;
                }
                for (Map.Entry<String, Object> pageEntry : new ArrayList<>(pages.entrySet())) {
                    Map<String, Object> page = mutableMap(pageEntry.getValue());
                    if (page == null) {
                        continue;
                    }
                    String skipReason = migrateGuiContainers(page, counter);
                    if (Texts.isNotBlank(skipReason)) {
                        return skipReason;
                    }
                    pages.put(pageEntry.getKey(), page);
                }
                container.put(entry.getKey(), pages);
            }
        }
        return "";
    }

    private static String migrateLegacyItemNodeInPlace(Map<String, Object> node, String itemId) {
        if (!isLegacyItemNode(node)) {
            return "";
        }
        Object existingItemRaw = node.get("item");
        Map<String, Object> existingItem = mutableMap(existingItemRaw);
        if (containsRawComponent(node) || containsRawComponent(existingItem)) {
            return "legacy components.raw cannot be normalized safely";
        }

        Map<String, Object> item = existingItem == null ? new LinkedHashMap<>() : existingItem;
        String source = firstSourceText(item.get("source"));
        if (Texts.isBlank(source)) {
            source = legacySource(item, null);
        }
        if (Texts.isBlank(source)) {
            source = legacySource(node, existingItemRaw instanceof String text ? text : null);
        }
        if (Texts.isNotBlank(source)) {
            item.put("source", source);
        }

        if (!item.containsKey("amount")) {
            item.put("amount", Math.max(1, Numbers.tryParseInt(node.get("amount"), 1)));
        }

        Map<String, Object> components = normalizeComponentMap(item.get("components"), itemId);
        Set<String> protectedComponents = new HashSet<>();
        components.keySet().stream().map(ConfiguredItemMigration::normalizedComponentKey).forEach(protectedComponents::add);
        mergeCandidates(components, legacyFieldComponents(item), protectedComponents, false);

        Map<String, Object> outerCandidates = normalizeComponentMap(node.get("components"), itemId);
        outerCandidates.putAll(legacyFieldComponents(node));
        mergeCandidates(components, outerCandidates, protectedComponents, true);
        if (!components.isEmpty()) {
            item.put("components", components);
        } else {
            item.remove("components");
        }

        removeLegacyNodeFields(item, false, false);
        removeLegacyNodeFields(node, true, true);
        node.put("item", item);
        return "";
    }

    private static boolean isLegacyItemNode(Map<String, Object> node) {
        if (node == null || node.isEmpty()) {
            return false;
        }
        Object item = node.get("item");
        if (item instanceof String || node.containsKey("source")) {
            return true;
        }
        for (String key : node.keySet()) {
            if (LEGACY_NODE_KEYS.contains(Texts.toStringSafe(key).toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        Map<String, Object> nestedItem = mutableMap(item);
        if (nestedItem == null) {
            return false;
        }
        for (String key : nestedItem.keySet()) {
            String normalized = Texts.toStringSafe(key).toLowerCase(Locale.ROOT);
            if (LEGACY_NODE_KEYS.contains(normalized) && !"components".equals(normalized)) {
                return true;
            }
        }
        return hasLegacyComponentAliases(nestedItem.get("components"));
    }

    private static boolean hasLegacyComponentAliases(Object raw) {
        Map<String, Object> components = mutableMap(raw);
        if (components == null) {
            return false;
        }
        for (String key : components.keySet()) {
            if (LEGACY_COMPONENT_ALIASES.contains(Texts.toStringSafe(key).toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        Object enchantable = components.get("enchantable");
        if (enchantable != null && mutableMap(enchantable) == null) {
            return true;
        }
        Object modifiers = components.get("attribute_modifiers");
        if (mutableMap(modifiers) != null || containsLegacyAttributeEntry(modifiers)) {
            return true;
        }
        Object enchantments = components.get("enchantments");
        return mutableMap(enchantments) != null && ConfigNodes.contains(enchantments, "levels");
    }

    private static boolean containsRawComponent(Map<String, Object> node) {
        if (node == null) {
            return false;
        }
        Object components = ConfigNodes.get(node, "components");
        return ConfigNodes.contains(components, "raw");
    }

    private static Map<String, Object> normalizeComponentMap(Object raw, String itemId) {
        Map<String, Object> source = mutableMap(raw);
        if (source == null) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = Texts.toStringSafe(entry.getKey());
            String lower = key.toLowerCase(Locale.ROOT);
            if (LEGACY_COMPONENT_ALIASES.contains(lower) || "raw".equals(lower)) {
                continue;
            }
            Object value = normalizeComponentValue(lower, entry.getValue(), itemId);
            if (value != null || entry.getValue() == null) {
                normalized.put(key, value);
            }
        }

        Map<String, Object> legacy = legacyFieldComponents(source);
        legacy.forEach(normalized::putIfAbsent);
        return normalized;
    }

    private static Object normalizeComponentValue(String key, Object raw, String itemId) {
        return switch (key) {
            case "custom_model_data", "minecraft:custom_model_data" -> normalizeCustomModelData(raw);
            case "enchantments", "minecraft:enchantments" -> normalizeEnchantments(raw);
            case "enchantable", "minecraft:enchantable" -> normalizeEnchantable(raw);
            case "attribute_modifiers", "minecraft:attribute_modifiers" -> normalizeAttributeModifiers(raw, itemId);
            default -> ConfigNodes.toPlainData(raw);
        };
    }

    private static Map<String, Object> legacyFieldComponents(Map<String, Object> node) {
        Map<String, Object> components = new LinkedHashMap<>();
        if (node == null) {
            return components;
        }
        Object displayName = first(node, "display_name", "displayName");
        if (displayName != null) {
            components.put("custom_name", ConfigNodes.toPlainData(displayName));
        }
        Object itemName = first(node, "item_name", "itemName");
        if (itemName != null) {
            components.put("item_name", ConfigNodes.toPlainData(itemName));
        }
        if (node.containsKey("lore")) {
            components.put("lore", ConfigNodes.toPlainData(node.get("lore") == null ? List.of() : node.get("lore")));
        }
        Object itemModel = first(node, "item_model", "item-model");
        if (itemModel != null) {
            components.put("item_model", ConfigNodes.toPlainData(itemModel));
        }
        Object customModelData = first(node, "custom_model_data", "custommodeldata");
        Object normalizedCustomModelData = normalizeCustomModelData(customModelData);
        if (normalizedCustomModelData != null) {
            components.put("custom_model_data", normalizedCustomModelData);
        }
        if (node.containsKey("enchantments")) {
            Map<String, Object> enchantments = normalizeEnchantments(node.get("enchantments"));
            if (!enchantments.isEmpty()) {
                components.put("enchantments", enchantments);
            }
        }
        Map<String, Object> tooltipDisplay = normalizeTooltipDisplay(node);
        if (!tooltipDisplay.isEmpty()) {
            components.put("tooltip_display", tooltipDisplay);
        }
        return components;
    }

    private static Map<String, Object> normalizeTooltipDisplay(Map<String, Object> node) {
        Map<String, Object> tooltip = mutableMap(node == null ? null : node.get("tooltip_display"));
        if (tooltip == null) {
            tooltip = new LinkedHashMap<>();
        }
        List<String> hidden = new ArrayList<>(Texts.asStringList(ConfigNodes.get(tooltip, "hidden_components")));
        hidden.addAll(Texts.asStringList(node == null ? null : node.get("hidden_components")));
        hidden.addAll(itemFlagComponents(node == null ? null : node.get("item_flags")));

        boolean hideTooltip = booleanValue(ConfigNodes.get(tooltip, "hide_tooltip"), false)
                || booleanValue(node == null ? null : node.get("hide_tooltip"), false)
                || booleanValue(node == null ? null : node.get("hide-tooltip"), false)
                || Boolean.TRUE.equals(node == null ? null : node.get("tooltip_display"));
        List<String> normalizedHidden = new ArrayList<>();
        for (String entry : hidden) {
            String normalized = normalizeHiddenComponent(entry);
            if ("minecraft:tooltip".equals(normalized)) {
                hideTooltip = true;
            } else if (Texts.isNotBlank(normalized) && !normalizedHidden.contains(normalized)) {
                normalizedHidden.add(normalized);
            }
        }
        if (!normalizedHidden.isEmpty() && !tooltip.containsKey("hidden_components")) {
            tooltip.put("hidden_components", normalizedHidden);
        } else if (tooltip.containsKey("hidden_components")) {
            tooltip.put("hidden_components", normalizedHidden);
        }
        if (hideTooltip && !tooltip.containsKey("hide_tooltip")) {
            tooltip.put("hide_tooltip", true);
        }
        return tooltip;
    }

    private static List<String> itemFlagComponents(Object raw) {
        List<String> components = new ArrayList<>();
        for (String flag : Texts.asStringList(raw)) {
            String component = switch (Texts.toStringSafe(flag).trim().toUpperCase(Locale.ROOT)) {
                case "HIDE_ENCHANTS" -> "minecraft:enchantments";
                case "HIDE_ATTRIBUTES" -> "minecraft:attribute_modifiers";
                case "HIDE_UNBREAKABLE" -> "minecraft:unbreakable";
                case "HIDE_DESTROYS" -> "minecraft:can_break";
                case "HIDE_PLACED_ON" -> "minecraft:can_place_on";
                case "HIDE_ADDITIONAL_TOOLTIP", "HIDE_POTION_EFFECTS" -> "minecraft:potion_contents";
                case "HIDE_DYE" -> "minecraft:dyed_color";
                case "HIDE_ARMOR_TRIM" -> "minecraft:trim";
                default -> normalizeComponentId(flag);
            };
            if (Texts.isNotBlank(component)) {
                components.add(component);
            }
        }
        return components;
    }

    private static Object normalizeCustomModelData(Object raw) {
        Object plain = ConfigNodes.toPlainData(raw);
        if (plain == null) {
            return null;
        }
        Map<String, Object> mapping = mutableMap(plain);
        if (mapping != null && (mapping.containsKey("floats") || mapping.containsKey("flags")
                || mapping.containsKey("strings") || mapping.containsKey("colors"))) {
            return mapping;
        }
        Double numeric = Numbers.tryParseDouble(plain, null);
        return numeric == null ? null : Map.of("floats", List.of(numeric.floatValue()));
    }

    private static Map<String, Object> normalizeEnchantments(Object raw) {
        Object source = ConfigNodes.contains(raw, "levels") ? ConfigNodes.get(raw, "levels") : raw;
        Map<String, Object> normalized = new LinkedHashMap<>();
        Map<String, Object> mapping = mutableMap(source);
        if (mapping != null) {
            for (Map.Entry<String, Object> entry : mapping.entrySet()) {
                Integer level = Numbers.tryParseInt(entry.getValue(), null);
                if (Texts.isNotBlank(entry.getKey()) && level != null && level > 0) {
                    normalized.put(normalizeComponentId(entry.getKey()), level);
                }
            }
            return normalized;
        }
        for (String text : Texts.asStringList(source)) {
            if (Texts.isBlank(text)) {
                continue;
            }
            String id = text.trim();
            int level = 1;
            int separator = id.lastIndexOf(':');
            if (separator > 0) {
                Integer parsedLevel = Numbers.tryParseInt(id.substring(separator + 1), null);
                if (parsedLevel != null) {
                    level = parsedLevel;
                    id = id.substring(0, separator);
                }
            }
            if (level > 0) {
                normalized.put(normalizeComponentId(id), level);
            }
        }
        return normalized;
    }

    private static Object normalizeEnchantable(Object raw) {
        Map<String, Object> mapping = mutableMap(raw);
        if (mapping != null) {
            return mapping;
        }
        Integer value = Numbers.tryParseInt(raw, null);
        return value == null ? ConfigNodes.toPlainData(raw) : Map.of("value", value);
    }

    private static Object normalizeAttributeModifiers(Object raw, String itemId) {
        Object entriesRaw = raw instanceof Iterable<?> ? raw : ConfigNodes.get(raw, "modifiers");
        List<Object> entries = ConfigNodes.asObjectList(entriesRaw);
        if (entries.isEmpty()) {
            return ConfigNodes.toPlainData(raw);
        }
        List<Map<String, Object>> modifiers = new ArrayList<>();
        for (Object entryRaw : entries) {
            Map<String, Object> entry = mutableMap(entryRaw);
            if (entry == null) {
                continue;
            }
            String type = text(first(entry, "type", "attribute"));
            Object amount = ConfigNodes.toPlainData(entry.get("amount"));
            if (Texts.isBlank(type) || amount == null) {
                continue;
            }
            Map<String, Object> modifier = new LinkedHashMap<>();
            modifier.put("type", normalizeAttributeType(type));
            String id = text(first(entry, "id", "name"));
            if (Texts.isBlank(id)) {
                String normalizedItemId = Texts.isBlank(itemId) ? "legacy" : Texts.normalizeId(itemId);
                id = "emakiitem:" + normalizedItemId + "/" + Texts.normalizeId(type);
            }
            modifier.put("id", namespaced(id));
            modifier.put("amount", amount);
            modifier.put("operation", normalizeAttributeOperation(text(entry.getOrDefault("operation", "add_number"))));
            modifier.put("slot", normalizeAttributeSlot(text(entry.getOrDefault("slot", "any"))));
            modifiers.add(modifier);
        }
        return modifiers.isEmpty() ? ConfigNodes.toPlainData(raw) : modifiers;
    }

    private static boolean containsLegacyAttributeEntry(Object raw) {
        Object entriesRaw = raw instanceof Iterable<?> ? raw : ConfigNodes.get(raw, "modifiers");
        for (Object entry : ConfigNodes.asObjectList(entriesRaw)) {
            if (ConfigNodes.contains(entry, "attribute") || ConfigNodes.contains(entry, "name")) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeAttributeType(String raw) {
        String normalized = namespaced(raw);
        String legacyPrefix = "minecraft:generic.";
        return normalized.startsWith(legacyPrefix)
                ? "minecraft:" + normalized.substring(legacyPrefix.length())
                : normalized;
    }

    private static String normalizeAttributeOperation(String raw) {
        return switch (Texts.normalizeId(raw)) {
            case "add_scalar" -> "add_multiplied_base";
            case "multiply_scalar_1" -> "add_multiplied_total";
            case "add_number" -> "add_value";
            default -> Texts.toStringSafe(raw);
        };
    }

    private static String normalizeAttributeSlot(String raw) {
        return switch (Texts.normalizeId(raw)) {
            case "hand", "mainhand", "main_hand" -> "hand";
            case "offhand", "off_hand" -> "offhand";
            case "head", "helmet" -> "head";
            case "chest", "chestplate" -> "chest";
            case "legs", "leggings" -> "legs";
            case "feet", "boots" -> "feet";
            default -> "any";
        };
    }

    private static String legacySource(Map<String, Object> node, String scalarItem) {
        if (node == null) {
            return scalarItem;
        }
        Object itemSource = first(node, "item_source", "item_sources");
        String source = firstSourceText(itemSource);
        if (Texts.isNotBlank(source)) {
            return source;
        }
        String material = text(node.get("material"));
        if (Texts.isNotBlank(material)) {
            String normalized = material.trim().toLowerCase(Locale.ROOT);
            return normalized.startsWith("minecraft-") ? normalized : "minecraft-" + normalized;
        }
        String nestedSource = firstSourceText(node.get("source"));
        return Texts.isNotBlank(nestedSource) ? nestedSource : scalarItem;
    }

    private static String firstSourceText(Object raw) {
        if (raw instanceof String text) {
            return Texts.trim(text);
        }
        for (Object entry : ConfigNodes.asObjectList(raw)) {
            if (entry == raw) {
                continue;
            }
            String value = firstSourceText(entry);
            if (Texts.isNotBlank(value)) {
                return value;
            }
        }
        Map<String, Object> mapping = mutableMap(raw);
        if (mapping == null) {
            return null;
        }
        String item = text(mapping.get("item"));
        if (Texts.isNotBlank(item)) {
            return item;
        }
        String source = firstSourceText(mapping.get("source"));
        if (Texts.isNotBlank(source)) {
            return source;
        }
        String type = text(mapping.get("type"));
        String identifier = text(mapping.get("identifier"));
        if (Texts.isBlank(type) || Texts.isBlank(identifier)) {
            return null;
        }
        return Texts.normalizeId(type) + "-" + identifier.trim();
    }

    private static void mergeCandidates(Map<String, Object> destination,
            Map<String, Object> candidates,
            Set<String> protectedComponents,
            boolean replaceUnprotected) {
        for (Map.Entry<String, Object> entry : candidates.entrySet()) {
            String normalizedKey = normalizedComponentKey(entry.getKey());
            if (protectedComponents.contains(normalizedKey)) {
                continue;
            }
            String existingKey = findComponentKey(destination, normalizedKey);
            if (existingKey == null) {
                destination.put(entry.getKey(), entry.getValue());
            } else if (replaceUnprotected) {
                destination.put(existingKey, entry.getValue());
            }
        }
    }

    private static String findComponentKey(Map<String, Object> components, String normalizedKey) {
        for (String key : components.keySet()) {
            if (normalizedComponentKey(key).equals(normalizedKey)) {
                return key;
            }
        }
        return null;
    }

    private static String normalizedComponentKey(String raw) {
        String normalized = Texts.toStringSafe(raw).trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("minecraft:") ? normalized.substring("minecraft:".length()) : normalized;
    }

    private static void removeLegacyNodeFields(Map<String, Object> node,
            boolean removeAmount,
            boolean removeComponents) {
        if (node == null) {
            return;
        }
        for (String key : new ArrayList<>(node.keySet())) {
            String normalized = Texts.toStringSafe(key).toLowerCase(Locale.ROOT);
            boolean legacyField = LEGACY_NODE_KEYS.contains(normalized)
                    && (removeComponents || !"components".equals(normalized));
            if (legacyField || (removeAmount && ("amount".equals(normalized) || "source".equals(normalized)))) {
                node.remove(key);
            }
        }
    }

    private static void validateInMemory(Map<String, Object> values) {
        String dumped = YamlFiles.dump(values);
        if (dumped.isBlank()) {
            throw new IllegalArgumentException("converted YAML could not be serialized");
        }
        Map<String, Object> reparsed = YamlFiles.load(dumped).asMap();
        if (!YamlFiles.dump(reparsed).equals(dumped)) {
            throw new IllegalArgumentException("converted YAML failed round-trip validation");
        }
    }

    private static EditableYaml openEditable(File file) throws IOException {
        Objects.requireNonNull(file, "file");
        Path target = file.toPath();
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        if (!file.exists()) {
            Files.createFile(target);
        }
        try (InputStream inputStream = Files.newInputStream(target)) {
            YamlDocument document = YamlDocument.create(
                    inputStream,
                    GENERAL_SETTINGS,
                    LOADER_SETTINGS,
                    DUMPER_SETTINGS,
                    UPDATER_SETTINGS
            );
            return new EditableYaml(document, new BoostedYamlSection(document).asMap());
        }
    }

    private static void saveAtomic(File file,
            YamlDocument document,
            Path recoveryBackup,
            Map<String, ?> expectedValues) throws IOException {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(expectedValues, "expectedValues");
        Path target = file.toPath();
        Path parent = target.getParent();
        Path tempDirectory = parent == null ? Path.of(".") : parent;
        Files.createDirectories(tempDirectory);
        Path temp = Files.createTempFile(tempDirectory, target.getFileName().toString(), ".migration.tmp");
        boolean moved = false;
        try {
            Files.writeString(temp, document.dump(DUMPER_SETTINGS), StandardCharsets.UTF_8);
            String expected = YamlFiles.dump(expectedValues);
            String actual = YamlFiles.dump(YamlFiles.load(temp.toFile()).asMap());
            if (expected.isBlank() || !expected.equals(actual)) {
                throw new IOException("Temporary YAML failed migration validation for '" + file.getPath() + "'.");
            }
            try {
                moveReplacing(temp, target);
                moved = true;
            } catch (IOException exception) {
                restoreBackup(recoveryBackup, target, exception);
                throw exception;
            }
        } finally {
            if (!moved) {
                Files.deleteIfExists(temp);
            }
        }
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException _) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void restoreBackup(Path backup, Path target, IOException moveFailure) {
        if (backup == null || target == null || !Files.isRegularFile(backup)) {
            return;
        }
        try {
            Files.copy(backup, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        } catch (IOException restoreFailure) {
            moveFailure.addSuppressed(restoreFailure);
        }
    }

    private static Path createBackup(Path dataFolder, Path backupRoot, Path source) throws IOException {
        Path normalizedSource = source.toAbsolutePath().normalize();
        Path relative = normalizedSource.startsWith(dataFolder)
                ? dataFolder.relativize(normalizedSource)
                : Path.of(normalizedSource.getFileName().toString());
        Path backup = backupRoot.resolve(relative).normalize();
        if (!backup.startsWith(backupRoot)) {
            throw new IOException("Refusing unsafe migration backup path for " + source);
        }
        Files.createDirectories(backup.getParent());
        Files.copy(normalizedSource, backup, StandardCopyOption.COPY_ATTRIBUTES);
        return backup;
    }

    private static void applyTopLevelChanges(YamlDocument document,
            Map<String, Object> original,
            Map<String, Object> migrated) {
        if (document == null) {
            throw new IllegalArgumentException("YAML document is unavailable");
        }
        for (String key : original.keySet()) {
            if (!migrated.containsKey(key)) {
                document.remove(key);
            }
        }
        for (Map.Entry<String, Object> entry : migrated.entrySet()) {
            if (!Objects.equals(original.get(entry.getKey()), entry.getValue())) {
                document.set(entry.getKey(), entry.getValue());
            }
        }
    }

    private static Object first(Map<String, Object> mapping, String... keys) {
        if (mapping == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (mapping.containsKey(key)) {
                return mapping.get(key);
            }
        }
        return null;
    }

    private static Map<String, Object> mutableMap(Object raw) {
        Object plain = ConfigNodes.toPlainData(raw);
        return plain instanceof Map<?, ?> map ? MapYamlSection.normalizeMap(map) : null;
    }

    private static String text(Object raw) {
        return raw == null ? null : Texts.toStringSafe(raw).trim();
    }

    private static boolean booleanValue(Object raw, boolean fallback) {
        if (raw instanceof Boolean bool) {
            return bool;
        }
        return raw == null ? fallback : Boolean.parseBoolean(String.valueOf(raw));
    }

    private static String normalizeHiddenComponent(String raw) {
        String normalized = Texts.toStringSafe(raw).trim().toLowerCase(Locale.ROOT);
        if ("tooltip".equals(normalized) || "*".equals(normalized)) {
            return "minecraft:tooltip";
        }
        return normalizeComponentId(normalized);
    }

    private static String normalizeComponentId(String raw) {
        String normalized = Texts.toStringSafe(raw).trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() || normalized.contains(":") ? normalized : "minecraft:" + normalized;
    }

    private static String namespaced(String raw) {
        String normalized = Texts.toStringSafe(raw).trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() || normalized.contains(":") ? normalized : "minecraft:" + normalized;
    }

    private static String safeMessage(Throwable throwable) {
        if (throwable == null || Texts.isBlank(throwable.getMessage())) {
            return throwable == null ? "unknown error" : throwable.getClass().getSimpleName();
        }
        return throwable.getMessage();
    }

    private record Conversion(Map<String, Object> values, int changedNodes, String skipReason) {

        public Conversion {
            values = MapYamlSection.normalizeMap(values);
            changedNodes = Math.max(0, changedNodes);
            skipReason = Texts.toStringSafe(skipReason);
        }

        public static Conversion unchanged(Map<String, ?> values) {
            return new Conversion(MapYamlSection.normalizeMap(values), 0, "");
        }

        public static Conversion changed(Map<String, ?> values, int changedNodes) {
            return new Conversion(MapYamlSection.normalizeMap(values), Math.max(1, changedNodes), "");
        }

        public static Conversion skipped(Map<String, ?> values, String reason) {
            return new Conversion(MapYamlSection.normalizeMap(values), 0, reason);
        }
    }

    private record NodeMigration(Map<String, Object> values, boolean changed, String skipReason) {

        public NodeMigration {
            values = MapYamlSection.normalizeMap(values);
            skipReason = Texts.toStringSafe(skipReason);
        }

        public static NodeMigration unchanged(Map<String, ?> values) {
            return new NodeMigration(MapYamlSection.normalizeMap(values), false, "");
        }

        public static NodeMigration changed(Map<String, ?> values) {
            return new NodeMigration(MapYamlSection.normalizeMap(values), true, "");
        }

        public static NodeMigration skipped(Map<String, ?> values, String reason) {
            return new NodeMigration(MapYamlSection.normalizeMap(values), false, reason);
        }
    }

    public record MigrationReport(int scannedFiles,
            int changedFiles,
            int changedNodes,
            Path backupRoot,
            List<FileIssue> failures,
            List<FileIssue> skipped) {

        public MigrationReport {
            scannedFiles = Math.max(0, scannedFiles);
            changedFiles = Math.max(0, changedFiles);
            changedNodes = Math.max(0, changedNodes);
            failures = failures == null ? List.of() : List.copyOf(failures);
            skipped = skipped == null ? List.of() : List.copyOf(skipped);
        }
    }

    public record FileIssue(File file, String message) {

        public FileIssue {
            message = Texts.toStringSafe(message);
        }
    }

    private record EditableYaml(YamlDocument document, Map<String, Object> values) {

        private EditableYaml {
            values = MapYamlSection.normalizeMap(values);
        }
    }

    private static final class Counter {

        private int value;
    }
}
