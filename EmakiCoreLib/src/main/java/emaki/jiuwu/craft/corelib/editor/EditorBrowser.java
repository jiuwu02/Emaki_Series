package emaki.jiuwu.craft.corelib.editor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;
import emaki.jiuwu.craft.corelib.api.item.ConfiguredItemDefinition;
import emaki.jiuwu.craft.corelib.api.item.ItemComponentPatch;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.gui.GuiClickContext;
import emaki.jiuwu.craft.corelib.gui.GuiItemBuilder;
import emaki.jiuwu.craft.corelib.gui.GuiOpenRequest;
import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.corelib.gui.GuiSessionHandler;
import emaki.jiuwu.craft.corelib.gui.GuiSlot;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.item.ConfiguredItemService;

public final class EditorBrowser implements GuiSessionHandler {

    public static final String TYPE_DOC_ENTRY = "doc_entry";
    public static final String TYPE_NEW_DOC = "new_doc";
    public static final String MENU_ROOT_SELECT = "@browser_new";

    private static final String BUTTON_DELETE_CONFIRM = "confirm_delete";
    private static final String BUTTON_DELETE_CANCEL = "cancel_delete";
    private static final String DELETE_STAMP = "delete_stamp";
    private static final long DELETE_CONFIRM_MILLIS = 5000L;

    private record BrowserEntry(String id, Path file, EditorBrowserRoot root) {
    }

    private static final class BrowserState {

        private int page;
        private boolean rootSelect;
        private String deleteStamp;
    }

    private final EditorService service;
    private final Map<UUID, BrowserState> states = new ConcurrentHashMap<>();

    public EditorBrowser(EditorService service) {
        this.service = service;
    }

    public void open(Player player) {
        BrowserState state = states.computeIfAbsent(player.getUniqueId(), key -> new BrowserState());
        state.page = 0;
        state.rootSelect = false;
        state.deleteStamp = null;
        render(player, state);
    }

    public void close(Player player) {
        states.remove(player.getUniqueId());
    }

    @Override
    public void onSlotClick(GuiSession guiSession, GuiClickContext click, GuiTemplate.ResolvedSlot slot) {
        if (slot == null || slot.definition() == null) {
            return;
        }
        click.setCancelled(true);
        Player player = click.viewer();
        BrowserState state = states.get(player.getUniqueId());
        if (state == null) {
            return;
        }
        String type = Texts.lower(slot.definition().type());
        switch (type) {
            case EditorService.TYPE_FIELD_ENTRY -> {
                if (state.rootSelect) {
                    chooseRoot(player, state, slot.slotIndex());
                } else {
                    handleEntry(player, state, slot.slotIndex(), click);
                }
            }
            case TYPE_NEW_DOC -> startCreate(player, state);
            case EditorService.TYPE_PAGE_PREV -> {
                state.page = Math.max(0, state.page - 1);
                render(player, state);
            }
            case EditorService.TYPE_PAGE_NEXT -> {
                state.page = state.page + 1;
                render(player, state);
            }
            case EditorService.TYPE_BACK, EditorService.TYPE_BACK_PARENT, EditorService.TYPE_BACK_HOME ->
                back(player, state);
            case EditorService.TYPE_CLOSE -> {
                close(player);
                player.closeInventory();
            }
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

    private void back(Player player, BrowserState state) {
        if (state.rootSelect) {
            state.rootSelect = false;
            state.page = 0;
            render(player, state);
            return;
        }
        close(player);
        player.closeInventory();
    }

    private void handleEntry(Player player, BrowserState state, int slotIndex, GuiClickContext click) {
        BrowserEntry entry = entryAt(state, slotIndex);
        if (entry == null) {
            return;
        }
        if (click.isRightClick()) {
            deleteDoc(player, state, entry);
            return;
        }
        if (!click.isLeftClick()) {
            return;
        }
        openDoc(player, entry);
    }

    private BrowserEntry entryAt(BrowserState state, int slotIndex) {
        List<BrowserEntry> entries = entries();
        int index = state.page * EditorRenderer.PAGE_SIZE + slotIndex;
        if (index < 0 || index >= entries.size()) {
            return null;
        }
        return entries.get(index);
    }

    private void openDoc(Player player, BrowserEntry entry) {
        states.remove(player.getUniqueId());
        EditorSession session = service.open(player, entry.file(), entry.id(),
                service.module().validator(entry.root(), entry.file()), null);
        if (session == null) {
            service.messages().send(player, "editor.open_failed", Map.of("id", entry.id()));
        }
    }

    private void startCreate(Player player, BrowserState state) {
        if (createRoots().isEmpty()) {
            service.messages().send(player, "editor.browser.no_create_root");
            return;
        }
        state.rootSelect = true;
        state.page = 0;
        render(player, state);
    }

    private void chooseRoot(Player player, BrowserState state, int slotIndex) {
        List<EditorBrowserRoot> roots = createRoots();
        int index = state.page * EditorRenderer.PAGE_SIZE + slotIndex;
        if (index < 0 || index >= roots.size()) {
            return;
        }
        EditorBrowserRoot root = roots.get(index);
        service.input().promptChat(player,
                service.messages().message("editor.browser.new_hint"),
                service.messages().message("editor.browser.new_prompt", Map.of("root", root.id())),
                text -> createDoc(player, root, text));
    }

    private void createDoc(Player player, EditorBrowserRoot root, String rawId) {
        String id = Texts.normalizeId(rawId == null ? "" : rawId.trim());
        if (id.isEmpty() || !id.matches("[a-z0-9_]+")) {
            service.messages().send(player, "editor.browser.invalid_id");
            return;
        }
        Map<String, Object> skeleton = service.module().skeleton(root, id);
        if (skeleton == null) {
            service.messages().send(player, "editor.browser.no_skeleton");
            return;
        }
        Path directory = resolveDirectory(root);
        Path file = directory.resolve(id + ".yml");
        if (Files.exists(file)) {
            service.messages().send(player, "editor.browser.duplicate_id", Map.of("id", id));
            return;
        }
        try {
            Files.createDirectories(directory);
            Files.writeString(file, YamlFiles.dump(skeleton), StandardCharsets.UTF_8);
            service.module().reload();
            service.messages().send(player, "editor.browser.create_success", Map.of("id", id));
            EditorFeedback.play(player, true);
            BrowserState state = states.get(player.getUniqueId());
            if (state != null) {
                state.rootSelect = false;
                state.page = 0;
            }
            EditorSession session = service.open(player, file, id, service.module().validator(root, file), null);
            if (session == null) {
                service.messages().send(player, "editor.open_failed", Map.of("id", id));
            }
        } catch (IOException failure) {
            service.messages().warning("console.editor_browser_create_failed",
                    Map.of("path", String.valueOf(file), "error", String.valueOf(failure)));
            service.messages().send(player, "editor.browser.create_failed");
            EditorFeedback.play(player, false);
        }
    }

    private void deleteDoc(Player player, BrowserState state, BrowserEntry entry) {
        if (!entry.root().supportsDelete()) {
            return;
        }
        if (requestDeleteConfirmation(player, state, entry)) {
            return;
        }
        performDelete(player, state, entry);
    }

    private boolean requestDeleteConfirmation(Player player, BrowserState state, BrowserEntry entry) {
        boolean shown = service.input().confirm(
                player,
                service.messages().message("editor.browser.delete_title", Map.of("id", entry.id())),
                List.of(
                        service.messages().message("editor.browser.delete_body", Map.of("id", entry.id())),
                        service.messages().message("editor.browser.delete_body_irreversible")),
                service.messages().message("editor.browser.delete_confirm_button"),
                service.messages().message("editor.browser.delete_cancel_button"),
                BUTTON_DELETE_CONFIRM,
                BUTTON_DELETE_CANCEL,
                buttonId -> runForEntity(player, () -> {
                    if (BUTTON_DELETE_CONFIRM.equals(buttonId)) {
                        performDelete(player, state, entry);
                    } else {
                        service.messages().send(player, "editor.browser.delete_cancelled");
                        EditorFeedback.play(player, false);
                        render(player, state);
                    }
                }));
        if (shown) {
            return true;
        }
        long now = System.currentTimeMillis();
        String stamp = state.deleteStamp;
        if (stamp == null || now - Long.parseLong(stamp) > DELETE_CONFIRM_MILLIS) {
            state.deleteStamp = Long.toString(now);
            service.messages().send(player, "editor.browser.delete_confirm");
            render(player, state);
            return true;
        }
        state.deleteStamp = null;
        return false;
    }

    private void performDelete(Player player, BrowserState state, BrowserEntry entry) {
        try {
            Path backup = service.editService().backupRoot()
                    .resolve(Long.toString(System.currentTimeMillis()))
                    .resolve(entry.file().getFileName().toString());
            Files.createDirectories(backup.getParent());
            Files.copy(entry.file(), backup);
            Files.delete(entry.file());
            service.module().reload();
            service.messages().send(player, "editor.browser.delete_success", Map.of("id", entry.id()));
            EditorFeedback.play(player, true);
        } catch (IOException failure) {
            service.messages().warning("console.editor_browser_delete_failed",
                    Map.of("path", String.valueOf(entry.file()), "error", String.valueOf(failure)));
            service.messages().send(player, "editor.browser.delete_failed");
            EditorFeedback.play(player, false);
        }
        render(player, state);
    }

    private void runForEntity(Player player, Runnable task) {
        EmakiCoreLibApi.scheduling().runForEntity(service.plugin(), player, task, () -> {
        });
    }

    private void render(Player player, BrowserState state) {
        String templateId = state.rootSelect
                ? service.module().templateId(MENU_ROOT_SELECT)
                : service.module().browserTemplateId();
        GuiTemplate template = service.templates().get(templateId);
        if (template == null) {
            service.messages().warning("console.editor_template_missing", Map.of("template", templateId));
            return;
        }
        List<BrowserEntry> entries = state.rootSelect ? List.of() : entries();
        int total = state.rootSelect ? createRoots().size() : entries.size();
        int pages = Math.max(1, (total + EditorRenderer.PAGE_SIZE - 1) / EditorRenderer.PAGE_SIZE);
        state.page = Math.min(state.page, pages - 1);
        String menuTitle = service.messages().message(
                state.rootSelect ? "editor.browser.roots" : "editor.browser.title");
        Map<String, Object> replacements = new LinkedHashMap<>();
        replacements.put(EditorService.KEY_CURRENT_PAGE, state.page + 1);
        replacements.put(EditorService.KEY_TOTAL_PAGES, pages);
        replacements.put(EditorService.KEY_MENU_TITLE, menuTitle);
        replacements.put(EditorService.KEY_ENTRY_COUNT, total);
        GuiSession guiSession = service.gui().open(new GuiOpenRequest(
                service.plugin(),
                player,
                template,
                replacements,
                (target, slot) -> renderSlot(state, entries, slot),
                this));
        if (guiSession != null) {
            guiSession.putReplacement(EditorService.KEY_MENU_TITLE, menuTitle);
            guiSession.putReplacement(EditorService.KEY_TOTAL_PAGES, pages);
        }
    }

    private ItemStack renderSlot(BrowserState state, List<BrowserEntry> entries,
            GuiTemplate.ResolvedSlot resolved) {
        if (resolved == null || resolved.definition() == null) {
            return null;
        }
        GuiSlot slot = resolved.definition();
        String type = Texts.lower(slot.type());
        if (EditorService.TYPE_FIELD_ENTRY.equals(type)) {
            return state.rootSelect
                    ? renderRootChoice(state, resolved.slotIndex())
                    : renderDocEntry(slot, entries, state, resolved.slotIndex());
        }
        if (EditorService.TYPE_PAGE_INFO.equals(type)) {
            int total = state.rootSelect ? createRoots().size() : entries.size();
            int pages = Math.max(1, (total + EditorRenderer.PAGE_SIZE - 1) / EditorRenderer.PAGE_SIZE);
            Map<String, Object> replacements = new LinkedHashMap<>();
            replacements.put(EditorService.KEY_CURRENT_PAGE, state.page + 1);
            replacements.put(EditorService.KEY_TOTAL_PAGES, pages);
            replacements.put(EditorService.KEY_MENU_TITLE, service.messages().message(
                    state.rootSelect ? "editor.browser.roots" : "editor.browser.title"));
            replacements.put(EditorService.KEY_ENTRY_COUNT, total);
            return GuiItemBuilder.build(slot.itemDefinition(), replacements, configuredItems());
        }
        if (TYPE_NEW_DOC.equals(type)) {
            return createRoots().isEmpty() ? service.renderer().filler() : service.renderer().buildStatic(slot);
        }
        if (EditorService.TYPE_PAGE_PREV.equals(type)) {
            return state.page > 0 ? service.renderer().buildStatic(slot) : service.renderer().filler();
        }
        if (EditorService.TYPE_PAGE_NEXT.equals(type)) {
            int total = state.rootSelect ? createRoots().size() : entries.size();
            int pages = Math.max(1, (total + EditorRenderer.PAGE_SIZE - 1) / EditorRenderer.PAGE_SIZE);
            return state.page < pages - 1 ? service.renderer().buildStatic(slot) : service.renderer().filler();
        }
        return service.renderer().buildStatic(slot);
    }

    private ItemStack renderRootChoice(BrowserState state, int slotIndex) {
        List<EditorBrowserRoot> roots = createRoots();
        int index = state.page * EditorRenderer.PAGE_SIZE + slotIndex;
        if (index < 0 || index >= roots.size()) {
            return service.renderer().filler();
        }
        EditorBrowserRoot root = roots.get(index);
        String source = root.icon() == null ? "minecraft-chest"
                : EditorRenderer.materialSource(root.icon());
        ConfiguredItemDefinition definition = new ConfiguredItemDefinition(source, 1, Map.of(
                "minecraft:custom_name",
                ItemComponentPatch.set(service.messages().message(root.labelKey())),
                "minecraft:lore",
                ItemComponentPatch.set(List.of(root.id()))));
        return GuiItemBuilder.build(definition, Map.of(), configuredItems());
    }

    private ItemStack renderDocEntry(GuiSlot slot, List<BrowserEntry> entries, BrowserState state,
            int slotIndex) {
        int index = state.page * EditorRenderer.PAGE_SIZE + slotIndex;
        if (index < 0 || index >= entries.size()) {
            return service.renderer().filler();
        }
        BrowserEntry entry = entries.get(index);
        return GuiItemBuilder.build(slot,
                "minecraft-paper",
                entry.id(),
                List.of(String.valueOf(entry.file())),
                Map.of(),
                configuredItems());
    }

    private List<BrowserEntry> entries() {
        List<BrowserEntry> collected = new ArrayList<>();
        for (EditorBrowserRoot root : service.module().roots()) {
            if (root.fileName() != null) {
                Path file = resolveDirectory(root).resolve(root.fileName());
                if (Files.isRegularFile(file)) {
                    collected.add(new BrowserEntry(
                            root.fileId().apply(file.toFile(), rootLabel(root)), file, root));
                }
                continue;
            }
            Path directory = resolveDirectory(root);
            if (!Files.isDirectory(directory)) {
                continue;
            }
            try (Stream<Path> children = Files.list(directory)) {
                List<Path> files = children
                        .filter(Files::isRegularFile)
                        .filter(EditorBrowser::isYaml)
                        .sorted()
                        .toList();
                for (Path file : files) {
                    String id = root.fileId().apply(file.toFile(), rootLabel(root));
                    if (id != null && !id.isEmpty()) {
                        collected.add(new BrowserEntry(id, file, root));
                    }
                }
            } catch (IOException failure) {
                service.messages().warning("console.editor_browser_list_failed",
                        Map.of("path", String.valueOf(directory), "error", String.valueOf(failure)));
            }
        }
        collected.sort(Comparator.comparing(BrowserEntry::id));
        return collected;
    }

    private List<EditorBrowserRoot> createRoots() {
        List<EditorBrowserRoot> roots = new ArrayList<>();
        for (EditorBrowserRoot root : service.module().roots()) {
            if (root.supportsCreate()) {
                roots.add(root);
            }
        }
        return roots;
    }

    private static boolean isYaml(Path file) {
        String name = file.getFileName() == null ? "" : file.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".yml") || name.endsWith(".yaml");
    }

    private static String rootLabel(EditorBrowserRoot root) {
        return root.directory() == null ? "" : root.directory();
    }

    private Path resolveDirectory(EditorBrowserRoot root) {
        Path base = service.plugin().getDataFolder().toPath();
        String directory = root.directory();
        return directory == null || directory.isBlank() ? base : base.resolve(directory);
    }

    private static ConfiguredItemService configuredItems() {
        return EmakiCoreLibPlugin.lookup().configuredItemService();
    }
}
