package emaki.jiuwu.craft.corelib.editor;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.editor.EditorSession.MenuRef;
import emaki.jiuwu.craft.corelib.gui.GuiClickContext;
import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.corelib.gui.GuiSessionHandler;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.service.AbstractMessageService;

public final class EditorInteractionController implements GuiSessionHandler {

    private static final String CONTEXT_LIST_PATH = EditorService.CONTEXT_LIST_PATH;
    private static final String CONTEXT_LIST_LABEL = "list_label";
    private static final String CONTEXT_ENTRY_TARGET = "entry_target";
    private static final String CONTEXT_ENTRY_INDEX = "entry_index";
    private static final String MENU_TYPE_PREFIX = "menu_";

    private final EditorService service;

    public EditorInteractionController(EditorService service) {
        this.service = service;
    }

    @Override
    public void onSlotClick(GuiSession guiSession, GuiClickContext context, GuiTemplate.ResolvedSlot slot) {
        if (slot == null || slot.definition() == null) {
            return;
        }
        EditorSession session = service.session(context.viewer());
        if (session == null) {
            return;
        }
        MenuRef ref = session.current();
        String type = Texts.lower(slot.definition().type());
        if (EditorService.TYPE_FIELD_ENTRY.equals(type)) {
            handleField(session, ref, slot.slotIndex(), context);
            return;
        }
        if (EditorService.TYPE_BACK_PARENT.equals(type)) {
            service.goBack(session);
            return;
        }
        if (EditorService.TYPE_BACK_HOME.equals(type)) {
            service.goHome(session);
            return;
        }
        if (EditorService.TYPE_CLOSE.equals(type)) {
            context.viewer().closeInventory();
            return;
        }
        if (EditorService.TYPE_CONFIRM.equals(type)) {
            handleConfirm(session, ref, slot, context);
            return;
        }
        if (EditorService.TYPE_PAGE_PREV.equals(type)) {
            turnPage(session, -1);
            return;
        }
        if (EditorService.TYPE_PAGE_NEXT.equals(type)) {
            turnPage(session, 1);
            return;
        }
        if (type != null && type.startsWith(MENU_TYPE_PREFIX)) {
            service.openMenu(session, type.substring(MENU_TYPE_PREFIX.length()));
            return;
        }
        if (module().handleCustomSlot(session, ref.menuId(), slot, context)) {
            return;
        }
        if (EditorService.TYPE_BACK.equals(type)) {
            service.openBrowser(context.viewer());
        }
    }

    @Override
    public void onPlayerInventoryClick(GuiSession guiSession, GuiClickContext context) {
        if (context.isBlockedTransfer()) {
            context.setCancelled(true);
        }
    }

    private void handleField(EditorSession session, MenuRef ref, int slotIndex, GuiClickContext click) {
        List<EditorField> fields = renderer().fields(session, ref);
        int index = session.page() * EditorRenderer.PAGE_SIZE + slotIndex;
        if (index < 0 || index >= fields.size()) {
            return;
        }
        EditorField field = fields.get(index);
        if (!field.enabled()) {
            return;
        }
        if (module().handleFieldOverride(session, field, click)) {
            return;
        }
        EditorFieldSpec spec = renderer().spec(session, ref, field.id());
        switch (field.kind()) {
            case TOGGLE -> {
                if (spec != null && click != null && click.isRightClick()) {
                    resetValue(session, field, specLabel(spec), spec.path());
                } else {
                    applyToggle(session, ref, field, spec);
                }
            }
            case CYCLE -> {
                if (spec != null && click != null && click.isRightClick()) {
                    resetValue(session, field, specLabel(spec), spec.path());
                } else {
                    applyCycle(session, ref, field, spec);
                }
            }
            case NUMBER, TEXT -> {
                if (spec != null && click != null && click.isRightClick()) {
                    resetValue(session, field, specLabel(spec), spec.path());
                } else {
                    applyPrompt(session, ref, field, spec);
                }
            }
            case NAVIGATE -> handleNavigate(session, ref, spec, click);
            case LIST -> handleListField(session, ref, field, spec, click);
            case COMMAND -> {
                if (click != null && click.isShiftClick()) {
                    deleteEntry(session, ref, index, field.options());
                } else {
                    applyEntry(session, ref, index, field.options());
                }
            }
        }
    }

    private void handleNavigate(EditorSession session, MenuRef ref, EditorFieldSpec spec, GuiClickContext click) {
        if (spec == null || spec.targetMenu() == null) {
            return;
        }
        if (click != null && click.isRightClick()) {
            return;
        }
        if (spec.targetMenu().startsWith("#")) {
            service.push(session, MenuRef.structural(
                    spec.targetMenu().substring(1).split("\\."), ref.scope()));
            return;
        }
        service.openMenu(session, spec.targetMenu());
    }

    private void handleConfirm(EditorSession session, MenuRef ref,
            GuiTemplate.ResolvedSlot slot, GuiClickContext context) {
        if (module().handleCustomSlot(session, ref.menuId(), slot, context)) {
            return;
        }
        if (EditorService.MENU_LIST_ENTRIES.equals(ref.menuId())) {
            handleAdd(session, ref);
        }
    }

    private void applyToggle(EditorSession session, MenuRef ref, EditorField field, EditorFieldSpec spec) {
        if (spec == null) {
            return;
        }
        YamlTextDocument draft = session.draft(ref.scope());
        boolean current = Boolean.parseBoolean(Texts.toStringSafe(draft.valueLenient(spec.path())));
        String[] path = draft.resolvePath(spec.path());
        mutate(session, field, specLabel(spec), false, candidate -> candidate.set(!current, path));
        service.refresh(session);
    }

    private void applyCycle(EditorSession session, MenuRef ref, EditorField field, EditorFieldSpec spec) {
        if (spec == null || spec.options().isEmpty()) {
            return;
        }
        YamlTextDocument draft = session.draft(ref.scope());
        String current = Texts.toStringSafe(draft.valueLenient(spec.path()));
        int position = spec.options().indexOf(current);
        String next = spec.options().get((position + 1) % spec.options().size());
        String[] path = draft.resolvePath(spec.path());
        mutate(session, field, specLabel(spec), false, candidate -> candidate.set(next, path));
        service.refresh(session);
    }

    private void applyPrompt(EditorSession session, MenuRef ref, EditorField field, EditorFieldSpec spec) {
        if (spec == null) {
            return;
        }
        YamlTextDocument draft = session.draft(ref.scope());
        String label = messages().message(spec.labelKey());
        String current = EditorRenderer.describeValue(draft.valueLenient(spec.path()));
        String hint = composeHint(module().fieldHint(session, spec), current);
        input().promptText(
                session.player(),
                label,
                hint,
                label,
                isEditableScalar(draft.valueLenient(spec.path())) ? current : "",
                messages().message("editor.prompt.chat", Map.of("label", label)),
                text -> {
                    if (text == null || text.isBlank()) {
                        return;
                    }
                    Object value = spec.kind() == EditorField.Kind.NUMBER ? parseNumber(text) : text;
                    String[] path = draft.resolvePath(spec.path());
                    mutate(session, field, label, false, candidate -> candidate.set(value, path));
                    service.refresh(session);
                });
    }

    private void handleListField(EditorSession session, MenuRef ref, EditorField field,
            EditorFieldSpec spec, GuiClickContext click) {
        if (spec == null || spec.listPath() == null) {
            return;
        }
        String listPath = spec.listPath();
        String label = specLabel(spec);
        YamlTextDocument draft = session.draft(ref.scope());
        Object value = draft.valueLenient(split(listPath));
        if (click != null && click.isRightClick()) {
            if (value instanceof Map<?, ?> || click.isShiftClick()) {
                resetValue(session, field, label, split(listPath));
            } else {
                removeLastListLine(session, field, label, listPath);
            }
            return;
        }
        if (!(value instanceof Map<?, ?>) && (click == null || !click.isShiftClick())) {
            appendListLine(session, field, label, listPath);
            return;
        }
        session.putContext(CONTEXT_LIST_PATH, listPath);
        session.putContext(CONTEXT_LIST_LABEL, field.displayName());
        session.putContext(CONTEXT_ENTRY_TARGET, spec.targetMenu());
        service.openMenu(session, EditorService.MENU_LIST_ENTRIES);
    }

    public void appendListLine(EditorSession session, EditorField field,
            String label, String listPath) {
        YamlTextDocument draft = session.draft(session.current().scope());
        String[] path = draft.resolvePath(split(listPath));
        input().promptText(
                session.player(),
                label,
                module().listHint(session, listPath, ""),
                label,
                "",
                messages().message("editor.prompt.chat", Map.of("label", label)),
                text -> {
                    String problem = module().validateInput(session, listPath, text);
                    if (problem != null) {
                        messages().send(session.player(), problem);
                        EditorFeedback.play(session.player(), false);
                        return;
                    }
                    if (Texts.isBlank(text)) {
                        return;
                    }
                    mutate(session, field, label, false,
                            candidate -> candidate.appendListItem(text, path));
                    service.refresh(session);
                });
    }

    public void removeLastListLine(EditorSession session, EditorField field,
            String label, String listPath) {
        YamlTextDocument draft = session.draft(session.current().scope());
        String[] path = draft.resolvePath(split(listPath));
        List<Object> items = draft.sequence(path);
        if (items.isEmpty()) {
            messages().send(session.player(), "editor.field.reset_empty");
            EditorFeedback.play(session.player(), false);
            return;
        }
        mutate(session, field, label, false,
                candidate -> candidate.removeListItem(items.size() - 1, path));
        service.refresh(session);
    }

    public void resetValue(EditorSession session, EditorField field, String label, String... path) {
        YamlTextDocument draft = session.draft(session.current().scope());
        String[] resolved = draft.resolvePath(path);
        if (draft.valueLenient(path) == null) {
            messages().send(session.player(), "editor.field.reset_empty");
            EditorFeedback.play(session.player(), false);
            return;
        }
        mutate(session, field, label, true, candidate -> candidate.remove(resolved));
        service.refresh(session);
    }

    private void applyEntry(EditorSession session, MenuRef ref, int index, List<String> keyHint) {
        String listPath = session.context(CONTEXT_LIST_PATH);
        if (listPath == null) {
            return;
        }
        String target = session.context(CONTEXT_ENTRY_TARGET);
        if (target != null && !EditorService.MENU_LIST_ENTRIES.equals(target) && keyHint.isEmpty()) {
            session.putContext(CONTEXT_ENTRY_INDEX, Integer.toString(index));
            service.openMenu(session, target);
            return;
        }
        String mapKey = keyHint.isEmpty() ? null : keyHint.get(0);
        YamlTextDocument draft = session.draft(ref.scope());
        String[] path = draft.resolvePath(split(listPath));
        Object current = mapKey == null
                ? draft.sequence(path).get(index)
                : mapValue(draft, listPath, mapKey);
        String label = listFieldLabel(session, ref, listPath);
        input().promptText(
                session.player(),
                label,
                module().listHint(session, listPath, Texts.toStringSafe(current)),
                label,
                Texts.toStringSafe(current),
                messages().message("editor.prompt.chat", Map.of("label", label)),
                text -> {
                    String problem = module().validateInput(session, listPath, text);
                    if (problem != null) {
                        messages().send(session.player(), problem);
                        EditorFeedback.play(session.player(), false);
                        return;
                    }
                    Object value = mapKey == null ? text : parseYamlValue(text);
                    mutate(session, null, label, false, candidate -> {
                        if (mapKey == null) {
                            candidate.setListItem(value, index, path);
                        } else {
                            candidate.set(value, appendKey(path, mapKey));
                        }
                    });
                    service.refresh(session);
                });
    }

    private void deleteEntry(EditorSession session, MenuRef ref, int index, List<String> keyHint) {
        String listPath = session.context(CONTEXT_LIST_PATH);
        if (listPath == null) {
            return;
        }
        String mapKey = keyHint.isEmpty() ? null : keyHint.get(0);
        mutate(session, null, listFieldLabel(session, ref, listPath), false, candidate -> {
            String[] resolved = candidate.resolvePath(split(listPath));
            if (mapKey == null) {
                candidate.removeListItem(index, resolved);
            } else {
                candidate.remove(appendKey(resolved, mapKey));
            }
        });
        service.refresh(session);
    }

    private void handleAdd(EditorSession session, MenuRef ref) {
        String listPath = session.context(CONTEXT_LIST_PATH);
        if (listPath == null) {
            return;
        }
        String label = listFieldLabel(session, ref, listPath);
        YamlTextDocument draft = session.draft(ref.scope());
        boolean mapTarget = draft.valueLenient(split(listPath)) instanceof Map<?, ?>;
        String promptKey = mapTarget ? "editor.prompt.map_entry" : "editor.prompt.chat";
        input().promptText(
                session.player(),
                label,
                module().listHint(session, listPath, ""),
                label,
                "",
                messages().message(promptKey, Map.of("label", label)),
                text -> {
                    String problem = module().validateInput(session, listPath, text);
                    if (problem != null) {
                        messages().send(session.player(), problem);
                        EditorFeedback.play(session.player(), false);
                        return;
                    }
                    if (Texts.isBlank(text)) {
                        return;
                    }
                    if (mapTarget) {
                        String[] pair = splitKeyValue(text);
                        if (pair == null) {
                            messages().send(session.player(), "editor.map.bad_entry");
                            EditorFeedback.play(session.player(), false);
                            return;
                        }
                        mutate(session, null, label, false, candidate -> candidate.set(parseYamlValue(pair[1]),
                                appendKey(candidate.resolvePath(split(listPath)), pair[0])));
                    } else {
                        mutate(session, null, label, false, candidate -> candidate.appendListItem(text,
                                candidate.resolvePath(split(listPath))));
                    }
                    service.refresh(session);
                });
    }

    private String listFieldLabel(EditorSession session, MenuRef ref, String listPath) {
        String custom = session.context(CONTEXT_LIST_LABEL);
        if (custom != null && !custom.isBlank()) {
            return custom;
        }
        for (EditorFieldSpec spec : renderer().specs(session, ref)) {
            if (listPath.equals(spec.listPath())) {
                return messages().message(spec.labelKey());
            }
        }
        return messages().message("editor.field.entry");
    }

    public EditorDocument.SaveResult mutate(EditorSession session, EditorField field, String label, boolean reset,
            Consumer<YamlTextDocument> mutation) {
        MenuRef ref = session.current();
        Function<YamlTextDocument, String> extraCheck =
                field == null ? null : candidate -> module().validateMutation(session, field, candidate);
        EditorDocument.SaveResult result =
                service.editService().mutate(session.document(ref.scope()), mutation, extraCheck);
        String item = feedbackLabel(label);
        if (result.saved()) {
            messages().send(session.player(),
                    reset ? "editor.reset_ok" : "editor.modified", Map.of("item", item));
            EditorFeedback.play(session.player(), true);
        } else {
            String detail = Texts.toStringSafe(result.detail());
            messages().send(session.player(),
                    "definition_parse_failed".equals(detail) ? "editor.modify_failed_parse" : "editor.modify_failed",
                    Map.of("item", item, "reason", detail));
            EditorFeedback.play(session.player(), false);
        }
        return result;
    }

    private static String feedbackLabel(String label) {
        String plain = Texts.stripMiniTags(label);
        return plain.isBlank() ? "?" : plain;
    }

    private void turnPage(EditorSession session, int delta) {
        int size = renderer().entryCount(session, session.current());
        int pages = Math.max(1, (size + EditorRenderer.PAGE_SIZE - 1) / EditorRenderer.PAGE_SIZE);
        int next = Math.min(pages - 1, Math.max(0, session.page() + delta));
        session.setPage(next);
        service.refresh(session);
    }

    private String specLabel(EditorFieldSpec spec) {
        return spec == null ? "?" : messages().message(spec.labelKey());
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

    private static Object mapValue(YamlTextDocument draft, String listPath, String key) {
        Object target = draft.valueLenient(split(listPath));
        return target instanceof Map<?, ?> map ? map.get(key) : null;
    }

    private static String[] appendKey(String[] path, String key) {
        String[] result = Arrays.copyOf(path, path.length + 1);
        result[path.length] = key;
        return result;
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

    private EditorModule module() {
        return service.module();
    }

    private EditorRenderer renderer() {
        return service.renderer();
    }

    private EditorInput input() {
        return service.input();
    }

    private AbstractMessageService messages() {
        return service.messages();
    }
}
