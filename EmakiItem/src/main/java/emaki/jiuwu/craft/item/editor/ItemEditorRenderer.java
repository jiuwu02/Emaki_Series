package emaki.jiuwu.craft.item.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.gui.GuiItemBuilder;
import emaki.jiuwu.craft.corelib.gui.GuiSlot;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.item.EmakiItemPlugin;

public final class ItemEditorRenderer {

    private final EmakiItemPlugin plugin;

    private static final emaki.jiuwu.craft.corelib.item.MinecraftItemComponentCatalog CATALOG =
            new emaki.jiuwu.craft.corelib.item.MinecraftItemComponentCatalog();

    public static final String SKIN_FIELD_ID = "skin_edit";

    public static final String COMPONENT_FIELD_PREFIX = "component_";
    public static final String CONTEXT_COMPONENT_FILTER = "component_filter";

    private static final List<String> RARITY_OPTIONS = List.of("common", "uncommon", "rare", "epic");
    private static final int DETAIL_LIMIT = 8;
    private static final int INLINE_LIMIT = 80;
    private static final java.util.Set<String> LIST_COMPONENTS = java.util.Set.of(
            "minecraft:lore",
            "minecraft:enchantments",
            "minecraft:attribute_modifiers",
            "minecraft:custom_model_data",
            "minecraft:stored_enchantments",
            "minecraft:banner_patterns",
            "minecraft:recipes",
            "minecraft:charged_projectiles",
            "minecraft:bundle_contents",
            "minecraft:container",
            "minecraft:bees",
            "minecraft:pot_decorations");

    public ItemEditorRenderer(EmakiItemPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 统一的组件列表：通用组件在前、当前材料专属组件在后，严格区分两组；
     * 名称 / 描述 / 图标全部取自 CoreLib 组件目录，EmakiItem 侧不再维护组件 i18n 与图标。
     */
    private List<ItemEditorField> componentFields(ItemEditorSession session) {
        List<emaki.jiuwu.craft.corelib.item.MinecraftItemComponentCatalog.Entry> entries = new ArrayList<>();
        entries.addAll(CATALOG.universalEntries());
        entries.addAll(CATALOG.specializedFor(materialOf(session)));
        String filter = Texts.lower(Texts.toStringSafe(session.context(CONTEXT_COMPONENT_FILTER))).trim();
        String serverVersion = serverVersion();
        List<ItemEditorField> fields = new ArrayList<>();
        for (emaki.jiuwu.craft.corelib.item.MinecraftItemComponentCatalog.Entry entry : entries) {
            if (!entry.applicableTo(serverVersion)) {
                continue;
            }
            if (!filter.isEmpty() && !matchesFilter(entry, filter)) {
                continue;
            }
            String componentId = entry.componentId();
            if (emaki.jiuwu.craft.corelib.item.ProfileComponentSupport.PROFILE_COMPONENT_ID.equals(componentId)) {
                fields.add(new ItemEditorField(SKIN_FIELD_ID, ItemEditorField.Kind.COMMAND, null,
                        plugin.messageService().message("editor.field.skin_edit"),
                        plugin.messageService().messageOrFallback("editor.field.skin_edit_desc", null),
                        entry.iconSource(), List.of(skinSummary(session)), List.of(), -1, true));
                continue;
            }
            ItemEditorField.Kind kind = editorKind(entry);
            Object current = session.draft().valueLenient("item", "components", componentDraftKey(componentId));
            fields.add(new ItemEditorField(
                    COMPONENT_FIELD_PREFIX + componentId,
                    kind,
                    null,
                    entry.displayNameOrId(),
                    componentDescription(entry),
                    entry.iconSource(),
                    describeValueLines(current),
                    kind == ItemEditorField.Kind.CYCLE ? RARITY_OPTIONS : List.of(),
                    -1,
                    true));
        }
        return fields;
    }

    /** 组件描述；若组件有最低版本要求，则在描述后追加版本提示行。 */
    private String componentDescription(emaki.jiuwu.craft.corelib.item.MinecraftItemComponentCatalog.Entry entry) {
        String description = entry.descriptionText();
        String requirement = entry.versionRequirement();
        if (requirement.isBlank()) {
            return description;
        }
        String note = plugin.messageService().message("editor.value.version", Map.of("version", requirement));
        return description.isBlank() ? note : description + "<newline>" + note;
    }

    private String serverVersion() {
        try {
            return plugin.getServer() == null ? "" : Texts.toStringSafe(plugin.getServer().getMinecraftVersion());
        } catch (Throwable unavailable) {
            return "";
        }
    }

    private static boolean matchesFilter(emaki.jiuwu.craft.corelib.item.MinecraftItemComponentCatalog.Entry entry,
            String filter) {
        return Texts.lower(entry.componentId()).contains(filter)
                || Texts.lower(entry.displayNameOrId()).contains(filter);
    }

    /** 组件在物品 YAML 中的规范键：minecraft 命名空间省略前缀，与物品配置惯例一致。 */
    public static String componentDraftKey(String componentId) {
        return componentId != null && componentId.startsWith("minecraft:")
                ? componentId.substring("minecraft:".length())
                : componentId;
    }

    private static ItemEditorField.Kind editorKind(
            emaki.jiuwu.craft.corelib.item.MinecraftItemComponentCatalog.Entry entry) {
        if (entry.nonValued()) {
            return ItemEditorField.Kind.TOGGLE;
        }
        if ("minecraft:rarity".equals(entry.componentId())) {
            return ItemEditorField.Kind.CYCLE;
        }
        String format = Texts.lower(entry.valueFormat());
        if (format.startsWith("boolean")) {
            return ItemEditorField.Kind.TOGGLE;
        }
        if (format.contains("integer") || format.contains("number")) {
            return ItemEditorField.Kind.NUMBER;
        }
        if (LIST_COMPONENTS.contains(entry.componentId())) {
            return ItemEditorField.Kind.LIST;
        }
        return ItemEditorField.Kind.TEXT;
    }

    static String materialOf(ItemEditorSession session) {
        Object source = session.draft().value("item", "item_source");
        if (source == null) {
            source = session.draft().value("item", "source");
        }
        String raw = Texts.toStringSafe(source).trim();
        if (raw.isEmpty()) {
            return "";
        }
        return raw.startsWith("minecraft-") ? "minecraft:" + raw.substring("minecraft-".length()) : raw;
    }

    static String skinSummary(ItemEditorSession session) {
        Object profile = session.draft().value("item", "components",
                emaki.jiuwu.craft.corelib.item.ProfileComponentSupport.PROFILE_COMPONENT_ID);
        String url = emaki.jiuwu.craft.corelib.item.ProfileComponentSupport.textureUrlOf(profile);
        if (url != null) {
            return url;
        }
        String value = emaki.jiuwu.craft.corelib.item.ProfileComponentSupport.textureValueOf(profile);
        if (value != null) {
            return value.length() > 32 ? value.substring(0, 32) + "..." : value;
        }
        return profile instanceof Map<?, ?> map && map.get("name") != null
                ? Texts.toStringSafe(map.get("name"))
                : "-";
    }

    public List<ItemEditorField> fields(ItemEditorSession session, String menuId) {
        if (ItemEditorMenus.SET_LIST.equals(menuId)) {
            List<ItemEditorField> fields = new ArrayList<>();
            for (String setId : setIds()) {
                fields.add(new ItemEditorField("set_" + setId, ItemEditorField.Kind.COMMAND,
                        setId, null, null, null, List.of(setId), List.of(), -1, true));
            }
            return fields;
        }
        String listPath = ItemEditorMenus.LIST_ENTRIES.equals(menuId) ? session.context("list_path") : null;
        if (listPath == null || listPath.isBlank()) {
            List<ItemEditorFieldSpec> specs = ItemEditorMenus.specs(menuId, session);
            List<ItemEditorField> fields = new ArrayList<>(specs.size());
            for (ItemEditorFieldSpec spec : specs) {
                fields.add(toField(session, menuId, spec));
            }
            if (ItemEditorMenus.COMPONENTS.equals(menuId)) {
                fields.addAll(componentFields(session));
            }
            return fields;
        }
        Object target = session.draftFor(menuId).valueLenient(listPath.split("\\."));
        if (target instanceof Map<?, ?> map) {
            List<ItemEditorField> fields = new ArrayList<>(map.size());
            int position = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                fields.add(new ItemEditorField(
                        "entry_" + position,
                        ItemEditorField.Kind.COMMAND,
                        "#" + position + " " + describeScalar(entry.getKey()),
                        null,
                        null,
                        null,
                        List.of(describeValue(entry.getValue())),
                        List.of(Texts.toStringSafe(entry.getKey())),
                        position,
                        true));
                position++;
            }
            return fields;
        }
        List<Object> items = new ArrayList<>();
        if (target instanceof List<?> list) {
            items.addAll(list);
        }
        List<ItemEditorField> fields = new ArrayList<>(items.size());
        for (int index = 0; index < items.size(); index++) {
            fields.add(new ItemEditorField(
                    "entry_" + index,
                    ItemEditorField.Kind.COMMAND,
                    "#" + index,
                    null,
                    null,
                    null,
                    List.of(describeValue(items.get(index))),
                    List.of(),
                    index,
                    true));
        }
        return fields;
    }

    public int entryCount(ItemEditorSession session, String menuId) {
        return fields(session, menuId).size();
    }

    public ItemStack render(ItemEditorSession session, String menuId, GuiTemplate.ResolvedSlot resolvedSlot) {
        if (resolvedSlot == null || resolvedSlot.definition() == null) {
            return null;
        }
        GuiSlot slot = resolvedSlot.definition();
        String type = Texts.lower(slot.type());
        if (ItemEditorGuiService.TYPE_FIELD_ENTRY.equals(type)) {
            return renderField(session, menuId, slot, resolvedSlot.slotIndex());
        }
        if (ItemEditorGuiService.TYPE_PREVIEW.equals(type)) {
            return renderPreview(session, slot);
        }
        if (ItemEditorGuiService.TYPE_FILE_PATH.equals(type)) {
            return GuiItemBuilder.build(slot.itemDefinition(),
                    Map.of("file_path", String.valueOf(session.document().path())),
                    plugin.coreLib().configuredItemService());
        }
        if (ItemEditorGuiService.TYPE_PAGE_INFO.equals(type)) {
            return renderPageInfo(session, menuId, slot);
        }
        if (ItemEditorGuiService.TYPE_CONFIRM.equals(type)) {
            if (ItemEditorMenus.COMPONENTS.equals(menuId)) {
                return renderComponentSearch(session);
            }
            return ItemEditorMenus.LIST_ENTRIES.equals(menuId) ? buildStatic(slot) : filler();
        }
        if (ItemEditorGuiService.TYPE_PAGE_PREV.equals(type)) {
            return session.page() > 0 ? buildStatic(slot) : filler();
        }
        if (ItemEditorGuiService.TYPE_PAGE_NEXT.equals(type)) {
            int pages = Math.max(1, (entryCount(session, menuId) + pageSize() - 1) / pageSize());
            return session.page() < pages - 1 ? buildStatic(slot) : filler();
        }
        return buildStatic(slot);
    }

    private ItemStack renderComponentSearch(ItemEditorSession session) {
        String filter = Texts.toStringSafe(session.context(CONTEXT_COMPONENT_FILTER)).trim();
        List<String> lore = new ArrayList<>();
        lore.add(plugin.messageService().message("editor.field.component_search_desc"));
        lore.add(plugin.messageService().message("editor.field.component_search_value", Map.of(
                "filter", filter.isEmpty()
                        ? plugin.messageService().message("editor.component.search_empty")
                        : filter)));
        emaki.jiuwu.craft.corelib.api.item.ConfiguredItemDefinition definition =
                new emaki.jiuwu.craft.corelib.api.item.ConfiguredItemDefinition("minecraft-compass", 1, Map.of(
                        "minecraft:custom_name",
                        emaki.jiuwu.craft.corelib.api.item.ItemComponentPatch.set(
                                plugin.messageService().message("editor.field.component_search")),
                        "minecraft:lore",
                        emaki.jiuwu.craft.corelib.api.item.ItemComponentPatch.set(lore)));
        return GuiItemBuilder.build(definition, Map.of(), plugin.coreLib().configuredItemService());
    }

    private ItemStack filler() {
        emaki.jiuwu.craft.corelib.api.item.ConfiguredItemDefinition definition =
                new emaki.jiuwu.craft.corelib.api.item.ConfiguredItemDefinition(
                        "minecraft-gray_stained_glass_pane", 1, Map.of(
                                "minecraft:tooltip_display",
                                emaki.jiuwu.craft.corelib.api.item.ItemComponentPatch.set(
                                        Map.of("hide_tooltip", true))));
        return GuiItemBuilder.build(definition, Map.of(), plugin.coreLib().configuredItemService());
    }

    private ItemStack renderPageInfo(ItemEditorSession session, String menuId, GuiSlot slot) {
        int totalEntries = entryCount(session, menuId);
        Map<String, Object> replacements = new java.util.LinkedHashMap<>();
        replacements.put(ItemEditorGuiService.KEY_CURRENT_PAGE, session.page() + 1);
        replacements.put(ItemEditorGuiService.KEY_TOTAL_PAGES, Math.max(1, (totalEntries + 20) / 21));
        replacements.put(ItemEditorGuiService.KEY_MENU_TITLE,
                plugin.messageService().message(ItemEditorMenus.titleKey(menuId)));
        replacements.put(ItemEditorGuiService.KEY_ENTRY_COUNT, totalEntries);
        return GuiItemBuilder.build(slot.itemDefinition(), replacements,
                plugin.coreLib().configuredItemService());
    }

    private ItemStack renderField(ItemEditorSession session, String menuId, GuiSlot slot, int slotIndex) {
        List<ItemEditorField> fields = fields(session, menuId);
        int index = session.page() * pageSize() + slotIndex;
        if (index < 0 || index >= fields.size()) {
            return filler();
        }
        ItemEditorField field = fields.get(index);
        List<String> lore = new ArrayList<>();
        String description = field.description() != null
                ? field.description()
                : plugin.messageService().messageOrFallback(field.labelKey() + "_desc", null);
        if (description != null && !description.isBlank()) {
            lore.add(description);
        }
        List<String> valueLines = field.valueLines().isEmpty() ? List.of("-") : field.valueLines();
        lore.add(plugin.messageService().message("editor.field.value", Map.of("value", valueLines.get(0))));
        for (int line = 1; line < valueLines.size(); line++) {
            lore.add(valueLines.get(line));
        }
        lore.add(field.enabled()
                ? plugin.messageService().message(fieldHintKey(session, menuId, field))
                : plugin.messageService().message("editor.field.disabled"));
        String label = field.displayName() != null
                ? field.displayName()
                : plugin.messageService().message(field.labelKey());
        String icon = field.icon() != null ? field.icon() : fieldIcon(field);
        return GuiItemBuilder.build(
                slot,
                icon,
                label,
                lore,
                Map.of(),
                plugin.coreLib().configuredItemService());
    }

    private String fieldHintKey(ItemEditorSession session, String menuId, ItemEditorField field) {
        if (field.kind() != ItemEditorField.Kind.LIST) {
            return "editor.field.hint." + field.kind().name().toLowerCase();
        }
        try {
            for (ItemEditorFieldSpec spec : ItemEditorMenus.specs(menuId, session)) {
                if (spec.id().equals(field.id()) && spec.listPath() != null) {
                    Object value = session.draftFor(menuId).valueLenient(spec.listPath().split("\\."));
                    return "editor.field.hint." + (value instanceof Map<?, ?> ? "list_map" : "list_seq");
                }
            }
        } catch (RuntimeException ignored) {
        }
        return "editor.field.hint.list";
    }

    private ItemStack renderPreview(ItemEditorSession session, GuiSlot slot) {
        try {
            return plugin.itemFactory().rebuildBase(
                    new emaki.jiuwu.craft.item.model.EmakiItemDefinitionParser(plugin.getLogger())
                            .parse(emaki.jiuwu.craft.corelib.api.yaml.YamlFiles.load(session.draft().text()),
                                    session.document().path().toString()),
                    1);
        } catch (RuntimeException failure) {
            return buildStatic(slot);
        }
    }

    private ItemStack buildStatic(GuiSlot slot) {
        return GuiItemBuilder.build(slot.itemDefinition(), Map.of(), plugin.coreLib().configuredItemService());
    }

    private int pageSize() {
        return 21;
    }

    private static String fieldIcon(ItemEditorField field) {
        String specific = specificIcon(field.id());
        if (specific != null) {
            return specific;
        }
        return switch (field.kind()) {
            case TOGGLE -> "minecraft-lever";
            case CYCLE -> "minecraft-comparator";
            case NUMBER -> "minecraft-clock";
            case TEXT -> "minecraft-book";
            case NAVIGATE -> "minecraft-oak_door";
            case LIST -> "minecraft-chest";
            case COMMAND -> "minecraft-paper";
        };
    }

    private static String specificIcon(String fieldId) {
        return switch (fieldId) {
            case "equip_slot" -> "minecraft-armor_stand";
            case "item_source" -> "minecraft-grass_block";
            case "update_enabled" -> "minecraft-redstone_torch";
            case "preserve_amount" -> "minecraft-bundle";
            case "preserve_damage" -> "minecraft-anvil";
            case "preserve_unknown" -> "minecraft-shulker_box";
            case "trigger_join" -> "minecraft-oak_door";
            case "trigger_held_change" -> "minecraft-iron_sword";
            case "trigger_inventory_click" -> "minecraft-chest";
            case "trigger_inventory_drag" -> "minecraft-rail";
            case "trigger_pickup" -> "minecraft-hopper";
            case "trigger_interact" -> "minecraft-lever";
            case "trigger_command" -> "minecraft-command_block";
            case "set_id", "set_display_name" -> "minecraft-name_tag";
            case "set_piece", "set_pieces", "set_lore_equipped" -> "minecraft-iron_chestplate";
            case "manage_sets" -> "minecraft-bookshelf";
            case "set_thresholds" -> "minecraft-ladder";
            case "set_lore_header" -> "minecraft-writable_book";
            case "set_lore_missing" -> "minecraft-gray_dye";
            case "set_lore_active" -> "minecraft-lime_dye";
            case "set_lore_inactive" -> "minecraft-light_gray_dye";
            case "set_lore_separator" -> "minecraft-stick";
            case "condition_type", "cond_type", "mat_matcher_type" -> "minecraft-comparator";
            case "condition_required_count", "cond_required_count", "mat_amount" -> "minecraft-clock";
            case "condition_invalid" -> "minecraft-redstone_torch";
            case "condition_fail_message" -> "minecraft-oak_sign";
            case "condition_block_output" -> "minecraft-redstone_block";
            case "condition_entries", "cond_nested", "mat_item_sources" -> "minecraft-chest";
            case "condition_pass_actions", "repair_on_repaired" -> "minecraft-lime_dye";
            case "condition_fail_actions", "repair_on_disabled" -> "minecraft-red_dye";
            case "cond_expression", "mat_matcher_value" -> "minecraft-book";
            case "repair_enabled" -> "minecraft-anvil";
            case "repair_name_prefix" -> "minecraft-name_tag";
            case "repair_materials" -> "minecraft-iron_ingot";
            case "repair_economy" -> "minecraft-gold_ingot";
            case "repair_lore_append" -> "minecraft-writable_book";
            case "repair_economy_enabled" -> "minecraft-redstone_torch";
            case "repair_economy_restore", "mat_restore" -> "minecraft-experience_bottle";
            case "repair_currencies" -> "minecraft-gold_nugget";
            case "mat_matcher_component" -> "minecraft-name_tag";
            case "mat_matcher_operator" -> "minecraft-redstone_torch";
            case "effects_list", "effect_type" -> "minecraft-nether_star";
            case "effect_variables" -> "minecraft-paper";
            case "effect_attributes" -> "minecraft-iron_axe";
            case "effect_skills" -> "minecraft-blaze_rod";
            case "effect_skill_triggers" -> "minecraft-tripwire_hook";
            case "effect_accessory_slots" -> "minecraft-amethyst_shard";
            case "effect_name_actions" -> "minecraft-name_tag";
            case "effect_lore_actions" -> "minecraft-writable_book";
            case "actions_list" -> "minecraft-redstone";
            case ItemEditorRenderer.SKIN_FIELD_ID -> "minecraft-player_head";
            default -> null;
        };
    }

    static String describeValue(Object value) {
        if (value == null) {
            return "-";
        }
        if (value instanceof List<?> list) {
            if (list.isEmpty()) {
                return "-";
            }
            return truncate(join(list.stream().map(ItemEditorRenderer::describeScalar).toList(), " | "));
        }
        if (value instanceof Map<?, ?> map) {
            if (map.isEmpty()) {
                return "-";
            }
            return truncate(join(map.entrySet().stream()
                    .map(entry -> describeScalar(entry.getKey()) + "=" + describeScalar(entry.getValue()))
                    .toList(), " | "));
        }
        return truncate(describeScalar(value));
    }

    private static String join(List<String> parts, String separator) {
        return String.join(separator, parts);
    }

    private static String describeScalar(Object value) {
        String text = Texts.toStringSafe(value);
        return text.isBlank() ? "-" : text;
    }

    private static String truncate(String text) {
        return text.length() > 60 ? text.substring(0, 60) + "..." : text;
    }

    /**
     * 按数据类型生成“当前值”的多行展示：首行为类型与摘要，其后逐条展开列表/字典内容，
     * 保证 Int、字符串、列表、字典等都能看到类型与具体内容。
     */
    private List<String> describeValueLines(Object value) {
        if (value == null) {
            return List.of(plugin.messageService().message("editor.value.unset"));
        }
        if (value instanceof Boolean bool) {
            return List.of(scalarLine("editor.value.boolean", bool ? "true" : "false"));
        }
        if (value instanceof Number number) {
            boolean integral = number instanceof Integer || number instanceof Long
                    || number instanceof Short || number instanceof Byte;
            return List.of(scalarLine(integral ? "editor.value.integer" : "editor.value.decimal",
                    String.valueOf(number)));
        }
        if (value instanceof String text) {
            return List.of(scalarLine("editor.value.text", text));
        }
        if (value instanceof List<?> list) {
            List<String> lines = new ArrayList<>();
            lines.add(plugin.messageService().message("editor.value.list_header", Map.of("count", list.size())));
            int limit = Math.min(list.size(), DETAIL_LIMIT);
            for (int index = 0; index < limit; index++) {
                lines.add("  · " + truncateTo(inline(list.get(index)), INLINE_LIMIT));
            }
            appendRemaining(lines, list.size() - limit);
            return lines;
        }
        if (value instanceof Map<?, ?> map) {
            List<String> lines = new ArrayList<>();
            lines.add(plugin.messageService().message("editor.value.map_header", Map.of("count", map.size())));
            int shown = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (shown >= DETAIL_LIMIT) {
                    break;
                }
                lines.add("  · " + truncateTo(inline(entry.getKey()), INLINE_LIMIT)
                        + " = " + truncateTo(inline(entry.getValue()), INLINE_LIMIT));
                shown++;
            }
            appendRemaining(lines, map.size() - shown);
            return lines;
        }
        return List.of(scalarLine("editor.value.text", String.valueOf(value)));
    }

    private String scalarLine(String typeKey, String value) {
        return plugin.messageService().message("editor.value.scalar", Map.of(
                "type", plugin.messageService().message(typeKey),
                "value", value));
    }

    private void appendRemaining(List<String> lines, int remaining) {
        if (remaining > 0) {
            lines.add("  · " + plugin.messageService().message("editor.value.more", Map.of("count", remaining)));
        }
    }

    private static String inline(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Boolean bool) {
            return bool ? "true" : "false";
        }
        if (value instanceof String text) {
            return "\"" + text + "\"";
        }
        if (value instanceof Number || value instanceof Character) {
            return String.valueOf(value);
        }
        if (value instanceof List<?> list) {
            StringBuilder builder = new StringBuilder("[");
            for (int index = 0; index < list.size() && index < DETAIL_LIMIT; index++) {
                if (index > 0) {
                    builder.append(", ");
                }
                builder.append(inline(list.get(index)));
            }
            if (list.size() > DETAIL_LIMIT) {
                builder.append(", …");
            }
            return builder.append(']').toString();
        }
        if (value instanceof Map<?, ?> map) {
            StringBuilder builder = new StringBuilder("{");
            int shown = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (shown >= DETAIL_LIMIT) {
                    builder.append(", …");
                    break;
                }
                if (shown > 0) {
                    builder.append(", ");
                }
                builder.append(entry.getKey()).append('=').append(inline(entry.getValue()));
                shown++;
            }
            return builder.append('}').toString();
        }
        return String.valueOf(value);
    }

    private static String truncateTo(String text, int limit) {
        return text.length() > limit ? text.substring(0, limit) + "..." : text;
    }

    private ItemEditorField toField(ItemEditorSession session, String menuId, ItemEditorFieldSpec spec) {
        if (spec.kind() == ItemEditorField.Kind.NAVIGATE || spec.kind() == ItemEditorField.Kind.LIST
                || spec.kind() == ItemEditorField.Kind.COMMAND) {
            return new ItemEditorField(spec.id(), spec.kind(), spec.labelKey(), null, null, null,
                    List.of(""), List.of(), -1, true);
        }
        Object value = session.draftFor(menuId).valueLenient(spec.path());
        return new ItemEditorField(
                spec.id(),
                spec.kind(),
                spec.labelKey(),
                null,
                null,
                null,
                describeValueLines(value),
                spec.options(),
                -1,
                true);
    }

    private java.util.List<String> setIds() {
        java.util.List<String> ids = new ArrayList<>();
        java.io.File directory = new java.io.File(plugin.getDataFolder(), "sets");
        java.io.File[] files = directory.listFiles(
                (dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (files == null) {
            return ids;
        }
        java.util.Arrays.sort(files);
        for (java.io.File file : files) {
            try {
                Object rawId = emaki.jiuwu.craft.corelib.api.yaml.YamlFiles.load(file).asMap().get("id");
                String id = Texts.normalizeId(Texts.toStringSafe(rawId));
                if (!id.isEmpty()) {
                    ids.add(id);
                }
            } catch (RuntimeException ignored) {
            }
        }
        return ids;
    }
}
