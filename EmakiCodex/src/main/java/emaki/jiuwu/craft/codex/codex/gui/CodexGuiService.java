package emaki.jiuwu.craft.codex.codex.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.codex.EmakiCodexPlugin;
import emaki.jiuwu.craft.codex.codex.loader.CodexCategoryLoader;
import emaki.jiuwu.craft.codex.codex.model.CodexCategory;
import emaki.jiuwu.craft.codex.codex.model.CodexEntry;
import emaki.jiuwu.craft.codex.codex.service.CodexEntryService;
import emaki.jiuwu.craft.codex.codex.service.CodexEntryService.EntryProgress;
import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.gui.GuiItemBuilder;
import emaki.jiuwu.craft.corelib.gui.GuiOpenRequest;
import emaki.jiuwu.craft.corelib.gui.GuiPagination;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.corelib.gui.GuiSlot;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.service.MessageService;

public final class CodexGuiService {

    private static final String TEMPLATE_CODEX_GUI = "codex_gui";
    private static final String SLOT_TYPE_ENTRY = "entry";
    private static final String FALLBACK_ICON = "book";
    private static final String LOCKED_ICON = "gray_stained_glass_pane";

    private final EmakiCodexPlugin plugin;
    private final GuiService guiService;
    private final GuiTemplateLoader guiTemplateLoader;
    private final CodexCategoryLoader categoryLoader;
    private final CodexEntryService entryService;
    private final MessageService messageService;

    public CodexGuiService(EmakiCodexPlugin plugin,
            GuiService guiService,
            GuiTemplateLoader guiTemplateLoader,
            CodexCategoryLoader categoryLoader,
            CodexEntryService entryService,
            MessageService messageService) {
        this.plugin = plugin;
        this.guiService = guiService;
        this.guiTemplateLoader = guiTemplateLoader;
        this.categoryLoader = categoryLoader;
        this.entryService = entryService;
        this.messageService = messageService;
    }

    public boolean open(Player player) {
        if (player == null) {
            return false;
        }
        GuiTemplate template = guiTemplateLoader.get(TEMPLATE_CODEX_GUI);
        if (template == null) {
            plugin.getLogger().warning("Codex GUI template '" + TEMPLATE_CODEX_GUI + "' is not loaded");
            return false;
        }
        List<CodexCategory> categories = categories();
        if (categories.isEmpty()) {
            messageService.send(player, "gui.no_categories");
            return false;
        }
        Map<String, Object> replacements = new LinkedHashMap<>();
        replacements.put(CodexGuiHandler.KEY_CURRENT_PAGE, 0);
        replacements.put(CodexGuiHandler.KEY_CURRENT_CATEGORY, categories.getFirst().categoryId());

        GuiOpenRequest request = new GuiOpenRequest(plugin, player, template, replacements,
                this::renderSlot, new CodexGuiHandler(this));
        return guiService.open(request) != null;
    }

    public List<CodexCategory> categories() {
        return categoryLoader.orderedCategories();
    }

    public CodexCategory currentCategory(GuiSession session) {
        String selected = CodexGuiHandler.categoryId(session);
        CodexCategory category = categoryLoader.get(Texts.normalizeId(selected));
        if (category != null) {
            return category;
        }
        List<CodexCategory> categories = categories();
        return categories.isEmpty() ? null : categories.getFirst();
    }

    public int entriesPerPage(GuiSession session) {
        return Math.max(1, GuiPagination.pageSize(session.template(), SLOT_TYPE_ENTRY));
    }

    public List<CodexEntry> visibleEntries(Player viewer, CodexCategory category) {
        List<CodexEntry> visible = new ArrayList<>();
        for (CodexEntry entry : category.orderedEntries()) {
            boolean unlocked = progress(viewer, category, entry) != EntryProgress.LOCKED;
            if (unlocked || !entry.hidden()) {
                visible.add(entry);
            }
        }
        return List.copyOf(visible);
    }

    public EmakiResult<?> activate(Player viewer, String categoryId, String entryId) {
        return entryService.activate(viewer, categoryId, entryId);
    }

    public EmakiResult<?> claim(Player viewer, String categoryId, String entryId) {
        return entryService.claim(viewer, categoryId, entryId);
    }

    public void reportOutcome(Player viewer, EmakiResult<?> outcome) {
        if (outcome.isSuccess()) {
            messageService.send(viewer, "gui.entry_action_done");
            return;
        }
        String reasonKey = outcome.reasonKey();
        messageService.sendRaw(viewer, messageService.messageOrFallback(reasonKey, reasonKey));
    }

    private EntryProgress progress(Player viewer, CodexCategory category, CodexEntry entry) {
        return entryService.progress(viewer.getUniqueId(), category.categoryId(), entry.entryId());
    }

    private ItemStack renderSlot(GuiSession session, GuiTemplate.ResolvedSlot resolved) {
        if (resolved == null || resolved.definition() == null || resolved.definition().type() == null) {
            return null;
        }
        return switch (resolved.definition().type()) {
            case SLOT_TYPE_ENTRY -> renderEntry(session, resolved);
            case "category_tab" -> renderCategoryTab(session, resolved);
            case "page_info" -> renderPageInfo(session, resolved.definition());
            default -> null;
        };
    }

    private ItemStack renderEntry(GuiSession session, GuiTemplate.ResolvedSlot resolved) {
        Player viewer = session.viewer();
        CodexCategory category = currentCategory(session);
        if (category == null) {
            return new ItemStack(Material.AIR);
        }
        List<CodexEntry> visible = visibleEntries(viewer, category);
        int index = CodexGuiHandler.page(session) * entriesPerPage(session) + resolved.slotIndex();
        if (index < 0 || index >= visible.size()) {
            return new ItemStack(Material.AIR);
        }
        CodexEntry entry = visible.get(index);
        EntryProgress progress = progress(viewer, category, entry);
        boolean locked = progress == EntryProgress.LOCKED;

        Map<String, Object> replacements = new LinkedHashMap<>();
        replacements.put("codex_category", category.categoryId());
        replacements.put("codex_entry", entry.entryId());
        replacements.put("entry_title", locked ? messageService.message("gui.entry_unknown") : entry.title());
        replacements.put("entry_description", locked ? "" : entry.description());
        replacements.put("entry_state", stateLabel(progress));
        replacements.put("entry_hint", actionHint(progress));
        replacements.put("entry_rewards", rewardLines(entry, locked));

        String icon = locked ? LOCKED_ICON : entry.icon();
        return buildItem(resolved.definition(), icon, entry.title(), replacements);
    }

    private ItemStack renderCategoryTab(GuiSession session, GuiTemplate.ResolvedSlot resolved) {
        List<CodexCategory> categories = categories();
        int index = resolved.slotIndex();
        if (index < 0 || index >= categories.size()) {
            return new ItemStack(Material.AIR);
        }
        CodexCategory category = categories.get(index);
        CodexCategory active = currentCategory(session);
        Map<String, Object> replacements = new LinkedHashMap<>();
        replacements.put("category_id", category.categoryId());
        replacements.put("category_title", category.title());
        replacements.put("category_state", messageService.message(
                active != null && active.categoryId().equals(category.categoryId())
                        ? "gui.category_active"
                        : "gui.category_inactive"));
        return buildItem(resolved.definition(), category.icon(), category.title(), replacements);
    }

    private ItemStack renderPageInfo(GuiSession session, GuiSlot slot) {
        Player viewer = session.viewer();
        CodexCategory category = currentCategory(session);
        int total = category == null ? 0 : visibleEntries(viewer, category).size();
        int pageSize = entriesPerPage(session);
        Map<String, Object> replacements = new LinkedHashMap<>();
        replacements.put("current_page", CodexGuiHandler.page(session) + 1);
        replacements.put("total_pages", GuiPagination.totalPages(total, pageSize));
        replacements.put("entry_count", total);
        replacements.put("category_title", category == null ? "" : category.title());
        return buildItem(slot, FALLBACK_ICON, "", replacements);
    }

    private List<String> rewardLines(CodexEntry entry, boolean locked) {
        if (locked) {
            return List.of(messageService.message("gui.reward_hidden"));
        }
        List<String> lines = new ArrayList<>();
        entry.attributeRewards().forEach((attributeId, value) -> lines.add(
                messageService.message("gui.reward_attribute", Map.of(
                        "attribute", attributeId,
                        "value", value))));
        if (entry.hasClaimActions()) {
            lines.add(messageService.message("gui.reward_claimable"));
        }
        if (lines.isEmpty()) {
            lines.add(messageService.message("gui.reward_none"));
        }
        return List.copyOf(lines);
    }

    private String stateLabel(EntryProgress progress) {
        return messageService.message(switch (progress) {
            case LOCKED -> "gui.state_locked";
            case UNLOCKED -> "gui.state_unlocked";
            case ACTIVATED -> "gui.state_activated";
            case CLAIMED -> "gui.state_claimed";
        });
    }

    private String actionHint(EntryProgress progress) {
        return messageService.message(switch (progress) {
            case LOCKED -> "gui.hint_locked";
            case UNLOCKED -> "gui.hint_activate";
            case ACTIVATED -> "gui.hint_claim";
            case CLAIMED -> "gui.hint_done";
        });
    }

    private ItemStack buildItem(GuiSlot slot, String fallbackIcon, String fallbackName,
            Map<String, Object> replacements) {
        String configured = slot == null ? null : slot.item();
        String icon = Texts.isBlank(configured) ? fallbackIcon : configured;
        return GuiItemBuilder.build(slot,
                Texts.isBlank(icon) ? FALLBACK_ICON : icon,
                fallbackName,
                List.of(),
                replacements,
                plugin.coreLib().configuredItemService());
    }
}
