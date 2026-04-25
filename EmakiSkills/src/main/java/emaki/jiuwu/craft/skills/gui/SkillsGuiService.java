package emaki.jiuwu.craft.skills.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.gui.GuiOpenRequest;
import emaki.jiuwu.craft.corelib.gui.GuiRenderer;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.corelib.gui.GuiSlot;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.skills.model.PlayerSkillProfile;
import emaki.jiuwu.craft.skills.model.SkillDefinition;
import emaki.jiuwu.craft.skills.model.SkillSlotBinding;
import emaki.jiuwu.craft.skills.model.UnlockedSkillEntry;
import emaki.jiuwu.craft.skills.service.CastModeService;
import emaki.jiuwu.craft.skills.service.PlayerSkillDataStore;
import emaki.jiuwu.craft.skills.service.PlayerSkillStateService;
import emaki.jiuwu.craft.skills.service.SkillRegistryService;
import emaki.jiuwu.craft.skills.trigger.SkillTriggerDefinition;
import emaki.jiuwu.craft.skills.trigger.TriggerRegistry;

public final class SkillsGuiService {

    private static final String TEMPLATE_SKILLS_GUI = "skills_gui";
    private static final String TEMPLATE_TRIGGER_SELECT = "trigger_select_gui";

    private final JavaPlugin plugin;
    private final GuiService guiService;
    private final GuiTemplateLoader guiTemplateLoader;
    private final PlayerSkillStateService stateService;
    private final PlayerSkillDataStore dataStore;
    private final SkillRegistryService registryService;
    private final TriggerRegistry triggerRegistry;
    private final CastModeService castModeService;
    private final MessageService messageService;

    public SkillsGuiService(JavaPlugin plugin,
            GuiService guiService,
            GuiTemplateLoader guiTemplateLoader,
            PlayerSkillStateService stateService,
            PlayerSkillDataStore dataStore,
            SkillRegistryService registryService,
            TriggerRegistry triggerRegistry,
            CastModeService castModeService,
            MessageService messageService) {
        this.plugin = plugin;
        this.guiService = guiService;
        this.guiTemplateLoader = guiTemplateLoader;
        this.stateService = stateService;
        this.dataStore = dataStore;
        this.registryService = registryService;
        this.triggerRegistry = triggerRegistry;
        this.castModeService = castModeService;
        this.messageService = messageService;
    }

    // ------------------------------------------------------------------
    // Open GUIs
    // ------------------------------------------------------------------

    public boolean open(Player player) {
        if (player == null) {
            return false;
        }
        GuiTemplate template = guiTemplateLoader.get(TEMPLATE_SKILLS_GUI);
        if (template == null) {
            plugin.getLogger().warning("[SkillsGui] Template '" + TEMPLATE_SKILLS_GUI + "' not found");
            return false;
        }

        SkillsGuiHandler handler = new SkillsGuiHandler(
                plugin, stateService, dataStore, registryService,
                castModeService, messageService, this);

        Map<String, Object> replacements = new LinkedHashMap<>();
        replacements.put(SkillsGuiHandler.KEY_CURRENT_PAGE, 0);
        replacements.put(SkillsGuiHandler.KEY_SELECTED_SLOT, -1);

        GuiRenderer renderer = (session, slot) -> renderSkillsSlot(session, slot);

        GuiOpenRequest request = new GuiOpenRequest(
                plugin, player, template, replacements, null, renderer, handler);
        GuiSession session = guiService.open(request);
        return session != null;
    }

    public boolean openTriggerSelect(Player player, int targetSlot) {
        if (player == null) {
            return false;
        }
        GuiTemplate template = guiTemplateLoader.get(TEMPLATE_TRIGGER_SELECT);
        if (template == null) {
            plugin.getLogger().warning("[SkillsGui] Template '" + TEMPLATE_TRIGGER_SELECT + "' not found");
            return false;
        }

        Runnable onBack = () -> open(player);

        TriggerSelectGuiHandler handler = new TriggerSelectGuiHandler(
                plugin, targetSlot, stateService, triggerRegistry,
                messageService, onBack);

        Map<String, Object> replacements = new LinkedHashMap<>();
        replacements.put(TriggerSelectGuiHandler.KEY_TARGET_SLOT, targetSlot);

        GuiRenderer renderer = (session, slot) -> renderTriggerSelectSlot(session, slot, targetSlot, player);

        GuiOpenRequest request = new GuiOpenRequest(
                plugin, player, template, replacements, null, renderer, handler);
        GuiSession session = guiService.open(request);
        return session != null;
    }

    public void clearAllSessions() {
        // Sessions are managed by GuiService; closing inventories handles cleanup
    }

    // ------------------------------------------------------------------
    // Render: Skills GUI
    // ------------------------------------------------------------------

    public void renderSkillsGui(GuiSession session) {
        if (session == null) {
            return;
        }
        session.refresh();
    }

    private ItemStack renderSkillsSlot(GuiSession session, GuiTemplate.ResolvedSlot resolved) {
        if (resolved == null || resolved.definition() == null) {
            return null;
        }
        String type = resolved.definition().type();
        if (type == null) {
            return null; // fall back to template default
        }
        Player player = session.viewer();

        return switch (type) {
            case "active_slot" -> renderActiveSlot(player, resolved.slotIndex());
            case "skill_pool" -> renderSkillPoolSlot(session, player, resolved.slotIndex());
            case "cast_mode_toggle" -> renderCastModeToggle(player);
            case "page_info" -> renderPageInfo(session, player);
            default -> null; // use template default for filler, close, refresh, page_prev, page_next
        };
    }

    private ItemStack renderActiveSlot(Player player, int slotIndex) {
        PlayerSkillProfile profile = dataStore.get(player);
        if (profile == null) {
            return emptySlotItem(slotIndex);
        }
        SkillSlotBinding binding = profile.getBinding(slotIndex);
        if (binding == null || binding.isEmpty()) {
            return emptySlotItem(slotIndex);
        }

        SkillDefinition definition = registryService.getDefinition(binding.skillId());
        if (definition == null) {
            return emptySlotItem(slotIndex);
        }

        Material icon = resolveIcon(definition.iconMaterial());
        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            ItemTextBridge.customNameText(meta, "<gold>" + definition.displayName());

            List<String> lore = new ArrayList<>();
            lore.add("<gray>槽位: <white>" + slotIndex);

            // Trigger info
            String triggerDisplay = "<red>未绑定触发器";
            if (binding.triggerId() != null && !binding.triggerId().isBlank()) {
                triggerDisplay = "<green>" + triggerRegistry.getDisplayName(binding.triggerId());
            }
            lore.add("<gray>触发器: " + triggerDisplay);

            // Cooldown info
            if (definition.cooldownTicks() > 0) {
                double seconds = definition.cooldownTicks() / 20.0;
                lore.add("<gray>冷却: <aqua>" + String.format("%.1f", seconds) + "s");
            }

            lore.add("");
            lore.add("<yellow>点击 <gray>卸下技能");
            lore.add("<yellow>Shift+点击 <gray>更换触发器");

            ItemTextBridge.setLoreLines(meta, lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack emptySlotItem(int slotIndex) {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            ItemTextBridge.customNameText(meta, "<gray>空技能槽 <dark_gray>#" + slotIndex);
            List<String> lore = new ArrayList<>();
            lore.add("<dark_gray>从技能池中选择技能装备");
            ItemTextBridge.setLoreLines(meta, lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack renderSkillPoolSlot(GuiSession session, Player player, int slotIndex) {
        int page = SkillsGuiHandler.getPage(session);
        List<UnlockedSkillEntry> unlocked = stateService.getUnlockedActiveSkills(player);
        List<GuiSlot> poolSlots = session.template().slotsByType("skill_pool");
        int poolSize = 0;
        for (GuiSlot poolSlot : poolSlots) {
            poolSize += poolSlot.slots().size();
        }
        poolSize = Math.max(1, poolSize);

        int index = page * poolSize + slotIndex;
        if (index < 0 || index >= unlocked.size()) {
            return new ItemStack(Material.AIR);
        }

        UnlockedSkillEntry entry = unlocked.get(index);
        SkillDefinition definition = registryService.getDefinition(entry.skillId());
        if (definition == null) {
            return new ItemStack(Material.AIR);
        }

        // Check if already equipped
        boolean equipped = isSkillEquipped(player, entry.skillId());

        Material icon = resolveIcon(definition.iconMaterial());
        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String nameColor = equipped ? "<gray><strikethrough>" : "<green>";
            ItemTextBridge.customNameText(meta, nameColor + definition.displayName());

            List<String> lore = new ArrayList<>();
            for (String line : definition.description()) {
                lore.add("<gray>" + line);
            }

            if (entry.sourceType() != null) {
                lore.add("");
                String sourceLabel = switch (entry.sourceType()) {
                    case EQUIPMENT -> "<blue>装备";
                    case PROVIDER -> "<light_purple>外部来源";
                };
                lore.add("<dark_gray>来源: " + sourceLabel);
            }

            if (equipped) {
                lore.add("");
                lore.add("<red>已装备");
            } else {
                lore.add("");
                lore.add("<yellow>点击装备到空槽位");
            }

            ItemTextBridge.setLoreLines(meta, lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack renderCastModeToggle(Player player) {
        boolean enabled = castModeService.isCastModeEnabled(player);
        Material material = enabled ? Material.LIME_DYE : Material.GRAY_DYE;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String state = enabled ? "<green>开启" : "<red>关闭";
            ItemTextBridge.customNameText(meta, "<gold>施法模式: " + state);
            List<String> lore = new ArrayList<>();
            lore.add("<gray>点击切换施法模式");
            if (enabled) {
                lore.add("<green>当前: 施法模式已激活");
            } else {
                lore.add("<red>当前: 施法模式未激活");
            }
            ItemTextBridge.setLoreLines(meta, lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack renderPageInfo(GuiSession session, Player player) {
        int page = SkillsGuiHandler.getPage(session);
        List<UnlockedSkillEntry> unlocked = stateService.getUnlockedActiveSkills(player);
        List<GuiSlot> poolSlots = session.template().slotsByType("skill_pool");
        int poolSize = 0;
        for (GuiSlot poolSlot : poolSlots) {
            poolSize += poolSlot.slots().size();
        }
        poolSize = Math.max(1, poolSize);
        int totalPages = Math.max(1, (int) Math.ceil((double) unlocked.size() / poolSize));

        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            ItemTextBridge.customNameText(meta, "<gold>页码: <white>" + (page + 1) + " / " + totalPages);
            List<String> lore = new ArrayList<>();
            lore.add("<gray>已解锁技能: <white>" + unlocked.size());
            ItemTextBridge.setLoreLines(meta, lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    // ------------------------------------------------------------------
    // Render: Trigger Select GUI
    // ------------------------------------------------------------------

    public void renderTriggerSelectGui(GuiSession session, int targetSlot) {
        if (session == null) {
            return;
        }
        session.refresh();
    }

    private ItemStack renderTriggerSelectSlot(GuiSession session, GuiTemplate.ResolvedSlot resolved,
            int targetSlot, Player player) {
        if (resolved == null || resolved.definition() == null) {
            return null;
        }
        String type = resolved.definition().type();
        if (type == null) {
            return null;
        }

        if ("trigger_option".equals(type)) {
            return renderTriggerOption(player, resolved.slotIndex(), targetSlot);
        }
        return null; // use template default for back, filler
    }

    private ItemStack renderTriggerOption(Player player, int slotIndex, int targetSlot) {
        List<SkillTriggerDefinition> enabledTriggers = getEnabledTriggers();
        if (slotIndex < 0 || slotIndex >= enabledTriggers.size()) {
            return new ItemStack(Material.AIR);
        }

        SkillTriggerDefinition trigger = enabledTriggers.get(slotIndex);

        // Check conflict
        String conflict = stateService.checkTriggerConflict(player, targetSlot, trigger.id());
        boolean hasConflict = conflict != null;

        // Check if currently bound to this slot
        PlayerSkillProfile profile = dataStore.get(player);
        boolean currentlyBound = false;
        if (profile != null) {
            SkillSlotBinding binding = profile.getBinding(targetSlot);
            if (binding != null && trigger.id().equals(binding.triggerId())) {
                currentlyBound = true;
            }
        }

        Material material;
        if (currentlyBound) {
            material = Material.YELLOW_STAINED_GLASS_PANE;
        } else if (hasConflict) {
            material = Material.RED_STAINED_GLASS_PANE;
        } else {
            material = Material.LIME_STAINED_GLASS_PANE;
        }

        // Override with trigger's own material if specified
        if (trigger.material() != null && !trigger.material().isBlank()) {
            Material triggerMat = ItemSourceUtil.resolveVanillaMaterial(trigger.material());
            if (triggerMat != null) {
                material = triggerMat;
            }
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String nameColor = hasConflict ? "<red>" : (currentlyBound ? "<yellow>" : "<green>");
            ItemTextBridge.customNameText(meta, nameColor + trigger.displayName());

            List<String> lore = new ArrayList<>();
            if (trigger.description() != null && !trigger.description().isBlank()) {
                lore.add("<gray>" + trigger.description());
            }

            if (currentlyBound) {
                lore.add("");
                lore.add("<yellow>当前已绑定");
            } else if (hasConflict) {
                lore.add("");
                lore.add("<red>存在冲突: " + conflict);
            } else {
                lore.add("");
                lore.add("<green>点击绑定此触发器");
            }

            ItemTextBridge.setLoreLines(meta, lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    // ------------------------------------------------------------------
    // Utility
    // ------------------------------------------------------------------

    private Material resolveIcon(String iconMaterial) {
        if (iconMaterial == null || iconMaterial.isBlank()) {
            return Material.NETHER_STAR;
        }
        Material material = ItemSourceUtil.resolveVanillaMaterial(iconMaterial);
        return material != null ? material : Material.NETHER_STAR;
    }

    private boolean isSkillEquipped(Player player, String skillId) {
        PlayerSkillProfile profile = dataStore.get(player);
        if (profile == null) {
            return false;
        }
        for (SkillSlotBinding binding : profile.bindings()) {
            if (!binding.isEmpty() && skillId.equals(binding.skillId())) {
                return true;
            }
        }
        return false;
    }

    private List<SkillTriggerDefinition> getEnabledTriggers() {
        List<SkillTriggerDefinition> result = new ArrayList<>();
        for (SkillTriggerDefinition def : triggerRegistry.all().values()) {
            if (def.enabled()) {
                result.add(def);
            }
        }
        return result;
    }
}
