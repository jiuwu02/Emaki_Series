package emaki.jiuwu.craft.corelib.editor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.item.ConfiguredItemDefinition;
import emaki.jiuwu.craft.corelib.api.item.ItemComponentPatch;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.editor.EditorSession.MenuRef;
import emaki.jiuwu.craft.corelib.gui.GuiItemBuilder;
import emaki.jiuwu.craft.corelib.gui.GuiSlot;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.item.ConfiguredItemService;
import emaki.jiuwu.craft.corelib.service.AbstractMessageService;

public final class EditorRenderer {

    public static final int PAGE_SIZE = 21;
    public static final String STRUCT_ID_PREFIX = "struct:";

    private static final int DETAIL_LIMIT = 8;
    private static final int INLINE_LIMIT = 80;

    private final AbstractMessageService messages;
    private final EditorModule module;

    public EditorRenderer(AbstractMessageService messages, EditorModule module) {
        this.messages = messages;
        this.module = module;
    }

    public List<EditorFieldSpec> specs(EditorSession session, MenuRef ref) {
        if (ref == null) {
            return List.of();
        }
        YamlTextDocument draft = session.draft(ref.scope());
        if (ref.structural()) {
            return structuralSpecs(draft, ref.path(), List.of());
        }
        List<EditorFieldSpec> curated = module.fields(session, ref.menuId());
        EditorMenuMeta meta = module.meta(ref.menuId());
        if (meta == null || meta.structuralRoot() == null) {
            return curated;
        }
        List<EditorFieldSpec> combined = new ArrayList<>(curated);
        combined.addAll(structuralSpecs(draft, meta.structuralRoot(), curated));
        return combined;
    }

    public static List<EditorFieldSpec> structuralSpecs(YamlTextDocument draft,
            String[] base,
            List<EditorFieldSpec> curated) {
        if (draft == null || base == null) {
            return List.of();
        }
        Object node = draft.valueLenient(base);
        if (!(node instanceof Map<?, ?> map)) {
            return List.of();
        }
        List<EditorFieldSpec> specs = new ArrayList<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = Texts.toStringSafe(entry.getKey());
            if (key.isEmpty() || covered(curated, base, key)) {
                continue;
            }
            String[] path = append(base, key);
            String id = STRUCT_ID_PREFIX + String.join(".", path);
            String labelKey = "editor.field." + key;
            Object value = entry.getValue();
            if (value instanceof Boolean) {
                specs.add(EditorFieldSpec.toggle(id, labelKey, path));
            } else if (value instanceof Number) {
                specs.add(EditorFieldSpec.number(id, labelKey, path));
            } else if (value instanceof Map<?, ?>) {
                specs.add(EditorFieldSpec.navigate(id, labelKey, "#" + String.join(".", path)));
            } else if (value instanceof List<?>) {
                specs.add(EditorFieldSpec.list(id, labelKey,
                        EditorService.MENU_LIST_ENTRIES, String.join(".", path)));
            } else {
                specs.add(EditorFieldSpec.text(id, labelKey, path));
            }
        }
        return specs;
    }

    private static boolean covered(List<EditorFieldSpec> curated, String[] base, String key) {
        for (EditorFieldSpec spec : curated) {
            if (spec.path() != null && matchesAt(spec.path(), base, key)) {
                return true;
            }
            if (spec.listPath() != null && matchesAt(spec.listPath().split("\\."), base, key)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesAt(String[] path, String[] base, String key) {
        if (path == null || path.length <= base.length) {
            return false;
        }
        for (int index = 0; index < base.length; index++) {
            if (!base[index].equals(path[index])) {
                return false;
            }
        }
        return key.equals(path[base.length]);
    }

    private static String[] append(String[] base, String key) {
        String[] result = new String[base.length + 1];
        System.arraycopy(base, 0, result, 0, base.length);
        result[base.length] = key;
        return result;
    }

    public List<EditorField> fields(EditorSession session, MenuRef ref) {
        String listPath = null;
        if (ref != null && EditorService.MENU_LIST_ENTRIES.equals(ref.menuId())) {
            String contextPath = session.context(EditorService.CONTEXT_LIST_PATH);
            if (contextPath != null && !contextPath.isBlank()) {
                listPath = contextPath;
            }
        }
        if (listPath == null) {
            List<EditorFieldSpec> specs = specs(session, ref);
            List<EditorField> fields = new ArrayList<>(specs.size());
            for (EditorFieldSpec spec : specs) {
                fields.add(toField(session, ref, spec));
            }
            if (ref != null) {
                fields.addAll(module.extraFields(session, ref.menuId()));
            }
            return fields;
        }
        YamlTextDocument draft = session.draft(ref.scope());
        Object target = draft.valueLenient(listPath.split("\\."));
        if (target instanceof Map<?, ?> map) {
            List<EditorField> fields = new ArrayList<>(map.size());
            int position = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                fields.add(new EditorField(
                        "entry_" + position,
                        EditorField.Kind.COMMAND,
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
        List<EditorField> fields = new ArrayList<>(items.size());
        for (int index = 0; index < items.size(); index++) {
            fields.add(new EditorField(
                    "entry_" + index,
                    EditorField.Kind.COMMAND,
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

    public int entryCount(EditorSession session, MenuRef ref) {
        return fields(session, ref).size();
    }

    public ItemStack render(EditorSession session, MenuRef ref, GuiTemplate.ResolvedSlot resolvedSlot) {
        if (resolvedSlot == null || resolvedSlot.definition() == null) {
            return null;
        }
        GuiSlot slot = resolvedSlot.definition();
        String type = Texts.lower(slot.type());
        if (EditorService.TYPE_FIELD_ENTRY.equals(type)) {
            return renderField(session, ref, slot, resolvedSlot.slotIndex());
        }
        if (EditorService.TYPE_PAGE_INFO.equals(type)) {
            return renderPageInfo(session, ref, slot);
        }
        if (EditorService.TYPE_CONFIRM.equals(type)) {
            if (EditorService.MENU_LIST_ENTRIES.equals(ref.menuId())) {
                return buildStatic(slot);
            }
            ItemStack custom = module.renderCustomSlot(session, ref.menuId(), resolvedSlot);
            return custom != null ? custom : filler();
        }
        if (EditorService.TYPE_PAGE_PREV.equals(type)) {
            return session.page() > 0 ? buildStatic(slot) : filler();
        }
        if (EditorService.TYPE_PAGE_NEXT.equals(type)) {
            int pages = Math.max(1, (entryCount(session, ref) + PAGE_SIZE - 1) / PAGE_SIZE);
            return session.page() < pages - 1 ? buildStatic(slot) : filler();
        }
        ItemStack custom = module.renderCustomSlot(session, ref.menuId(), resolvedSlot);
        return custom != null ? custom : buildStatic(slot);
    }

    public String titleText(EditorSession session, MenuRef ref) {
        if (ref == null) {
            return "";
        }
        if (ref.structural()) {
            String last = ref.path().length == 0 ? "" : ref.path()[ref.path().length - 1];
            return messages.messageOrFallback("editor.field." + last, last);
        }
        return messages.message(module.titleKey(ref.menuId()));
    }

    private ItemStack renderPageInfo(EditorSession session, MenuRef ref, GuiSlot slot) {
        int totalEntries = entryCount(session, ref);
        Map<String, Object> replacements = new LinkedHashMap<>();
        replacements.put(EditorService.KEY_CURRENT_PAGE, session.page() + 1);
        replacements.put(EditorService.KEY_TOTAL_PAGES, Math.max(1, (totalEntries + PAGE_SIZE - 1) / PAGE_SIZE));
        replacements.put(EditorService.KEY_MENU_TITLE, titleText(session, ref));
        replacements.put(EditorService.KEY_ENTRY_COUNT, totalEntries);
        return GuiItemBuilder.build(slot.itemDefinition(), replacements, configuredItems());
    }

    private ItemStack renderField(EditorSession session, MenuRef ref, GuiSlot slot, int slotIndex) {
        List<EditorField> fields = fields(session, ref);
        int index = session.page() * PAGE_SIZE + slotIndex;
        if (index < 0 || index >= fields.size()) {
            return filler();
        }
        EditorField field = fields.get(index);
        List<String> lore = new ArrayList<>();
        String description = field.description() != null
                ? field.description()
                : messages.messageOrFallback(field.labelKey() + "_desc", null);
        if (description != null && !description.isBlank()) {
            lore.add(description);
        }
        List<String> valueLines = field.valueLines().isEmpty() ? List.of("-") : field.valueLines();
        lore.add(messages.message("editor.field.value", Map.of("value", valueLines.get(0))));
        for (int line = 1; line < valueLines.size(); line++) {
            lore.add(valueLines.get(line));
        }
        lore.add(field.enabled()
                ? messages.message(fieldHintKey(session, ref, field))
                : messages.message("editor.field.disabled"));
        String label = field.displayName() != null
                ? field.displayName()
                : messages.message(field.labelKey());
        String icon = field.icon() != null ? field.icon() : iconFor(session, ref, field);
        return GuiItemBuilder.build(
                slot,
                icon,
                label,
                lore,
                Map.of(),
                configuredItems());
    }

    private String iconFor(EditorSession session, MenuRef ref, EditorField field) {
        EditorFieldSpec spec = spec(session, ref, field.id());
        if (spec != null && spec.icon() != null) {
            return materialSource(spec.icon());
        }
        Material moduleIcon = module.fieldIcon(field);
        if (moduleIcon != null) {
            return materialSource(moduleIcon);
        }
        return defaultIcon(field.kind());
    }

    public static String materialSource(Material material) {
        return "minecraft-" + material.name().toLowerCase(Locale.ROOT);
    }

    private static String defaultIcon(EditorField.Kind kind) {
        return switch (kind) {
            case TOGGLE -> "minecraft-lever";
            case CYCLE -> "minecraft-comparator";
            case NUMBER -> "minecraft-clock";
            case TEXT -> "minecraft-book";
            case NAVIGATE -> "minecraft-oak_door";
            case LIST -> "minecraft-chest";
            case COMMAND -> "minecraft-paper";
        };
    }

    public EditorFieldSpec spec(EditorSession session, MenuRef ref, String fieldId) {
        for (EditorFieldSpec candidate : specs(session, ref)) {
            if (candidate.id().equals(fieldId)) {
                return candidate;
            }
        }
        return null;
    }

    private String fieldHintKey(EditorSession session, MenuRef ref, EditorField field) {
        if (field.kind() != EditorField.Kind.LIST) {
            return "editor.field.hint." + field.kind().name().toLowerCase(Locale.ROOT);
        }
        try {
            for (EditorFieldSpec spec : specs(session, ref)) {
                if (spec.id().equals(field.id()) && spec.listPath() != null) {
                    Object value = session.draft(ref.scope()).valueLenient(spec.listPath().split("\\."));
                    return "editor.field.hint." + (value instanceof Map<?, ?> ? "list_map" : "list_seq");
                }
            }
        } catch (RuntimeException failure) {
            Logger.getLogger("EmakiCoreLib").fine("[editor] 列表提示键解析失败: " + failure);
        }
        return "editor.field.hint.list";
    }

    public ItemStack filler() {
        ConfiguredItemDefinition definition = new ConfiguredItemDefinition(
                "minecraft-gray_stained_glass_pane", 1, Map.of(
                        "minecraft:tooltip_display",
                        ItemComponentPatch.set(
                                Map.of("hide_tooltip", true))));
        return GuiItemBuilder.build(definition, Map.of(), configuredItems());
    }

    public ItemStack buildStatic(GuiSlot slot) {
        return GuiItemBuilder.build(slot.itemDefinition(), Map.of(), configuredItems());
    }

    private static ConfiguredItemService configuredItems() {
        return EmakiCoreLibPlugin.lookup().configuredItemService();
    }

    private EditorField toField(EditorSession session, MenuRef ref, EditorFieldSpec spec) {
        if (spec.kind() == EditorField.Kind.NAVIGATE || spec.kind() == EditorField.Kind.LIST
                || spec.kind() == EditorField.Kind.COMMAND) {
            return new EditorField(spec.id(), spec.kind(), spec.labelKey(), null, null, null,
                    List.of(""), List.of(), -1, true);
        }
        YamlTextDocument draft = session.draft(ref.scope());
        Object value = draft.valueLenient(spec.path());
        EditorField field = new EditorField(
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
        List<String> override = module.valueLines(session, field, draft);
        return override == null ? field : field.withValueLines(override);
    }

    public static String describeValue(Object value) {
        if (value == null) {
            return "-";
        }
        if (value instanceof List<?> list) {
            if (list.isEmpty()) {
                return "-";
            }
            return truncate(String.join(" | ",
                    list.stream().map(EditorRenderer::describeScalar).toList()));
        }
        if (value instanceof Map<?, ?> map) {
            if (map.isEmpty()) {
                return "-";
            }
            return truncate(String.join(" | ", map.entrySet().stream()
                    .map(entry -> describeScalar(entry.getKey()) + "=" + describeScalar(entry.getValue()))
                    .toList()));
        }
        return truncate(describeScalar(value));
    }

    private static String describeScalar(Object value) {
        String text = Texts.toStringSafe(value);
        return text.isBlank() ? "-" : text;
    }

    private static String truncate(String text) {
        return text.length() > 60 ? text.substring(0, 60) + "..." : text;
    }

    public List<String> describeValueLines(Object value) {
        if (value == null) {
            return List.of(messages.message("editor.value.unset"));
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
            lines.add(messages.message("editor.value.list_header", Map.of("count", list.size())));
            int limit = Math.min(list.size(), DETAIL_LIMIT);
            for (int index = 0; index < limit; index++) {
                lines.add("  · " + truncateTo(inline(list.get(index)), INLINE_LIMIT));
            }
            appendRemaining(lines, list.size() - limit);
            return lines;
        }
        if (value instanceof Map<?, ?> map) {
            List<String> lines = new ArrayList<>();
            lines.add(messages.message("editor.value.map_header", Map.of("count", map.size())));
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
        return messages.message("editor.value.scalar", Map.of(
                "type", messages.message(typeKey),
                "value", value));
    }

    private void appendRemaining(List<String> lines, int remaining) {
        if (remaining > 0) {
            lines.add("  · " + messages.message("editor.value.more", Map.of("count", remaining)));
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
}
