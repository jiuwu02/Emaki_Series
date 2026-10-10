package emaki.jiuwu.craft.item.editor;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.descriptor.CoreActionStageDescriptor;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.editor.EditorBrowserRoot;
import emaki.jiuwu.craft.corelib.editor.EditorField;
import emaki.jiuwu.craft.corelib.editor.EditorFieldSpec;
import emaki.jiuwu.craft.corelib.editor.EditorFeedback;
import emaki.jiuwu.craft.corelib.editor.EditorMenuMeta;
import emaki.jiuwu.craft.corelib.editor.EditorModule;
import emaki.jiuwu.craft.corelib.editor.EditorRenderer;
import emaki.jiuwu.craft.corelib.editor.EditorService;
import emaki.jiuwu.craft.corelib.editor.EditorSession;
import emaki.jiuwu.craft.corelib.editor.EditorValidator;
import emaki.jiuwu.craft.corelib.editor.YamlTextDocument;
import emaki.jiuwu.craft.corelib.gui.GuiClickContext;
import emaki.jiuwu.craft.corelib.gui.GuiItemBuilder;
import emaki.jiuwu.craft.corelib.gui.GuiSlot;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.item.MinecraftItemComponentCatalog;
import emaki.jiuwu.craft.corelib.item.ProfileComponentSupport;
import emaki.jiuwu.craft.item.EmakiItemPlugin;
import emaki.jiuwu.craft.item.model.EmakiItemDefinition;
import emaki.jiuwu.craft.item.model.EmakiItemDefinitionParser;

public final class ItemEditorModule implements EditorModule {

    public static final String SKIN_FIELD_ID = "skin_edit";
    public static final String COMPONENT_FIELD_PREFIX = "component_";
    public static final String CONTEXT_COMPONENT_FILTER = "component_filter";

    public static final String TYPE_PREVIEW = "preview";
    public static final String TYPE_FILE_PATH = "file_path";
    public static final String TYPE_GET_ITEM = "get_item";
    public static final String TYPE_DELETE_ITEM = "delete_item";

    public record ItemReturnTarget(String packId, int returnPage) {
    }

    private static final List<String> RARITY_OPTIONS = List.of("common", "uncommon", "rare", "epic");
    private static final List<String> LIST_COMPONENTS = List.of(
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

    private static final String BUTTON_DELETE_CONFIRM = "confirm_delete";
    private static final String BUTTON_DELETE_CANCEL = "cancel_delete";
    private static final String CONTEXT_DELETE_STAMP = "delete_stamp";
    private static final long DELETE_CONFIRM_MILLIS = 5000L;
    private static final String SET_SCOPE = "set";

    private static final MinecraftItemComponentCatalog CATALOG = new MinecraftItemComponentCatalog();
    private static final ActionLineValidator ACTION_VALIDATOR = ActionLineValidator.coreLib();

    private final EmakiItemPlugin plugin;
    private EditorService service;

    public ItemEditorModule(EmakiItemPlugin plugin) {
        this.plugin = plugin;
    }

    public void bind(EditorService service) {
        this.service = service;
    }

    public EditorService service() {
        return service;
    }

    @Override
    public String rootCommand() {
        return "emakiitem";
    }

    @Override
    public List<EditorBrowserRoot> roots() {
        return List.of();
    }

    @Override
    public EditorMenuMeta meta(String menuId) {
        return ItemEditorMenus.meta(menuId);
    }

    @Override
    public List<EditorFieldSpec> fields(EditorSession session, String menuId) {
        return ItemEditorMenus.specs(menuId, session);
    }

    @Override
    public String homeMenuId() {
        return ItemEditorMenus.HOME;
    }

    @Override
    public String titleKey(String menuId) {
        return ItemEditorMenus.titleKey(menuId);
    }

    @Override
    public String templateId(String menuId) {
        return ItemEditorMenus.template(menuId);
    }

    @Override
    public EditorValidator validator(EditorBrowserRoot root, Path file) {
        return documentValidator();
    }

    @Override
    public void reload() {
        plugin.reloadPluginStateAsync();
    }

    @Override
    public String scopeForMenu(String menuId) {
        return ItemEditorMenus.setMenu(menuId) ? SET_SCOPE : EditorSession.SCOPE_DEFAULT;
    }

    public EditorValidator documentValidator() {
        return (candidate, docId, source) -> {
            try {
                YamlSection section = YamlFiles.load(candidate.text());
                if (section == null) {
                    return "unparseable_yaml";
                }
                EmakiItemDefinition definition = new EmakiItemDefinitionParser(plugin.getLogger())
                        .parse(section, source.toString());
                if (definition == null) {
                    return "definition_parse_failed";
                }
                if (!docId.equals(definition.id())) {
                    return "id_changed";
                }
            } catch (RuntimeException failure) {
                return "unparseable_yaml";
            }
            return null;
        };
    }

    private static final EditorValidator SET_VALIDATOR = (candidate, docId, source) -> {
        Object rawId = candidate.value("id");
        if (rawId == null || Texts.isBlank(Texts.toStringSafe(rawId))) {
            return "definition_parse_failed";
        }
        return docId.equals(Texts.normalizeId(Texts.toStringSafe(rawId))) ? null : "id_changed";
    };

    public EditorSession openItem(Player player, String itemId, String packId, int returnPage) {
        try {
            File file = plugin.itemLoader().fileOf(itemId);
            return service.open(player, file.toPath(), itemId, documentValidator(),
                    new ItemReturnTarget(packId, returnPage));
        } catch (RuntimeException failure) {
            plugin.messageService().warning("console.editor_open_failed",
                    Map.of("item", itemId, "error", String.valueOf(failure)));
            return null;
        }
    }

    public void createAndOpen(Player player, String packId, int returnPage, String rawId) {
        String itemId = Texts.normalizeId(rawId == null ? "" : rawId.trim());
        if (itemId.isEmpty() || !itemId.matches("[a-z0-9_]+")) {
            plugin.messageService().send(player, "editor.create.invalid_id");
            return;
        }
        if (plugin.itemLoader().get(itemId) != null) {
            plugin.messageService().send(player, "editor.create.duplicate_id", Map.of("id", itemId));
            return;
        }
        File directory = packId == null || packId.isBlank()
                ? plugin.itemLoader().rootDirectory()
                : new File(plugin.itemLoader().rootDirectory(), packId);
        File file = new File(directory, itemId + ".yml");
        if (file.exists()) {
            plugin.messageService().send(player, "editor.create.duplicate_id", Map.of("id", itemId));
            return;
        }
        try {
            Files.createDirectories(directory.toPath());
            Files.writeString(file.toPath(), skeleton(itemId), StandardCharsets.UTF_8);
            plugin.reloadPluginStateAsync();
            plugin.messageService().send(player, "editor.create.success", Map.of("id", itemId));
            openItem(player, itemId, packId == null ? "" : packId, returnPage);
        } catch (IOException failure) {
            plugin.messageService().warning("console.editor_create_failed",
                    Map.of("path", String.valueOf(file), "error", String.valueOf(failure)));
            plugin.messageService().send(player, "editor.create.failed");
        }
    }

    static String skeleton(String itemId) {
        return """
                id: "%s"

                item:
                  source: "minecraft-stone"
                  components:
                    custom_name: "<white>%s</white>"

                equip_slot: "all"

                update:
                  enabled: false
                  version: 1
                  triggers:
                    join: true
                    held_change: true
                    inventory_click: true
                    inventory_drag: true
                    pickup: true
                    interact: true
                    command: true

                effects: []

                set:
                  id: ""
                  piece: ""

                condition:
                  type: "all_of"
                  required_count: 0
                  invalid_as_failure: true
                  entries: []
                  on_pass:
                    actions: []
                  on_fail:
                    message: ""
                    block_output: false
                    actions: []

                repair:
                  enabled: false
                  materials: []
                  economy:
                    enabled: false
                    restore: "100%%"
                    currencies: []
                  disabled_display:
                    name_prefix: ""
                    lore_append: []
                  on_disabled: []
                  on_repaired: []

                actions: {}
                """.formatted(itemId, itemId);
    }

    @Override
    public List<EditorField> extraFields(EditorSession session, String menuId) {
        if (ItemEditorMenus.SET_LIST.equals(menuId)) {
            List<EditorField> fields = new ArrayList<>();
            for (String setId : setIds()) {
                fields.add(new EditorField("set_" + setId, EditorField.Kind.COMMAND,
                        setId, null, null, null, List.of(setId), List.of(), -1, true));
            }
            return fields;
        }
        if (ItemEditorMenus.COMPONENTS.equals(menuId)) {
            return componentFields(session);
        }
        return List.of();
    }

    @Override
    public Material fieldIcon(EditorField field) {
        String specific = specificIcon(field.id());
        if (specific == null) {
            return null;
        }
        String name = specific.startsWith("minecraft-")
                ? specific.substring("minecraft-".length())
                : specific;
        return Material.matchMaterial(name.toUpperCase(Locale.ROOT));
    }

    @Override
    public ItemStack renderCustomSlot(EditorSession session, String menuId, GuiTemplate.ResolvedSlot slot) {
        String type = Texts.lower(slot.definition().type());
        if (TYPE_PREVIEW.equals(type)) {
            return renderPreview(session, slot.definition());
        }
        if (TYPE_FILE_PATH.equals(type)) {
            return GuiItemBuilder.build(slot.definition().itemDefinition(),
                    Map.of("file_path", String.valueOf(session.document().path())),
                    plugin.coreLib().configuredItemService());
        }
        return null;
    }

    @Override
    public boolean handleCustomSlot(EditorSession session, String menuId, GuiTemplate.ResolvedSlot slot,
            GuiClickContext click) {
        String type = Texts.lower(slot.definition().type());
        if (EditorService.TYPE_BACK.equals(type)) {
            returnToBrowser(session);
            return true;
        }
        if (TYPE_GET_ITEM.equals(type)) {
            handleGetItem(session);
            return true;
        }
        if (TYPE_DELETE_ITEM.equals(type)) {
            handleDelete(session);
            return true;
        }
        if (EditorService.TYPE_CONFIRM.equals(type) && ItemEditorMenus.COMPONENTS.equals(menuId)) {
            promptComponentSearch(session);
            return true;
        }
        return false;
    }

    @Override
    public boolean handleFieldOverride(EditorSession session, EditorField field, GuiClickContext click) {
        if (ItemEditorMenus.SET_LIST.equals(session.currentMenuId()) && field.id().startsWith("set_")) {
            openSetEditor(session, field.id().substring("set_".length()));
            return true;
        }
        if (SKIN_FIELD_ID.equals(field.id())) {
            handleSkinEdit(session);
            return true;
        }
        if (field.id().startsWith(COMPONENT_FIELD_PREFIX)) {
            handleComponentField(session, field, click);
            return true;
        }
        return false;
    }

    @Override
    public String validateInput(EditorSession session, String listPath, String text) {
        return isActionList(listPath) ? ACTION_VALIDATOR.validate(text) : null;
    }

    @Override
    public String fieldHint(EditorSession session, EditorFieldSpec spec) {
        if (spec.path() == null || spec.path().length != 3
                || !"item".equals(spec.path()[0]) || !"components".equals(spec.path()[1])) {
            return null;
        }
        return componentHint(spec.path()[2]);
    }

    @Override
    public String listHint(EditorSession session, String listPath, String current) {
        return isActionList(listPath) ? actionHint(current) : null;
    }

    private void returnToBrowser(EditorSession session) {
        String packId = "";
        int page = 0;
        if (session.returnTarget() instanceof ItemReturnTarget target) {
            packId = target.packId();
            page = target.returnPage();
        }
        service.close(session.player());
        if (!plugin.browserGuiService().openItemBrowser(session.player(), packId, page)) {
            plugin.messageService().send(session.player(), "browser.gui_open_failed");
        }
    }

    private void handleGetItem(EditorSession session) {
        ItemStack created = plugin.itemFactory().create(session.document().docId(), 1);
        if (created == null) {
            EditorFeedback.play(session.player(), false);
            return;
        }
        session.player().getInventory().addItem(created);
        plugin.messageService().send(session.player(), "editor.get_item.success",
                Map.of("id", session.document().docId()));
        EditorFeedback.play(session.player(), true);
    }

    private void handleDelete(EditorSession session) {
        if (requestDeleteConfirmation(session)) {
            return;
        }
        performDelete(session);
    }

    private boolean requestDeleteConfirmation(EditorSession session) {
        Player player = session.player();
        String itemId = session.document().docId();
        boolean shown = service.input().confirm(
                player,
                plugin.messageService().message("editor.delete.title", Map.of("id", itemId)),
                List.of(
                        plugin.messageService().message("editor.delete.body", Map.of("id", itemId)),
                        plugin.messageService().message("editor.delete.body_irreversible")),
                plugin.messageService().message("editor.delete.confirm_button"),
                plugin.messageService().message("editor.delete.cancel_button"),
                BUTTON_DELETE_CONFIRM,
                BUTTON_DELETE_CANCEL,
                buttonId -> plugin.scheduling().runForEntity(plugin, player, () -> {
                    if (BUTTON_DELETE_CONFIRM.equals(buttonId)) {
                        performDelete(session);
                    } else {
                        plugin.messageService().send(player, "editor.delete.cancelled");
                        EditorFeedback.play(player, false);
                        service.refresh(session);
                    }
                }, () -> {
                }));
        if (shown) {
            return true;
        }
        long now = System.currentTimeMillis();
        String stamp = session.context(CONTEXT_DELETE_STAMP);
        if (stamp == null || now - Long.parseLong(stamp) > DELETE_CONFIRM_MILLIS) {
            session.putContext(CONTEXT_DELETE_STAMP, Long.toString(now));
            plugin.messageService().send(player, "editor.delete.confirm");
            service.refresh(session);
            return true;
        }
        session.putContext(CONTEXT_DELETE_STAMP, null);
        return false;
    }

    private void performDelete(EditorSession session) {
        String itemId = session.document().docId();
        service.editService().close(session.player());
        List<String> references = collectReferences(plugin.getDataFolder(), itemId);
        if (!references.isEmpty()) {
            plugin.messageService().send(session.player(), "editor.delete.references",
                    Map.of("refs", String.join(", ", references)));
        }
        if (plugin.itemLoader().fileOf(itemId) != null) {
            try {
                Path file = plugin.itemLoader().fileOf(itemId).toPath();
                Path backup = service.editService().backupRoot()
                        .resolve(Long.toString(System.currentTimeMillis()))
                        .resolve(itemId + ".yml");
                Files.createDirectories(backup.getParent());
                Files.copy(file, backup);
                Files.delete(file);
                plugin.messageService().send(session.player(), "editor.delete.success",
                        Map.of("id", itemId));
                EditorFeedback.play(session.player(), true);
            } catch (IOException failure) {
                plugin.messageService().warning("console.editor_delete_failed",
                        Map.of("error", String.valueOf(failure)));
                plugin.messageService().send(session.player(), "editor.delete.failed");
                EditorFeedback.play(session.player(), false);
            }
        }
        service.refresh(session);
    }

    private List<String> collectReferences(File dataFolder, String itemId) {
        List<String> found = new ArrayList<>();
        File setsDirectory = new File(dataFolder, "sets");
        File[] setFiles = setsDirectory.listFiles(
                (directory, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (setFiles != null) {
            for (File file : setFiles) {
                try {
                    Map<String, Object> root = YamlFiles.load(file).asMap();
                    if (root.get("pieces") instanceof Map<?, ?> pieces) {
                        for (Map.Entry<?, ?> entry : pieces.entrySet()) {
                            Object item = entry.getValue() instanceof Map<?, ?> piece
                                    ? piece.get("item")
                                    : entry.getValue();
                            if (itemId.equals(Texts.normalizeId(Texts.toStringSafe(item)))) {
                                found.add("sets/" + file.getName() + "#pieces." + entry.getKey());
                            }
                        }
                    }
                } catch (RuntimeException failure) {
                    plugin.getLogger().warning("[editor] 读取套装引用失败: " + file + ": " + failure);
                }
            }
        }
        File aliasFile = new File(dataFolder, "id_aliases.yml");
        if (aliasFile.isFile()) {
            try {
                Map<String, Object> root = YamlFiles.load(aliasFile).asMap();
                if (root.get("aliases") instanceof Map<?, ?> aliases) {
                    for (Map.Entry<?, ?> entry : aliases.entrySet()) {
                        Object target = entry.getValue() instanceof Map<?, ?> alias ? alias.get("target") : null;
                        if (itemId.equals(Texts.normalizeId(Texts.toStringSafe(target)))) {
                            found.add("id_aliases.yml#aliases." + entry.getKey());
                        }
                    }
                }
            } catch (RuntimeException failure) {
                plugin.getLogger().warning("[editor] 读取 alias 引用失败: " + aliasFile + ": " + failure);
            }
        }
        return found;
    }

    private void openSetEditor(EditorSession session, String setId) {
        File file = new File(new File(plugin.getDataFolder(), "sets"), setId + ".yml");
        if (!file.isFile()) {
            plugin.messageService().send(session.player(), "editor.set.missing", Map.of("id", setId));
            return;
        }
        try {
            session.attach(SET_SCOPE, service.editService().openDetached(file.toPath(), SET_VALIDATOR));
        } catch (IOException failure) {
            plugin.messageService().warning("console.editor_set_open_failed",
                    Map.of("file", String.valueOf(file), "error", String.valueOf(failure)));
            plugin.messageService().send(session.player(), "editor.set.missing", Map.of("id", setId));
            return;
        }
        service.openMenu(session, ItemEditorMenus.SET_EDITOR);
    }

    private List<EditorField> componentFields(EditorSession session) {
        List<MinecraftItemComponentCatalog.Entry> entries = new ArrayList<>();
        entries.addAll(CATALOG.universalEntries());
        entries.addAll(CATALOG.specializedFor(materialOf(session)));
        String filter = Texts.lower(Texts.toStringSafe(session.context(CONTEXT_COMPONENT_FILTER))).trim();
        String serverVersion = serverVersion();
        List<EditorField> fields = new ArrayList<>();
        for (MinecraftItemComponentCatalog.Entry entry : entries) {
            if (!entry.applicableTo(serverVersion)) {
                continue;
            }
            if (!filter.isEmpty() && !matchesFilter(entry, filter)) {
                continue;
            }
            String componentId = entry.componentId();
            if (ProfileComponentSupport.PROFILE_COMPONENT_ID.equals(componentId)) {
                fields.add(new EditorField(SKIN_FIELD_ID, EditorField.Kind.COMMAND, null,
                        plugin.messageService().message("editor.field.skin_edit"),
                        plugin.messageService().messageOrFallback("editor.field.skin_edit_desc", null),
                        entry.iconSource(), List.of(skinSummary(session)), List.of(), -1, true));
                continue;
            }
            EditorField.Kind kind = editorKind(entry);
            Object current = session.draft().valueLenient("item", "components", componentDraftKey(componentId));
            fields.add(new EditorField(
                    COMPONENT_FIELD_PREFIX + componentId,
                    kind,
                    null,
                    entry.displayNameOrId(),
                    componentDescription(entry),
                    entry.iconSource(),
                    service.renderer().describeValueLines(current),
                    kind == EditorField.Kind.CYCLE ? RARITY_OPTIONS : List.of(),
                    -1,
                    true));
        }
        return fields;
    }

    private String componentDescription(MinecraftItemComponentCatalog.Entry entry) {
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

    private static boolean matchesFilter(MinecraftItemComponentCatalog.Entry entry, String filter) {
        return Texts.lower(entry.componentId()).contains(filter)
                || Texts.lower(entry.displayNameOrId()).contains(filter);
    }

    public static String componentDraftKey(String componentId) {
        return componentId != null && componentId.startsWith("minecraft:")
                ? componentId.substring("minecraft:".length())
                : componentId;
    }

    private static EditorField.Kind editorKind(MinecraftItemComponentCatalog.Entry entry) {
        if (entry.nonValued()) {
            return EditorField.Kind.TOGGLE;
        }
        if ("minecraft:rarity".equals(entry.componentId())) {
            return EditorField.Kind.CYCLE;
        }
        String format = Texts.lower(entry.valueFormat());
        if (format.startsWith("boolean")) {
            return EditorField.Kind.TOGGLE;
        }
        if (format.contains("integer") || format.contains("number")) {
            return EditorField.Kind.NUMBER;
        }
        if (LIST_COMPONENTS.contains(entry.componentId())) {
            return EditorField.Kind.LIST;
        }
        return EditorField.Kind.TEXT;
    }

    static String materialOf(EditorSession session) {
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

    static String skinSummary(EditorSession session) {
        Object profile = session.draft().value("item", "components",
                ProfileComponentSupport.PROFILE_COMPONENT_ID);
        String url = ProfileComponentSupport.textureUrlOf(profile);
        if (url != null) {
            return url;
        }
        String value = ProfileComponentSupport.textureValueOf(profile);
        if (value != null) {
            return value.length() > 32 ? value.substring(0, 32) + "..." : value;
        }
        return profile instanceof Map<?, ?> map && map.get("name") != null
                ? Texts.toStringSafe(map.get("name"))
                : "-";
    }

    private ItemStack renderPreview(EditorSession session, GuiSlot slot) {
        try {
            return plugin.itemFactory().rebuildBase(
                    new EmakiItemDefinitionParser(plugin.getLogger())
                            .parse(YamlFiles.load(session.draft().text()),
                                    session.document().path().toString()),
                    1);
        } catch (RuntimeException failure) {
            return service.renderer().buildStatic(slot);
        }
    }

    private void handleSkinEdit(EditorSession session) {
        service.input().promptText(
                session.player(),
                plugin.messageService().message("editor.field.skin_edit"),
                plugin.messageService().message("editor.hint.skin"),
                plugin.messageService().message("editor.field.skin_edit"),
                skinSummary(session),
                plugin.messageService().message("editor.prompt.skin"),
                text -> {
                    Map<String, Object> profile = buildProfile(text);
                    if (profile == null) {
                        plugin.messageService().send(session.player(), "editor.skin.invalid");
                        EditorFeedback.play(session.player(), false);
                        return;
                    }
                    service.mutate(session, null, plugin.messageService().message("editor.field.skin_edit"), false,
                            candidate -> candidate.set(profile, "item", "components",
                                    ProfileComponentSupport.PROFILE_COMPONENT_ID));
                    service.refresh(session);
                });
    }

    private static Map<String, Object> buildProfile(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return ProfileComponentSupport.profileWithTextureUrl(trimmed);
        }
        if (trimmed.length() > 24 && trimmed.matches("[A-Za-z0-9+/=]+")) {
            return ProfileComponentSupport.profileWithTextureValue(trimmed);
        }
        return ProfileComponentSupport.profileWithPlayerName(trimmed);
    }

    private void handleComponentField(EditorSession session, EditorField field, GuiClickContext click) {
        String componentId = field.id().substring(COMPONENT_FIELD_PREFIX.length());
        String[] path = { "item", "components", componentDraftKey(componentId) };
        String label = componentLabel(field, componentId);
        boolean reset = click != null && click.isRightClick();
        switch (field.kind()) {
            case TOGGLE -> {
                if (reset) {
                    service.resetValue(session, field, label, path);
                    return;
                }
                YamlTextDocument draft = draft(session);
                Object current = draft.valueLenient(path);
                String[] resolved = draft.resolvePath(path);
                service.mutate(session, field, label, false, candidate -> {
                    if (isTruthy(current)) {
                        candidate.remove(resolved);
                    } else {
                        candidate.set(Boolean.TRUE, resolved);
                    }
                });
                service.refresh(session);
            }
            case CYCLE -> {
                if (reset) {
                    service.resetValue(session, field, label, path);
                    return;
                }
                if (field.options().isEmpty()) {
                    return;
                }
                YamlTextDocument draft = draft(session);
                String current = Texts.toStringSafe(draft.valueLenient(path));
                int position = field.options().indexOf(current);
                String next = field.options().get((position + 1) % field.options().size());
                String[] resolved = draft.resolvePath(path);
                service.mutate(session, field, label, false, candidate -> candidate.set(next, resolved));
                service.refresh(session);
            }
            case NUMBER, TEXT -> {
                if (reset) {
                    service.resetValue(session, field, label, path);
                    return;
                }
                promptComponentValue(session, field, componentId, path);
            }
            case LIST -> handleComponentList(session, field, path, click);
            default -> {
            }
        }
    }

    private String componentLabel(EditorField field, String componentId) {
        return field.displayName() != null ? field.displayName() : componentId;
    }

    private void promptComponentValue(EditorSession session, EditorField field, String componentId,
            String[] path) {
        YamlTextDocument draft = draft(session);
        Object current = draft.valueLenient(path);
        String label = componentLabel(field, componentId);
        String hint = composeHint(componentHint(componentId), EditorRenderer.describeValue(current));
        service.input().promptText(
                session.player(),
                label,
                hint,
                label,
                isEditableScalar(current) ? Texts.toStringSafe(current) : "",
                plugin.messageService().message("editor.prompt.chat", Map.of("label", label)),
                text -> {
                    if (text == null || text.isBlank()) {
                        return;
                    }
                    Object value = field.kind() == EditorField.Kind.NUMBER
                            ? parseNumber(text)
                            : parseYamlValue(text);
                    String[] resolved = draft.resolvePath(path);
                    service.mutate(session, field, label, false, candidate -> candidate.set(value, resolved));
                    service.refresh(session);
                });
    }

    private void handleComponentList(EditorSession session, EditorField field, String[] path,
            GuiClickContext click) {
        String listPath = String.join(".", path);
        String label = componentLabel(field, listPath);
        YamlTextDocument draft = draft(session);
        Object value = draft.valueLenient(path);
        if (click != null && click.isRightClick()) {
            if (value instanceof Map<?, ?> || click.isShiftClick()) {
                service.resetValue(session, field, label, path);
            } else {
                service.removeLastListLine(session, field, label, listPath);
            }
            return;
        }
        if (!(value instanceof Map<?, ?>)) {
            service.appendListLine(session, field, label, listPath);
            return;
        }
        session.putContext(EditorService.CONTEXT_LIST_PATH, listPath);
        session.putContext("list_label", field.displayName());
        session.putContext("entry_target", EditorService.MENU_LIST_ENTRIES);
        service.openMenu(session, EditorService.MENU_LIST_ENTRIES);
    }

    private YamlTextDocument draft(EditorSession session) {
        return session.draft(session.current().scope());
    }

    private static boolean isTruthy(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = Texts.toStringSafe(value).trim();
        return "true".equalsIgnoreCase(text) || "yes".equalsIgnoreCase(text);
    }

    private void promptComponentSearch(EditorSession session) {
        String current = Texts.toStringSafe(session.context(CONTEXT_COMPONENT_FILTER)).trim();
        String label = plugin.messageService().message("editor.field.component_search");
        service.input().promptText(
                session.player(),
                label,
                plugin.messageService().message("editor.hint.component_search"),
                label,
                current,
                plugin.messageService().message("editor.prompt.chat", Map.of("label", label)),
                text -> {
                    session.putContext(CONTEXT_COMPONENT_FILTER,
                            text == null || text.isBlank() ? null : text.trim());
                    session.setPage(0);
                    service.refresh(session);
                });
    }

    private static Object parseYamlValue(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        try {
            Object parsed = YamlFiles.load("value: " + trimmed).asMap().get("value");
            return parsed == null ? trimmed : parsed;
        } catch (RuntimeException failure) {
            return trimmed;
        }
    }

    private String componentHint(String componentId) {
        MinecraftItemComponentCatalog.Entry entry = CATALOG.entry(componentId);
        if (entry == null) {
            return null;
        }
        String description = entry.descriptionText();
        return description.isBlank()
                ? plugin.messageService().message("editor.hint.format", Map.of("format", entry.valueFormat()))
                : description;
    }

    private String actionHint(String current) {
        String[] tokens = Texts.toStringSafe(current).trim().split("\\s+");
        if (tokens.length == 0 || tokens[0].isBlank()) {
            return plugin.messageService().message("editor.hint.action_generic");
        }
        Optional<CoreActionStageDescriptor> descriptor =
                EmakiCoreLibApi.actionStage(tokens[0].toLowerCase(Locale.ROOT));
        if (descriptor.isEmpty()) {
            return plugin.messageService().message("editor.hint.action_generic");
        }
        StringBuilder builder = new StringBuilder();
        for (CoreStageParameter parameter : descriptor.get().parameters()) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(parameter.name());
            if (parameter.required() && !parameter.positional()) {
                builder.append("*");
            }
        }
        return plugin.messageService().message("editor.hint.action_parameters",
                Map.of("stage", descriptor.get().id(), "params", builder.toString()));
    }

    private static boolean isActionList(String listPath) {
        return listPath != null && listPath.contains("actions");
    }

    private static String composeHint(String formatHint, String currentValue) {
        if (formatHint == null || formatHint.isBlank()) {
            return currentValue;
        }
        return formatHint + "\n" + currentValue;
    }

    private static boolean isEditableScalar(Object value) {
        return !(value instanceof Map<?, ?>) && !(value instanceof List<?>);
    }

    private static Object parseNumber(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException ignored) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignoredAgain) {
                return text;
            }
        }
    }

    private List<String> setIds() {
        List<String> ids = new ArrayList<>();
        File directory = new File(plugin.getDataFolder(), "sets");
        File[] files = directory.listFiles(
                (dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (files == null) {
            return ids;
        }
        Arrays.sort(files);
        for (File file : files) {
            try {
                Object rawId = YamlFiles.load(file).asMap().get("id");
                String id = Texts.normalizeId(Texts.toStringSafe(rawId));
                if (!id.isEmpty()) {
                    ids.add(id);
                }
            } catch (RuntimeException failure) {
                plugin.getLogger().warning("[editor] 读取套装定义失败: " + file + ": " + failure);
            }
        }
        return ids;
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
            case SKIN_FIELD_ID -> "minecraft-player_head";
            default -> null;
        };
    }
}
