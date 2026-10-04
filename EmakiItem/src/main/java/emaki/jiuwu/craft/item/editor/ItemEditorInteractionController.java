package emaki.jiuwu.craft.item.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.gui.GuiClickContext;
import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.corelib.gui.GuiSessionHandler;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.item.EmakiItemPlugin;

public final class ItemEditorInteractionController implements GuiSessionHandler {

    private static final String CONTEXT_LIST_PATH = "list_path";
    private static final String CONTEXT_LIST_LABEL = "list_label";
    private static final String CONTEXT_ENTRY_TARGET = "entry_target";
    private static final String CONTEXT_ENTRY_INDEX = "entry_index";
    private static final String CONTEXT_DELETE_STAMP = "delete_stamp";
    private static final String BUTTON_DELETE_CONFIRM = "confirm_delete";
    private static final String BUTTON_DELETE_CANCEL = "cancel_delete";
    private static final long DELETE_CONFIRM_MILLIS = 5000L;

    private final EmakiItemPlugin plugin;
    private final ItemEditorGuiService guiService;
    private final ItemEditService editService;

    public ItemEditorInteractionController(EmakiItemPlugin plugin,
            ItemEditorGuiService guiService,
            ItemEditService editService) {
        this.plugin = plugin;
        this.guiService = guiService;
        this.editService = editService;
    }

    @Override
    public void onSlotClick(GuiSession guiSession, GuiClickContext context, GuiTemplate.ResolvedSlot slot) {
        if (slot == null || slot.definition() == null) {
            return;
        }
        ItemEditorSession session = guiService.session(context.viewer());
        if (session == null) {
            return;
        }
        String type = Texts.lower(slot.definition().type());
        switch (type) {
            case ItemEditorGuiService.TYPE_FIELD_ENTRY -> handleField(session, slot.slotIndex(), context);
            case "menu_basic" -> guiService.openMenu(session, ItemEditorMenus.BASIC);
            case "menu_components" -> guiService.openMenu(session, ItemEditorMenus.COMPONENTS);
            case "menu_effects" -> guiService.openMenu(session, ItemEditorMenus.EFFECTS);
            case "menu_set" -> guiService.openMenu(session, ItemEditorMenus.SET);
            case "menu_condition" -> guiService.openMenu(session, ItemEditorMenus.CONDITION);
            case "menu_repair" -> guiService.openMenu(session, ItemEditorMenus.REPAIR);
            case "menu_update" -> guiService.openMenu(session, ItemEditorMenus.UPDATE);
            case "menu_actions" -> guiService.openMenu(session, ItemEditorMenus.ACTIONS);
            case ItemEditorGuiService.TYPE_BACK_PARENT -> guiService.goBack(session);
            case ItemEditorGuiService.TYPE_BACK_HOME -> guiService.goHome(session);
            case ItemEditorGuiService.TYPE_BACK -> guiService.returnToBrowser(session);
            case ItemEditorGuiService.TYPE_CLOSE -> context.viewer().closeInventory();
            case ItemEditorGuiService.TYPE_CONFIRM -> handleConfirm(session);
            case ItemEditorGuiService.TYPE_PAGE_PREV -> turnPage(session, -1);
            case ItemEditorGuiService.TYPE_PAGE_NEXT -> turnPage(session, 1);
            case ItemEditorGuiService.TYPE_GET_ITEM -> handleGetItem(session);
            case ItemEditorGuiService.TYPE_DELETE_ITEM -> handleDelete(session);
            default -> {
            }
        }
    }

    @Override
    public void onPlayerInventoryClick(GuiSession guiSession, GuiClickContext context) {
        if (context.isBlockedTransfer()) {
            context.setCancelled(true);
        }
    }

    private void handleField(ItemEditorSession session, int slotIndex, GuiClickContext click) {
        List<ItemEditorField> fields = renderer().fields(session, session.currentMenu());
        int index = session.page() * 21 + slotIndex;
        if (index < 0 || index >= fields.size()) {
            return;
        }
        ItemEditorField field = fields.get(index);
        if (!field.enabled()) {
            return;
        }
        if (ItemEditorMenus.SET_LIST.equals(session.currentMenu()) && field.id().startsWith("set_")) {
            openSetEditor(session, field.id().substring("set_".length()));
            return;
        }
        if (ItemEditorRenderer.SKIN_FIELD_ID.equals(field.id())) {
            handleSkinEdit(session);
            return;
        }
        if (field.id().startsWith(ItemEditorRenderer.COMPONENT_FIELD_PREFIX)) {
            handleComponentField(session, field, click);
            return;
        }
        ItemEditorFieldSpec spec = spec(session, field.id());
        switch (field.kind()) {
            case TOGGLE -> {
                if (spec != null && click != null && click.isRightClick()) {
                    resetPath(session, specLabel(spec), spec.path());
                } else {
                    applyToggle(session, spec);
                }
            }
            case CYCLE -> {
                if (spec != null && click != null && click.isRightClick()) {
                    resetPath(session, specLabel(spec), spec.path());
                } else {
                    applyCycle(session, spec);
                }
            }
            case NUMBER, TEXT -> {
                if (spec != null && click != null && click.isRightClick()) {
                    resetPath(session, specLabel(spec), spec.path());
                } else {
                    applyPrompt(session, spec);
                }
            }
            case NAVIGATE -> {
                if (click == null || !click.isRightClick()) {
                    guiService.openMenu(session, spec.targetMenu());
                }
            }
            case LIST -> handleListField(session, spec, click);
            case COMMAND -> {
                if (click != null && click.isShiftClick()) {
                    deleteEntry(session, index, field.options());
                } else {
                    applyEntry(session, index, field.options());
                }
            }
        }
    }

    private void handleSkinEdit(ItemEditorSession session) {
        input().promptText(
                session.player(),
                plugin.messageService().message("editor.field.skin_edit"),
                plugin.messageService().message("editor.hint.skin"),
                plugin.messageService().message("editor.field.skin_edit"),
                ItemEditorRenderer.skinSummary(session),
                plugin.messageService().message("editor.prompt.skin"),
                text -> {
                    Map<String, Object> profile = buildProfile(text);
                    if (profile == null) {
                        plugin.messageService().send(session.player(), "editor.skin.invalid");
                        feedback(session.player(), false);
                        return;
                    }
                    mutate(session, plugin.messageService().message("editor.field.skin_edit"), false,
                            candidate -> candidate.set(profile, "item", "components",
                                    emaki.jiuwu.craft.corelib.item.ProfileComponentSupport.PROFILE_COMPONENT_ID));
                    guiService.refresh(session);
                });
    }

    private static Map<String, Object> buildProfile(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return emaki.jiuwu.craft.corelib.item.ProfileComponentSupport.profileWithTextureUrl(trimmed);
        }
        if (trimmed.length() > 24 && trimmed.matches("[A-Za-z0-9+/=]+")) {
            return emaki.jiuwu.craft.corelib.item.ProfileComponentSupport.profileWithTextureValue(trimmed);
        }
        return emaki.jiuwu.craft.corelib.item.ProfileComponentSupport.profileWithPlayerName(trimmed);
    }

    private void handleComponentField(ItemEditorSession session, ItemEditorField field, GuiClickContext click) {
        String componentId = field.id().substring(ItemEditorRenderer.COMPONENT_FIELD_PREFIX.length());
        String[] path = { "item", "components", ItemEditorRenderer.componentDraftKey(componentId) };
        String label = componentLabel(field, componentId);
        boolean reset = click != null && click.isRightClick();
        switch (field.kind()) {
            case TOGGLE -> {
                if (reset) {
                    resetPath(session, label, path);
                    return;
                }
                Object current = draft(session).valueLenient(path);
                String[] resolved = draft(session).resolvePath(path);
                mutate(session, label, false, candidate -> {
                    if (isTruthy(current)) {
                        candidate.remove(resolved);
                    } else {
                        candidate.set(Boolean.TRUE, resolved);
                    }
                });
                guiService.refresh(session);
            }
            case CYCLE -> {
                if (reset) {
                    resetPath(session, label, path);
                    return;
                }
                if (field.options().isEmpty()) {
                    return;
                }
                String current = Texts.toStringSafe(draft(session).valueLenient(path));
                int position = field.options().indexOf(current);
                String next = field.options().get((position + 1) % field.options().size());
                String[] resolved = draft(session).resolvePath(path);
                mutate(session, label, false, candidate -> candidate.set(next, resolved));
                guiService.refresh(session);
            }
            case NUMBER, TEXT -> {
                if (reset) {
                    resetPath(session, label, path);
                    return;
                }
                promptComponentValue(session, field, componentId, path);
            }
            case LIST -> handleComponentList(session, field, path, click);
            default -> {
            }
        }
    }

    private String componentLabel(ItemEditorField field, String componentId) {
        return field.displayName() != null ? field.displayName() : componentId;
    }

    private void promptComponentValue(ItemEditorSession session, ItemEditorField field, String componentId,
            String[] path) {
        Object current = draft(session).valueLenient(path);
        String label = componentLabel(field, componentId);
        String hint = composeHint(componentHint(componentId), ItemEditorRenderer.describeValue(current));
        input().promptText(
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
                    Object value = field.kind() == ItemEditorField.Kind.NUMBER
                            ? parseNumber(text)
                            : parseYamlValue(text);
                    String[] resolved = draft(session).resolvePath(path);
                    mutate(session, label, false, candidate -> candidate.set(value, resolved));
                    guiService.refresh(session);
                });
    }

    private void handleComponentList(ItemEditorSession session, ItemEditorField field, String[] path,
            GuiClickContext click) {
        String listPath = String.join(".", path);
        String label = componentLabel(field, listPath);
        Object value = draft(session).valueLenient(path);
        if (click != null && click.isRightClick()) {
            if (value instanceof Map<?, ?> || click.isShiftClick()) {
                resetPath(session, label, path);
            } else {
                removeLastListLine(session, label, listPath);
            }
            return;
        }
        if (!(value instanceof Map<?, ?>)) {
            promptAppendLine(session, label, listPath);
            return;
        }
        session.putContext(CONTEXT_LIST_PATH, listPath);
        session.putContext(CONTEXT_LIST_LABEL, field.displayName());
        session.putContext(CONTEXT_ENTRY_TARGET, ItemEditorMenus.LIST_ENTRIES);
        guiService.openMenu(session, ItemEditorMenus.LIST_ENTRIES);
    }

    private static boolean isTruthy(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = Texts.toStringSafe(value).trim();
        return "true".equalsIgnoreCase(text) || "yes".equalsIgnoreCase(text);
    }

    private void handleConfirm(ItemEditorSession session) {
        if (ItemEditorMenus.COMPONENTS.equals(session.currentMenu())) {
            promptComponentSearch(session);
        } else if (ItemEditorMenus.LIST_ENTRIES.equals(session.currentMenu())) {
            handleAdd(session);
        }
    }

    private void promptComponentSearch(ItemEditorSession session) {
        String current = Texts.toStringSafe(session.context(ItemEditorRenderer.CONTEXT_COMPONENT_FILTER)).trim();
        String label = plugin.messageService().message("editor.field.component_search");
        input().promptText(
                session.player(),
                label,
                plugin.messageService().message("editor.hint.component_search"),
                label,
                current,
                plugin.messageService().message("editor.prompt.chat", Map.of("label", label)),
                text -> {
                    session.putContext(ItemEditorRenderer.CONTEXT_COMPONENT_FILTER,
                            text == null || text.isBlank() ? null : text.trim());
                    session.setPage(0);
                    guiService.refresh(session);
                });
    }

    private static Object parseYamlValue(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        try {
            Object parsed = emaki.jiuwu.craft.corelib.api.yaml.YamlFiles.load("value: " + trimmed).asMap().get("value");
            return parsed == null ? trimmed : parsed;
        } catch (RuntimeException failure) {
            return trimmed;
        }
    }

    private static final emaki.jiuwu.craft.corelib.item.MinecraftItemComponentCatalog CATALOG =
            new emaki.jiuwu.craft.corelib.item.MinecraftItemComponentCatalog();

    private String fieldHint(ItemEditorFieldSpec spec) {
        if (spec == null || spec.path().length != 3
                || !"item".equals(spec.path()[0]) || !"components".equals(spec.path()[1])) {
            return null;
        }
        return componentHint(spec.path()[2]);
    }

    private String componentHint(String componentId) {
        emaki.jiuwu.craft.corelib.item.MinecraftItemComponentCatalog.Entry entry = CATALOG.entry(componentId);
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
        java.util.Optional<emaki.jiuwu.craft.corelib.api.action.descriptor.CoreActionStageDescriptor> descriptor =
                emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi.actionStage(tokens[0].toLowerCase(java.util.Locale.ROOT));
        if (descriptor.isEmpty()) {
            return plugin.messageService().message("editor.hint.action_generic");
        }
        StringBuilder builder = new StringBuilder();
        for (emaki.jiuwu.craft.corelib.api.action.CoreStageParameter parameter : descriptor.get().parameters()) {
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

    private YamlTextDocument draft(ItemEditorSession session) {
        return session.draftFor(session.currentMenu());
    }

    private void openSetEditor(ItemEditorSession session, String setId) {
        java.io.File file = new java.io.File(new java.io.File(plugin.getDataFolder(), "sets"), setId + ".yml");
        if (!file.isFile()) {
            plugin.messageService().send(session.player(), "editor.set.missing", Map.of("id", setId));
            return;
        }
        try {
            session.setSetDocument(editService.openSet(file.toPath()));
        } catch (java.io.IOException failure) {
            plugin.messageService().warning("console.editor_set_open_failed", Map.of("file", String.valueOf(file), "error", String.valueOf(failure)));
            plugin.messageService().send(session.player(), "editor.set.missing", Map.of("id", setId));
            return;
        }
        guiService.openMenu(session, ItemEditorMenus.SET_EDITOR);
    }

    private void applyToggle(ItemEditorSession session, ItemEditorFieldSpec spec) {
        if (spec == null) {
            return;
        }
        boolean current = Boolean.parseBoolean(Texts.toStringSafe(draft(session).valueLenient(spec.path())));
        String[] path = draft(session).resolvePath(spec.path());
        mutate(session, specLabel(spec), false, candidate -> candidate.set(!current, path));
        guiService.refresh(session);
    }

    private void applyCycle(ItemEditorSession session, ItemEditorFieldSpec spec) {
        if (spec == null || spec.options().isEmpty()) {
            return;
        }
        String current = Texts.toStringSafe(draft(session).valueLenient(spec.path()));
        int position = spec.options().indexOf(current);
        String next = spec.options().get((position + 1) % spec.options().size());
        String[] path = draft(session).resolvePath(spec.path());
        mutate(session, specLabel(spec), false, candidate -> candidate.set(next, path));
        guiService.refresh(session);
    }

    private void applyPrompt(ItemEditorSession session, ItemEditorFieldSpec spec) {
        if (spec == null) {
            return;
        }
        String label = plugin.messageService().message(spec.labelKey());
        String current = ItemEditorRenderer.describeValue(draft(session).valueLenient(spec.path()));
        String hint = composeHint(fieldHint(spec), current);
        input().promptText(
                session.player(),
                label,
                hint,
                label,
                isEditableScalar(draft(session).valueLenient(spec.path())) ? current : "",
                plugin.messageService().message("editor.prompt.chat", Map.of("label", label)),
                text -> {
                    if (text == null || text.isBlank()) {
                        return;
                    }
                    Object value = spec.kind() == ItemEditorField.Kind.NUMBER ? parseNumber(text) : text;
                    String[] path = draft(session).resolvePath(spec.path());
                    mutate(session, label, false, candidate -> candidate.set(value, path));
                    guiService.refresh(session);
                });
    }

    private String specLabel(ItemEditorFieldSpec spec) {
        return spec == null ? "?" : plugin.messageService().message(spec.labelKey());
    }

    private String composeHint(String formatHint, String currentValue) {
        if (formatHint == null || formatHint.isBlank()) {
            return currentValue;
        }
        return formatHint + "\n" + currentValue;
    }

    private boolean isEditableScalar(Object value) {
        return !(value instanceof Map<?, ?>) && !(value instanceof List<?>);
    }

    private void handleListField(ItemEditorSession session, ItemEditorFieldSpec spec, GuiClickContext click) {
        if (spec == null || spec.listPath() == null) {
            return;
        }
        String listPath = spec.listPath();
        String label = specLabel(spec);
        Object value = draft(session).valueLenient(split(listPath));
        if (click != null && click.isRightClick()) {
            if (value instanceof Map<?, ?> || click.isShiftClick()) {
                resetPath(session, label, split(listPath));
            } else {
                removeLastListLine(session, label, listPath);
            }
            return;
        }
        if (!(value instanceof Map<?, ?>) && !(click != null && click.isShiftClick())) {
            promptAppendLine(session, label, listPath);
            return;
        }
        session.putContext(CONTEXT_LIST_PATH, listPath);
        session.putContext(CONTEXT_ENTRY_TARGET, spec.targetMenu());
        guiService.openMenu(session, ItemEditorMenus.LIST_ENTRIES);
    }

    private void promptAppendLine(ItemEditorSession session, String label, String listPath) {
        String[] path = draft(session).resolvePath(split(listPath));
        input().promptText(
                session.player(),
                label,
                isActionList(listPath) ? actionHint("") : null,
                label,
                "",
                plugin.messageService().message("editor.prompt.chat", Map.of("label", label)),
                text -> {
                    String problem = precheckActionLine(listPath, text);
                    if (problem != null) {
                        plugin.messageService().send(session.player(), problem);
                        feedback(session.player(), false);
                        return;
                    }
                    if (Texts.isBlank(text)) {
                        return;
                    }
                    mutate(session, label, false, candidate -> candidate.appendListItem(text, path));
                    guiService.refresh(session);
                });
    }

    private void removeLastListLine(ItemEditorSession session, String label, String listPath) {
        String[] path = draft(session).resolvePath(split(listPath));
        List<Object> items = draft(session).sequence(path);
        if (items.isEmpty()) {
            plugin.messageService().send(session.player(), "editor.field.reset_empty");
            feedback(session.player(), false);
            return;
        }
        mutate(session, label, false, candidate -> candidate.removeListItem(items.size() - 1, path));
        guiService.refresh(session);
    }

    private void resetPath(ItemEditorSession session, String label, String... path) {
        String[] resolved = draft(session).resolvePath(path);
        if (draft(session).valueLenient(path) == null) {
            plugin.messageService().send(session.player(), "editor.field.reset_empty");
            feedback(session.player(), false);
            return;
        }
        mutate(session, label, true, candidate -> candidate.remove(resolved));
        guiService.refresh(session);
    }

    private static final ActionLineValidator ACTION_VALIDATOR = ActionLineValidator.coreLib();

    private static boolean isActionList(String listPath) {
        return listPath != null && listPath.contains("actions");
    }

    private String precheckActionLine(String listPath, String text) {
        return isActionList(listPath) ? ACTION_VALIDATOR.validate(text) : null;
    }

    private void applyEntry(ItemEditorSession session, int index, List<String> keyHint) {
        String listPath = session.context(CONTEXT_LIST_PATH);
        if (listPath == null) {
            return;
        }
        String target = session.context(CONTEXT_ENTRY_TARGET);
        if (target != null && !ItemEditorMenus.LIST_ENTRIES.equals(target) && keyHint.isEmpty()) {
            session.putContext(CONTEXT_ENTRY_INDEX, Integer.toString(index));
            guiService.openMenu(session, target);
            return;
        }
        String mapKey = keyHint.isEmpty() ? null : keyHint.get(0);
        String[] path = draft(session).resolvePath(split(listPath));
        Object current = mapKey == null
                ? draft(session).sequence(path).get(index)
                : mapValue(draft(session), listPath, mapKey);
        String label = listFieldLabel(session, listPath);
        input().promptText(
                session.player(),
                label,
                isActionList(listPath) ? actionHint(Texts.toStringSafe(current)) : null,
                label,
                Texts.toStringSafe(current),
                plugin.messageService().message("editor.prompt.chat", Map.of("label", label)),
                text -> {
                    String problem = precheckActionLine(listPath, text);
                    if (problem != null) {
                        plugin.messageService().send(session.player(), problem);
                        feedback(session.player(), false);
                        return;
                    }
                    Object value = mapKey == null ? text : parseYamlValue(text);
                    mutate(session, label, false, candidate -> {
                        if (mapKey == null) {
                            candidate.setListItem(value, index, path);
                        } else {
                            candidate.set(value, appendKey(path, mapKey));
                        }
                    });
                    guiService.refresh(session);
                });
    }

    private String listFieldLabel(ItemEditorSession session, String listPath) {
        String custom = session.context(CONTEXT_LIST_LABEL);
        if (custom != null && !custom.isBlank()) {
            return custom;
        }
        for (ItemEditorFieldSpec spec : ItemEditorMenus.specs(session.currentMenu(), session)) {
            if (listPath.equals(spec.listPath())) {
                return plugin.messageService().message(spec.labelKey());
            }
        }
        return plugin.messageService().message("editor.field.entry");
    }

    private void deleteEntry(ItemEditorSession session, int index, List<String> keyHint) {
        String listPath = session.context(CONTEXT_LIST_PATH);
        if (listPath == null) {
            return;
        }
        String mapKey = keyHint.isEmpty() ? null : keyHint.get(0);
        mutate(session, listFieldLabel(session, listPath), false, candidate -> {
            String[] resolved = candidate.resolvePath(split(listPath));
            if (mapKey == null) {
                candidate.removeListItem(index, resolved);
            } else {
                candidate.remove(appendKey(resolved, mapKey));
            }
        });
        guiService.refresh(session);
    }

    private static Object mapValue(YamlTextDocument draft, String listPath, String key) {
        Object target = draft.valueLenient(split(listPath));
        return target instanceof Map<?, ?> map ? map.get(key) : null;
    }

    private static String[] appendKey(String[] path, String key) {
        String[] result = java.util.Arrays.copyOf(path, path.length + 1);
        result[path.length] = key;
        return result;
    }

    private void handleAdd(ItemEditorSession session) {
        String listPath = session.context(CONTEXT_LIST_PATH);
        if (listPath == null) {
            return;
        }
        String label = listFieldLabel(session, listPath);
        boolean mapTarget = draft(session).valueLenient(split(listPath)) instanceof Map<?, ?>;
        String promptKey = mapTarget ? "editor.prompt.map_entry" : "editor.prompt.chat";
        input().promptText(
                session.player(),
                label,
                isActionList(listPath) ? actionHint("") : null,
                label,
                "",
                plugin.messageService().message(promptKey, Map.of("label", label)),
                text -> {
                    String problem = precheckActionLine(listPath, text);
                    if (problem != null) {
                        plugin.messageService().send(session.player(), problem);
                        feedback(session.player(), false);
                        return;
                    }
                    if (Texts.isBlank(text)) {
                        return;
                    }
                    if (mapTarget) {
                        String[] pair = splitKeyValue(text);
                        if (pair == null) {
                            plugin.messageService().send(session.player(), "editor.map.bad_entry");
                            feedback(session.player(), false);
                            return;
                        }
                        mutate(session, label, false, candidate -> candidate.set(parseYamlValue(pair[1]),
                                appendKey(candidate.resolvePath(split(listPath)), pair[0])));
                    } else {
                        mutate(session, label, false, candidate -> candidate.appendListItem(text,
                                candidate.resolvePath(split(listPath))));
                    }
                    guiService.refresh(session);
                });
    }

    private static String[] splitKeyValue(String text) {
        int separator = text.indexOf('=');
        if (separator <= 0 || separator >= text.length() - 1) {
            separator = text.indexOf(':');
        }
        if (separator <= 0 || separator >= text.length() - 1) {
            return null;
        }
        return new String[]{ text.substring(0, separator).trim(), text.substring(separator + 1).trim() };
    }

    private void handleGetItem(ItemEditorSession session) {
        ItemStack created = plugin.itemFactory().create(session.itemId(), 1);
        if (created == null) {
            feedback(session.player(), false);
            return;
        }
        session.player().getInventory().addItem(created);
        plugin.messageService().send(session.player(), "editor.get_item.success",
                Map.of("id", session.itemId()));
        feedback(session.player(), true);
    }

    private void handleDelete(ItemEditorSession session) {
        if (requestDeleteConfirmation(session)) {
            return;
        }
        performDelete(session);
    }

    private boolean requestDeleteConfirmation(ItemEditorSession session) {
        Player player = session.player();
        boolean shown = input().confirm(
                player,
                plugin.messageService().message("editor.delete.title", Map.of("id", session.itemId())),
                List.of(
                        plugin.messageService().message("editor.delete.body", Map.of("id", session.itemId())),
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
                        feedback(player, false);
                        guiService.refresh(session);
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
            guiService.refresh(session);
            return true;
        }
        session.putContext(CONTEXT_DELETE_STAMP, null);
        return false;
    }

    private void performDelete(ItemEditorSession session) {
        editService.close(session.player());
        List<String> references = collectReferences(session.itemId());
        if (!references.isEmpty()) {
            plugin.messageService().send(session.player(), "editor.delete.references",
                    Map.of("refs", String.join(", ", references)));
        }
        if (plugin.itemLoader().fileOf(session.itemId()) != null) {
            try {
                java.nio.file.Path file = plugin.itemLoader().fileOf(session.itemId()).toPath();
                java.nio.file.Path backup = editService.backupRoot()
                        .resolve(Long.toString(System.currentTimeMillis()))
                        .resolve(session.itemId() + ".yml");
                java.nio.file.Files.createDirectories(backup.getParent());
                java.nio.file.Files.copy(file, backup);
                java.nio.file.Files.delete(file);
                plugin.messageService().send(session.player(), "editor.delete.success",
                        Map.of("id", session.itemId()));
                feedback(session.player(), true);
            } catch (java.io.IOException failure) {
                plugin.messageService().warning("console.editor_delete_failed", Map.of("error", String.valueOf(failure)));
                plugin.messageService().send(session.player(), "editor.delete.failed");
                feedback(session.player(), false);
            }
        }
        guiService.refresh(session);
    }

    private List<String> collectReferences(String itemId) {
        return collectReferences(plugin.getDataFolder(), itemId);
    }

    static List<String> collectReferences(java.io.File dataFolder, String itemId) {
        List<String> found = new ArrayList<>();
        java.io.File setsDirectory = new java.io.File(dataFolder, "sets");
        java.io.File[] setFiles = setsDirectory.listFiles(
                (directory, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (setFiles != null) {
            for (java.io.File file : setFiles) {
                try {
                    Map<String, Object> root = emaki.jiuwu.craft.corelib.api.yaml.YamlFiles.load(file).asMap();
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
                } catch (RuntimeException ignored) {
                }
            }
        }
        java.io.File aliasFile = new java.io.File(dataFolder, "id_aliases.yml");
        if (aliasFile.isFile()) {
            try {
                Map<String, Object> root = emaki.jiuwu.craft.corelib.api.yaml.YamlFiles.load(aliasFile).asMap();
                if (root.get("aliases") instanceof Map<?, ?> aliases) {
                    for (Map.Entry<?, ?> entry : aliases.entrySet()) {
                        Object target = entry.getValue() instanceof Map<?, ?> alias ? alias.get("target") : null;
                        if (itemId.equals(Texts.normalizeId(Texts.toStringSafe(target)))) {
                            found.add("id_aliases.yml#aliases." + entry.getKey());
                        }
                    }
                }
            } catch (RuntimeException ignored) {
            }
        }
        return found;
    }

    private void turnPage(ItemEditorSession session, int delta) {
        int size = renderer().entryCount(session, session.currentMenu());
        int pages = Math.max(1, (size + 20) / 21);
        int next = Math.min(pages - 1, Math.max(0, session.page() + delta));
        session.setPage(next);
        guiService.refresh(session);
    }

    private void mutate(ItemEditorSession session, String label, boolean reset,
            java.util.function.Consumer<YamlTextDocument> mutation) {
        ItemDefinitionDocument.SaveResult result = editService.mutate(session.documentFor(session.currentMenu()), mutation);
        String item = feedbackLabel(label);
        if (result.saved()) {
            plugin.messageService().send(session.player(),
                    reset ? "editor.reset_ok" : "editor.modified", Map.of("item", item));
            feedback(session.player(), true);
        } else {
            String detail = Texts.toStringSafe(result.detail());
            plugin.messageService().send(session.player(),
                    "definition_parse_failed".equals(detail) ? "editor.modify_failed_parse" : "editor.modify_failed",
                    Map.of("item", item, "reason", detail));
            feedback(session.player(), false);
        }
    }

    private static String feedbackLabel(String label) {
        String plain = Texts.stripMiniTags(label);
        return plain.isBlank() ? "?" : plain;
    }

    private void feedback(Player player, boolean success) {
        player.playSound(player.getLocation(),
                success ? Sound.ENTITY_EXPERIENCE_ORB_PICKUP : Sound.ENTITY_VILLAGER_NO,
                1.0F, success ? 1.2F : 0.6F);
    }

    private ItemEditorRenderer renderer() {
        return guiService.renderer();
    }

    private ItemEditorInput input() {
        return guiService.input();
    }

    private ItemEditorFieldSpec spec(ItemEditorSession session, String fieldId) {
        for (ItemEditorFieldSpec candidate : ItemEditorMenus.specs(session.currentMenu(), session)) {
            if (candidate.id().equals(fieldId)) {
                return candidate;
            }
        }
        return null;
    }

    private static String[] split(String dotted) {
        return dotted.split("\\.");
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
}
