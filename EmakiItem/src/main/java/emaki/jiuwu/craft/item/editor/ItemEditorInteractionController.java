package emaki.jiuwu.craft.item.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
            case ItemEditorGuiService.TYPE_FIELD_ENTRY -> handleField(session, slot.slotIndex());
            case ItemEditorGuiService.TYPE_BACK_PARENT -> guiService.goBack(session);
            case ItemEditorGuiService.TYPE_BACK_HOME -> guiService.goHome(session);
            case ItemEditorGuiService.TYPE_BACK -> guiService.returnToBrowser(session);
            case ItemEditorGuiService.TYPE_CLOSE -> context.viewer().closeInventory();
            case ItemEditorGuiService.TYPE_CONFIRM -> handleAdd(session);
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

    private void handleField(ItemEditorSession session, int slotIndex) {
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
        if (field.id().startsWith("component_")) {
            promptComponent(session, field.id().substring("component_".length()));
            return;
        }
        ItemEditorFieldSpec spec = spec(field.id());
        switch (field.kind()) {
            case TOGGLE -> applyToggle(session, spec);
            case CYCLE -> applyCycle(session, spec);
            case NUMBER, TEXT -> applyPrompt(session, spec);
            case NAVIGATE -> guiService.openMenu(session, spec.targetMenu());
            case LIST -> {
                session.putContext(CONTEXT_LIST_PATH, spec.listPath());
                guiService.openMenu(session, spec.targetMenu());
            }
            case COMMAND -> applyEntry(session, index);
        }
    }

    private void handleSkinEdit(ItemEditorSession session) {
        input().promptText(
                session.player(),
                plugin.messageService().message("editor.field.skin_edit"),
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
                    mutate(session, candidate -> candidate.set(profile, "item", "components",
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

    private void promptComponent(ItemEditorSession session, String componentId) {
        Object current = session.draft().value("item", "components", componentId);
        input().promptText(
                session.player(),
                componentId,
                componentId,
                Texts.toStringSafe(current),
                plugin.messageService().message("editor.prompt.chat", Map.of("label", componentId)),
                text -> {
                    mutate(session, candidate -> candidate.set(parseYamlValue(text), "item", "components", componentId));
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
            plugin.getLogger().warning("Could not open EmakiItem set definition " + file + ": " + failure);
            plugin.messageService().send(session.player(), "editor.set.missing", Map.of("id", setId));
            return;
        }
        guiService.openMenu(session, ItemEditorMenus.SET_EDITOR);
    }

    private void applyToggle(ItemEditorSession session, ItemEditorFieldSpec spec) {
        if (spec == null) {
            return;
        }
        boolean current = Boolean.parseBoolean(Texts.toStringSafe(draft(session).value(spec.path())));
        mutate(session, candidate -> candidate.set(!current, spec.path()));
    }

    private void applyCycle(ItemEditorSession session, ItemEditorFieldSpec spec) {
        if (spec == null || spec.options().isEmpty()) {
            return;
        }
        String current = Texts.toStringSafe(draft(session).value(spec.path()));
        int position = spec.options().indexOf(current);
        String next = spec.options().get((position + 1) % spec.options().size());
        mutate(session, candidate -> candidate.set(next, spec.path()));
    }

    private void applyPrompt(ItemEditorSession session, ItemEditorFieldSpec spec) {
        if (spec == null) {
            return;
        }
        String current = Texts.toStringSafe(draft(session).value(spec.path()));
        input().promptText(
                session.player(),
                plugin.messageService().message(spec.labelKey()),
                plugin.messageService().message(spec.labelKey()),
                current,
                plugin.messageService().message("editor.prompt.chat", Map.of("label", spec.labelKey())),
                text -> {
                    Object value = spec.kind() == ItemEditorField.Kind.NUMBER ? parseNumber(text) : text;
                    mutate(session, candidate -> candidate.set(value, spec.path()));
                    guiService.refresh(session);
                });
    }

    private static final Set<String> ACTION_CONTROL_KEYWORDS = Set.of("if", "else", "run", "weight", "every", "after", "limit", "keep", "sort_by", "stop", "chance", "set", "create_item", "filter", "where", "select");

    private static boolean isActionList(String listPath) {
        return listPath != null && listPath.contains("actions");
    }

    private String precheckActionLine(String listPath, String text) {
        if (!isActionList(listPath)) {
            return null;
        }
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            return "editor.action.empty";
        }
        for (String segment : trimmed.split("\\|")) {
            String token = segment.trim().split("\\s+")[0].toLowerCase(java.util.Locale.ROOT);
            if (token.isEmpty()) {
                return "editor.action.empty_segment";
            }
            if (ACTION_CONTROL_KEYWORDS.contains(token)) {
                continue;
            }
            if (emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi.actionStage(token).isEmpty()) {
                return "editor.action.unknown_stage";
            }
        }
        return null;
    }

    private void applyEntry(ItemEditorSession session, int index) {
        String listPath = session.context(CONTEXT_LIST_PATH);
        if (listPath == null) {
            return;
        }
        input().promptText(
                session.player(),
                plugin.messageService().message("editor.field.entry"),
                plugin.messageService().message("editor.field.entry"),
                Texts.toStringSafe(draft(session).sequence(split(listPath)).get(index)),
                plugin.messageService().message("editor.prompt.chat", Map.of("label", "editor.field.entry")),
                text -> {
                    String problem = precheckActionLine(listPath, text);
                    if (problem != null) {
                        plugin.messageService().send(session.player(), problem);
                        feedback(session.player(), false);
                        return;
                    }
                    mutate(session, candidate -> candidate.setListItem(text, index, split(listPath)));
                    guiService.refresh(session);
                });
    }

    private void handleAdd(ItemEditorSession session) {
        String listPath = session.context(CONTEXT_LIST_PATH);
        if (listPath == null) {
            return;
        }
        input().promptText(
                session.player(),
                plugin.messageService().message("editor.field.entry"),
                plugin.messageService().message("editor.field.entry"),
                "",
                plugin.messageService().message("editor.prompt.chat", Map.of("label", "editor.field.entry")),
                text -> {
                    String problem = precheckActionLine(listPath, text);
                    if (problem != null) {
                        plugin.messageService().send(session.player(), problem);
                        feedback(session.player(), false);
                        return;
                    }
                    if (!Texts.isBlank(text)) {
                        mutate(session, candidate -> candidate.appendListItem(text, split(listPath)));
                    }
                    guiService.refresh(session);
                });
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
                plugin.getLogger().warning("Could not delete EmakiItem definition: " + failure);
                plugin.messageService().send(session.player(), "editor.delete.failed");
                feedback(session.player(), false);
            }
        }
        guiService.refresh(session);
    }

    private List<String> collectReferences(String itemId) {
        List<String> found = new ArrayList<>();
        java.io.File setsDirectory = new java.io.File(plugin.getDataFolder(), "sets");
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
        java.io.File aliasFile = new java.io.File(plugin.getDataFolder(), "id_aliases.yml");
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

    private void mutate(ItemEditorSession session, java.util.function.Consumer<YamlTextDocument> mutation) {
        ItemDefinitionDocument.SaveResult result = editService.mutate(session.documentFor(session.currentMenu()), mutation);
        if (result.saved()) {
            plugin.messageService().send(session.player(), "editor.saved");
            feedback(session.player(), true);
        } else {
            plugin.messageService().send(session.player(), "editor.save_failed",
                    Map.of("reason", Texts.toStringSafe(result.detail())));
            feedback(session.player(), false);
        }
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

    private static ItemEditorFieldSpec spec(String fieldId) {
        for (String menu : List.of(ItemEditorMenus.BASIC, ItemEditorMenus.UPDATE, ItemEditorMenus.SET,
                ItemEditorMenus.CONDITION, ItemEditorMenus.REPAIR, ItemEditorMenus.REPAIR_ECONOMY,
                ItemEditorMenus.COMPONENTS, ItemEditorMenus.EFFECTS, ItemEditorMenus.ACTIONS)) {
            for (ItemEditorFieldSpec candidate : ItemEditorMenus.specs(menu)) {
                if (candidate.id().equals(fieldId)) {
                    return candidate;
                }
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
