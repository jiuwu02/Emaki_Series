package emaki.jiuwu.craft.skills.gui;

import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.corelib.gui.GuiSessionHandler;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.skills.model.PlayerSkillProfile;
import emaki.jiuwu.craft.skills.model.SkillDefinition;
import emaki.jiuwu.craft.skills.model.SkillSlotBinding;
import emaki.jiuwu.craft.skills.model.UnlockedSkillEntry;
import emaki.jiuwu.craft.skills.service.CastModeService;
import emaki.jiuwu.craft.skills.service.PlayerSkillDataStore;
import emaki.jiuwu.craft.skills.service.PlayerSkillStateService;
import emaki.jiuwu.craft.skills.service.SkillRegistryService;

public final class SkillsGuiHandler implements GuiSessionHandler {

    static final String KEY_CURRENT_PAGE = "current_page";
    static final String KEY_SELECTED_SLOT = "selected_slot";

    private final JavaPlugin plugin;
    private final PlayerSkillStateService stateService;
    private final PlayerSkillDataStore dataStore;
    private final SkillRegistryService registryService;
    private final CastModeService castModeService;
    private final MessageService messageService;
    private final SkillsGuiService skillsGuiService;

    public SkillsGuiHandler(JavaPlugin plugin,
            PlayerSkillStateService stateService,
            PlayerSkillDataStore dataStore,
            SkillRegistryService registryService,
            CastModeService castModeService,
            MessageService messageService,
            SkillsGuiService skillsGuiService) {
        this.plugin = plugin;
        this.stateService = stateService;
        this.dataStore = dataStore;
        this.registryService = registryService;
        this.castModeService = castModeService;
        this.messageService = messageService;
        this.skillsGuiService = skillsGuiService;
    }

    @Override
    public void onSlotClick(GuiSession session, InventoryClickEvent event, GuiTemplate.ResolvedSlot slot) {
        event.setCancelled(true);
        if (slot == null || slot.definition() == null || slot.definition().type() == null) {
            return;
        }
        Player player = session.viewer();
        String type = slot.definition().type();

        switch (type) {
            case "active_slot" -> handleActiveSlotClick(session, event, slot, player);
            case "skill_pool" -> handleSkillPoolClick(session, slot, player);
            case "cast_mode_toggle" -> handleCastModeToggle(session, player);
            case "refresh" -> handleRefresh(session, player);
            case "page_prev" -> handlePagePrev(session);
            case "page_next" -> handlePageNext(session, player);
            case "close" -> player.closeInventory();
            default -> { /* filler or unknown — do nothing */ }
        }
    }

    @Override
    public void onPlayerInventoryClick(GuiSession session, InventoryClickEvent event) {
        if (GuiSessionHandler.isBlockedTransfer(event)) {
            event.setCancelled(true);
        }
    }

    @Override
    public void onDrag(GuiSession session, InventoryDragEvent event) {
        event.setCancelled(true);
    }

    @Override
    public void onClose(GuiSession session, InventoryCloseEvent event) {
        Player player = session.viewer();
        dataStore.save(player);
    }

    // ------------------------------------------------------------------
    // Slot type handlers
    // ------------------------------------------------------------------

    private void handleActiveSlotClick(GuiSession session, InventoryClickEvent event,
            GuiTemplate.ResolvedSlot slot, Player player) {
        int slotIndex = slot.slotIndex();
        PlayerSkillProfile profile = dataStore.get(player);
        if (profile == null) {
            return;
        }
        SkillSlotBinding binding = profile.getBinding(slotIndex);
        if (binding == null || binding.isEmpty()) {
            return;
        }

        if (event.isShiftClick()) {
            // Shift-click: open trigger selection for this slot
            player.closeInventory();
            plugin.getServer().getScheduler().runTask(plugin, () ->
                    skillsGuiService.openTriggerSelect(player, slotIndex));
        } else {
            // Normal click: unequip
            stateService.unequipSkill(player, slotIndex);
            messageService.send(player, "gui.skill_unequipped");
            skillsGuiService.renderSkillsGui(session);
        }
    }

    private void handleSkillPoolClick(GuiSession session, GuiTemplate.ResolvedSlot slot, Player player) {
        int page = getPage(session);
        List<UnlockedSkillEntry> unlocked = stateService.getUnlockedSkills(player);
        List<String> poolSlotPositions = poolSlotPositions(session);
        int poolSize = poolSlotPositions.size();
        int index = page * poolSize + slot.slotIndex();
        if (index < 0 || index >= unlocked.size()) {
            return;
        }

        UnlockedSkillEntry entry = unlocked.get(index);
        SkillDefinition definition = registryService.getDefinition(entry.skillId());
        if (definition == null || !definition.enabled()) {
            return;
        }

        PlayerSkillProfile profile = dataStore.get(player);
        if (profile == null) {
            return;
        }

        // Check if skill is already equipped
        for (SkillSlotBinding binding : profile.bindings()) {
            if (!binding.isEmpty() && entry.skillId().equals(binding.skillId())) {
                messageService.send(player, "gui.skill_already_equipped");
                return;
            }
        }

        // Find first empty slot
        int emptySlot = -1;
        for (SkillSlotBinding binding : profile.bindings()) {
            if (binding.isEmpty()) {
                emptySlot = binding.slotIndex();
                break;
            }
        }

        if (emptySlot < 0) {
            messageService.send(player, "gui.slots_full");
            return;
        }

        stateService.equipSkill(player, emptySlot, entry.skillId());
        messageService.send(player, "gui.skill_equipped", Map.of(
                "skill", definition.displayName(),
                "slot", emptySlot
        ));
        skillsGuiService.renderSkillsGui(session);
    }

    private void handleCastModeToggle(GuiSession session, Player player) {
        castModeService.toggleCastMode(player);
        boolean nowEnabled = castModeService.isCastModeEnabled(player);
        messageService.send(player, nowEnabled ? "gui.cast_mode_enabled" : "gui.cast_mode_disabled");
        skillsGuiService.renderSkillsGui(session);
    }

    private void handleRefresh(GuiSession session, Player player) {
        stateService.validateBindings(player);
        skillsGuiService.renderSkillsGui(session);
        messageService.send(player, "gui.refreshed");
    }

    private void handlePagePrev(GuiSession session) {
        int page = getPage(session);
        if (page > 0) {
            session.putReplacement(KEY_CURRENT_PAGE, page - 1);
            skillsGuiService.renderSkillsGui(session);
        }
    }

    private void handlePageNext(GuiSession session, Player player) {
        int page = getPage(session);
        List<UnlockedSkillEntry> unlocked = stateService.getUnlockedSkills(player);
        List<String> poolSlotPositions = poolSlotPositions(session);
        int poolSize = Math.max(1, poolSlotPositions.size());
        int totalPages = Math.max(1, (int) Math.ceil((double) unlocked.size() / poolSize));
        if (page < totalPages - 1) {
            session.putReplacement(KEY_CURRENT_PAGE, page + 1);
            skillsGuiService.renderSkillsGui(session);
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    static int getPage(GuiSession session) {
        Object raw = session.replacements().get(KEY_CURRENT_PAGE);
        if (raw instanceof Number number) {
            return Math.max(0, number.intValue());
        }
        return 0;
    }

    private List<String> poolSlotPositions(GuiSession session) {
        return session.template().slotsByType("skill_pool").stream()
                .flatMap(s -> s.slots().stream().map(String::valueOf))
                .toList();
    }
}
