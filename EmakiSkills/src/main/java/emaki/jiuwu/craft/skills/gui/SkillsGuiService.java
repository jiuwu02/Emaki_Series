package emaki.jiuwu.craft.skills.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.gui.GuiItemBuilder;
import emaki.jiuwu.craft.corelib.gui.GuiOpenRequest;
import emaki.jiuwu.craft.corelib.gui.GuiRenderer;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.corelib.gui.GuiSlot;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.gui.ItemComponentParser;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.skills.model.PlayerSkillProfile;
import emaki.jiuwu.craft.skills.model.ResolvedSkillParameters;
import emaki.jiuwu.craft.skills.model.SkillDefinition;
import emaki.jiuwu.craft.skills.model.SkillSlotBinding;
import emaki.jiuwu.craft.skills.model.UnlockedSkillEntry;
import emaki.jiuwu.craft.skills.service.CastModeService;
import emaki.jiuwu.craft.skills.service.PlayerSkillDataStore;
import emaki.jiuwu.craft.skills.service.PlayerSkillStateService;
import emaki.jiuwu.craft.skills.service.SkillLevelService;
import emaki.jiuwu.craft.skills.service.SkillParameterResolver;
import emaki.jiuwu.craft.skills.service.SkillRegistryService;
import emaki.jiuwu.craft.skills.trigger.SkillTriggerDefinition;
import emaki.jiuwu.craft.skills.trigger.TriggerCategory;
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
    private final SkillLevelService skillLevelService;
    private final SkillParameterResolver skillParameterResolver;
    private final MessageService messageService;

    public SkillsGuiService(JavaPlugin plugin,
            GuiService guiService,
            GuiTemplateLoader guiTemplateLoader,
            PlayerSkillStateService stateService,
            PlayerSkillDataStore dataStore,
            SkillRegistryService registryService,
            TriggerRegistry triggerRegistry,
            CastModeService castModeService,
            SkillLevelService skillLevelService,
            SkillParameterResolver skillParameterResolver,
            MessageService messageService) {
        this.plugin = plugin;
        this.guiService = guiService;
        this.guiTemplateLoader = guiTemplateLoader;
        this.stateService = stateService;
        this.dataStore = dataStore;
        this.registryService = registryService;
        this.triggerRegistry = triggerRegistry;
        this.castModeService = castModeService;
        this.skillLevelService = skillLevelService;
        this.skillParameterResolver = skillParameterResolver;
        this.messageService = messageService;
    }


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
    }


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
            return null;
        }
        Player player = session.viewer();

        GuiSlot slot = resolved.definition();
        return switch (type) {
            case "active_slot" -> renderActiveSlot(player, slot, resolved.slotIndex());
            case "skill_pool" -> renderSkillPoolSlot(session, player, slot, resolved.slotIndex());
            case "cast_mode_toggle" -> renderCastModeToggle(player, slot);
            case "page_info" -> renderPageInfo(session, player, slot);
            default -> null;
        };
    }

    private ItemStack renderActiveSlot(Player player, GuiSlot slot, int slotIndex) {
        PlayerSkillProfile profile = dataStore.get(player);
        if (profile == null) {
            return emptySlotItem(slot, slotIndex);
        }
        SkillSlotBinding binding = profile.getBinding(slotIndex);
        if (binding == null || binding.isEmpty()) {
            return emptySlotItem(slot, slotIndex);
        }

        SkillDefinition definition = registryService.getDefinition(binding.skillId());
        if (definition == null) {
            return emptySlotItem(slot, slotIndex);
        }

        String triggerDisplay = "<red>未绑定触发器";
        String triggerPlain = "未绑定触发器";
        if (binding.triggerId() != null && !binding.triggerId().isBlank()) {
            String displayName = triggerRegistry.getDisplayName(binding.triggerId());
            triggerDisplay = "<green>" + displayName;
            triggerPlain = displayName;
        }
        List<String> lore = new ArrayList<>();
        lore.add("<gray>槽位: <white>" + slotIndex);
        lore.add("<gray>触发器: " + triggerDisplay);
        appendLevelAndParameterLore(lore, player, definition);
        if (definition.cooldownTicks() > 0) {
            lore.add("<gray>冷却: <aqua>" + cooldownSeconds(definition.cooldownTicks()) + "s");
        }
        lore.add("");
        lore.add("<yellow>点击 <gray>卸下技能");
        lore.add("<yellow>Shift+点击 <gray>更换触发器");
        Map<String, Object> replacements = activeSlotReplacements(player, slotIndex, definition, binding, triggerPlain);
        return buildConfiguredItem(slot, definition.iconMaterial(), "<gold>" + definition.displayName(), lore, replacements);
    }

    private ItemStack emptySlotItem(GuiSlot slot, int slotIndex) {
        Map<String, Object> replacements = new LinkedHashMap<>();
        replacements.put("slot", slotIndex);
        replacements.put("skill", "空技能槽");
        replacements.put("skill_id", "");
        replacements.put("trigger", "未绑定触发器");
        replacements.put("trigger_id", "");
        replacements.put("level", 0);
        replacements.put("max_level", 0);
        replacements.put("cooldown", "0.0");
        replacements.put("cooldown_ticks", 0);
        return buildConfiguredItem(slot, "gray_stained_glass_pane", "<gray>空技能槽 <dark_gray>#" + slotIndex,
                List.of("<dark_gray>从技能池中选择技能装备"), replacements);
    }

    private ItemStack renderSkillPoolSlot(GuiSession session, Player player, GuiSlot slot, int slotIndex) {
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

        boolean equipped = isSkillEquipped(player, entry.skillId());

        String nameColor = equipped ? "<gray><strikethrough>" : "<green>";
        List<String> lore = new ArrayList<>();
        for (String line : definition.description()) {
            lore.add("<gray>" + line);
        }
        appendLevelAndParameterLore(lore, player, definition);
        String sourceLabel = "";
        if (entry.sourceType() != null) {
            lore.add("");
            sourceLabel = switch (entry.sourceType()) {
                case EQUIPMENT -> "装备";
                case PROVIDER -> "外部来源";
                case MANUAL -> "手动解锁";
            };
            String coloredSourceLabel = switch (entry.sourceType()) {
                case EQUIPMENT -> "<blue>装备";
                case PROVIDER -> "<light_purple>外部来源";
                case MANUAL -> "<gold>手动解锁";
            };
            lore.add("<dark_gray>来源: " + coloredSourceLabel);
        }
        lore.add("");
        lore.add(equipped ? "<red>已装备" : "<yellow>点击装备到空槽位");
        Map<String, Object> replacements = skillPoolReplacements(player, definition, entry, equipped, sourceLabel);
        return buildConfiguredItem(slot, definition.iconMaterial(), nameColor + definition.displayName(), lore, replacements);
    }

    private ItemStack renderCastModeToggle(Player player, GuiSlot slot) {
        boolean enabled = castModeService.isCastModeEnabled(player);
        String state = enabled ? "<green>开启" : "<red>关闭";
        List<String> lore = new ArrayList<>();
        lore.add("<gray>点击切换施法模式");
        lore.add(enabled ? "<green>当前: 施法模式已激活" : "<red>当前: 施法模式未激活");
        return buildConfiguredItem(slot, enabled ? "lime_dye" : "gray_dye", "<gold>施法模式: " + state, lore,
                Map.of("state", enabled ? "开启" : "关闭", "enabled", enabled));
    }

    private ItemStack renderPageInfo(GuiSession session, Player player, GuiSlot slot) {
        int page = SkillsGuiHandler.getPage(session);
        List<UnlockedSkillEntry> unlocked = stateService.getUnlockedActiveSkills(player);
        List<GuiSlot> poolSlots = session.template().slotsByType("skill_pool");
        int poolSize = 0;
        for (GuiSlot poolSlot : poolSlots) {
            poolSize += poolSlot.slots().size();
        }
        poolSize = Math.max(1, poolSize);
        int totalPages = Math.max(1, (int) Math.ceil((double) unlocked.size() / poolSize));

        Map<String, Object> replacements = Map.of(
                "current_page", page + 1,
                "total_pages", totalPages,
                "page", page + 1,
                "pages", totalPages,
                "unlocked_count", unlocked.size()
        );
        return buildConfiguredItem(slot, "paper", "<gold>页码: <white>" + (page + 1) + " / " + totalPages,
                List.of("<gray>已解锁技能: <white>" + unlocked.size()), replacements);
    }


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
            return renderTriggerOption(player, resolved.definition(), resolved.slotIndex(), targetSlot);
        }
        return null;
    }

    private ItemStack renderTriggerOption(Player player, GuiSlot slot, int slotIndex, int targetSlot) {
        List<SkillTriggerDefinition> enabledTriggers = getEnabledTriggers();
        if (slotIndex < 0 || slotIndex >= enabledTriggers.size()) {
            return new ItemStack(Material.AIR);
        }

        SkillTriggerDefinition trigger = enabledTriggers.get(slotIndex);

        String conflict = stateService.checkTriggerConflict(player, targetSlot, trigger.id());
        boolean hasConflict = conflict != null;

        PlayerSkillProfile profile = dataStore.get(player);
        boolean currentlyBound = false;
        if (profile != null) {
            SkillSlotBinding binding = profile.getBinding(targetSlot);
            if (binding != null && trigger.id().equals(binding.triggerId())) {
                currentlyBound = true;
            }
        }

        String fallbackItem;
        if (Texts.isNotBlank(trigger.material()) && ItemSourceUtil.resolveVanillaMaterial(trigger.material()) != null) {
            fallbackItem = trigger.material();
        } else if (currentlyBound) {
            fallbackItem = "yellow_stained_glass_pane";
        } else if (hasConflict) {
            fallbackItem = "red_stained_glass_pane";
        } else {
            fallbackItem = "lime_stained_glass_pane";
        }

        String nameColor = hasConflict ? "<red>" : (currentlyBound ? "<yellow>" : "<green>");
        List<String> lore = new ArrayList<>();
        if (Texts.isNotBlank(trigger.description())) {
            lore.add("<gray>" + trigger.description());
        }
        lore.add("");
        if (currentlyBound) {
            lore.add("<yellow>当前已绑定");
        } else if (hasConflict) {
            lore.add("<red>存在冲突: " + conflict);
        } else {
            lore.add("<green>点击绑定此触发器");
        }

        Map<String, Object> replacements = Map.of(
                "target_slot", targetSlot,
                "trigger", trigger.displayName(),
                "trigger_id", trigger.id(),
                "description", Texts.toStringSafe(trigger.description()),
                "conflict", Texts.toStringSafe(conflict),
                "bound", currentlyBound,
                "available", !currentlyBound && !hasConflict
        );
        return buildConfiguredItem(slot, fallbackItem, nameColor + trigger.displayName(), lore, replacements);
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
            if (def.enabled() && def.category() == TriggerCategory.ACTIVE) {
                result.add(def);
            }
        }
        return result;
    }

    private void appendLevelAndParameterLore(List<String> lore, Player player, SkillDefinition definition) {
        if (lore == null || player == null || definition == null || skillLevelService == null) {
            return;
        }
        int level = skillLevelService.currentLevel(player, definition);
        int maxLevel = skillLevelService.maxLevel(definition);
        if (maxLevel > 1) {
            lore.add("<gray>等级: <gold>" + level + "</gold><dark_gray>/</dark_gray><yellow>" + maxLevel + "</yellow>");
        }
        if (skillParameterResolver == null || definition.skillParameters().isEmpty()) {
            return;
        }
        ResolvedSkillParameters parameters = skillParameterResolver.resolve(player, definition, "preview", null);
        int shown = 0;
        for (Map.Entry<String, String> entry : parameters.values().entrySet()) {
            if (entry.getKey().startsWith("emaki_")) {
                continue;
            }
            if (shown == 0) {
                lore.add("<dark_gray>参数预览:");
            }
            lore.add("<gray> - " + entry.getKey() + ": <white>" + entry.getValue());
            shown++;
            if (shown >= 3) {
                break;
            }
        }
    }

    private ItemStack buildConfiguredItem(GuiSlot slot,
            String fallbackItem,
            String fallbackName,
            List<String> fallbackLore,
            Map<String, ?> replacements) {
        ItemComponentParser.ItemComponents fallbackComponents = new ItemComponentParser.ItemComponents(
                fallbackName,
                true,
                fallbackLore == null ? List.of() : fallbackLore,
                null,
                null,
                Map.of(),
                List.of()
        );
        ItemComponentParser.ItemComponents components = hasConfiguredComponents(slot)
                ? slot.components()
                : fallbackComponents;
        String configuredItem = slot == null ? null : slot.item();
        String item = Texts.isBlank(configuredItem) ? fallbackItem : configuredItem;
        return GuiItemBuilder.build(
                Texts.isBlank(item) ? "nether_star" : item,
                components,
                1,
                replacements == null ? Map.of() : replacements,
                null
        );
    }

    private boolean hasConfiguredComponents(GuiSlot slot) {
        if (slot == null || slot.components() == null) {
            return false;
        }
        ItemComponentParser.ItemComponents components = slot.components();
        return Texts.isNotBlank(components.displayName())
                || components.displayNameConfig() != null
                || components.loreConfigured()
                || Texts.isNotBlank(components.itemModel())
                || components.customModelData() != null
                || !components.enchantments().isEmpty()
                || !components.hiddenComponents().isEmpty();
    }

    private Map<String, Object> activeSlotReplacements(Player player,
            int slotIndex,
            SkillDefinition definition,
            SkillSlotBinding binding,
            String triggerDisplay) {
        Map<String, Object> replacements = baseSkillReplacements(player, definition);
        replacements.put("slot", slotIndex);
        replacements.put("trigger", triggerDisplay);
        replacements.put("trigger_id", binding == null ? "" : Texts.toStringSafe(binding.triggerId()));
        return replacements;
    }

    private Map<String, Object> skillPoolReplacements(Player player,
            SkillDefinition definition,
            UnlockedSkillEntry entry,
            boolean equipped,
            String sourceLabel) {
        Map<String, Object> replacements = baseSkillReplacements(player, definition);
        replacements.put("equipped", equipped);
        replacements.put("source", sourceLabel == null ? "" : sourceLabel);
        replacements.put("source_type", entry == null || entry.sourceType() == null ? "" : entry.sourceType().name().toLowerCase(java.util.Locale.ROOT));
        return replacements;
    }

    private Map<String, Object> baseSkillReplacements(Player player, SkillDefinition definition) {
        Map<String, Object> replacements = new LinkedHashMap<>();
        if (definition == null) {
            return replacements;
        }
        replacements.put("skill", definition.displayName());
        replacements.put("skill_id", definition.id());
        replacements.put("cooldown", cooldownSeconds(definition.cooldownTicks()));
        replacements.put("cooldown_ticks", definition.cooldownTicks());
        replacements.put("tags", String.join(",", definition.tags()));
        replacements.put("tab_tags", String.join(",", definition.tabTags()));
        int level = skillLevelService == null || player == null ? 1 : skillLevelService.currentLevel(player, definition);
        int maxLevel = skillLevelService == null ? 1 : skillLevelService.maxLevel(definition);
        replacements.put("level", level);
        replacements.put("max_level", maxLevel);
        return replacements;
    }

    private String cooldownSeconds(long cooldownTicks) {
        return String.format(java.util.Locale.ROOT, "%.1f", cooldownTicks / 20.0D);
    }
}
