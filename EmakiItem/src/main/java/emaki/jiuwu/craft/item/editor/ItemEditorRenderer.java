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

    public ItemEditorRenderer(EmakiItemPlugin plugin) {
        this.plugin = plugin;
    }

    private List<ItemEditorField> adaptiveComponents(ItemEditorSession session) {
        String material = materialOf(session);
        List<ItemEditorField> fields = new ArrayList<>();
        for (emaki.jiuwu.craft.corelib.item.MinecraftItemComponentCatalog.Entry entry : CATALOG.specializedFor(material)) {
            if (emaki.jiuwu.craft.corelib.item.ProfileComponentSupport.PROFILE_COMPONENT_ID
                    .equals(entry.componentId())) {
                fields.add(new ItemEditorField(SKIN_FIELD_ID, ItemEditorField.Kind.COMMAND,
                        "editor.field.skin_edit", skinSummary(session), List.of(), -1, true));
                continue;
            }
            fields.add(new ItemEditorField("component_" + entry.componentId(), ItemEditorField.Kind.TEXT,
                    entry.componentId(), entry.valueFormat(), List.of(), -1, true));
        }
        return fields;
    }

    static String materialOf(ItemEditorSession session) {
        Object source = session.draft().value("item", "source");
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
                        setId, setId, List.of(), -1, true));
            }
            return fields;
        }
        String listPath = session.context("list_path");
        if (listPath == null || listPath.isBlank()) {
            List<ItemEditorFieldSpec> specs = ItemEditorMenus.specs(menuId, session);
            List<ItemEditorField> fields = new ArrayList<>(specs.size());
            for (ItemEditorFieldSpec spec : specs) {
                fields.add(toField(session, menuId, spec));
            }
            if (ItemEditorMenus.COMPONENTS.equals(menuId)) {
                fields.addAll(adaptiveComponents(session));
            }
            return fields;
        }
        Object target = session.draftFor(menuId).value(listPath.split("\\."));
        if (target instanceof Map<?, ?> map) {
            List<ItemEditorField> fields = new ArrayList<>(map.size());
            int position = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                fields.add(new ItemEditorField(
                        "entry_" + position,
                        ItemEditorField.Kind.COMMAND,
                        "editor.field.entry",
                        Texts.toStringSafe(entry.getKey()) + " -> " + describe(entry.getValue()),
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
                    "editor.field.entry",
                    describe(items.get(index)),
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
        return buildStatic(slot);
    }

    private ItemStack renderField(ItemEditorSession session, String menuId, GuiSlot slot, int slotIndex) {
        List<ItemEditorField> fields = fields(session, menuId);
        int index = session.page() * pageSize() + slotIndex;
        if (index < 0 || index >= fields.size()) {
            return buildStatic(slot);
        }
        ItemEditorField field = fields.get(index);
        return GuiItemBuilder.build(
                slot,
                fieldIcon(field),
                plugin.messageService().message(field.labelKey()),
                List.of(
                        plugin.messageService().message("editor.field.value", Map.of("value", field.valueKey())),
                        field.enabled()
                                ? plugin.messageService().message("editor.field.hint." + field.kind().name().toLowerCase())
                                : plugin.messageService().message("editor.field.disabled")),
                Map.of(),
                plugin.coreLib().configuredItemService());
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
        return switch (field.kind()) {
            case TOGGLE -> "minecraft:lever";
            case CYCLE -> "minecraft:comparator";
            case NUMBER -> "minecraft:clock";
            case TEXT -> "minecraft:book";
            case NAVIGATE -> "minecraft:oak_door";
            case LIST -> "minecraft:chest";
            case COMMAND -> "minecraft:paper";
        };
    }

    private static String describe(Object value) {
        if (value == null) {
            return "-";
        }
        String text = Texts.toStringSafe(value);
        return text.length() > 40 ? text.substring(0, 40) + "..." : text;
    }

    private static ItemEditorField toField(ItemEditorSession session, String menuId, ItemEditorFieldSpec spec) {
        if (spec.kind() == ItemEditorField.Kind.NAVIGATE || spec.kind() == ItemEditorField.Kind.LIST) {
            return new ItemEditorField(spec.id(), spec.kind(), spec.labelKey(), "", List.of(), -1, true);
        }
        Object value = session.draftFor(menuId).value(spec.path());
        return new ItemEditorField(
                spec.id(),
                spec.kind(),
                spec.labelKey(),
                describe(value),
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
