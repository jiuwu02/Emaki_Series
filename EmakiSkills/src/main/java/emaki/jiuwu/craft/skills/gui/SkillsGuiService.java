package emaki.jiuwu.craft.skills.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.gui.GuiItemBuilder;
import emaki.jiuwu.craft.corelib.gui.GuiOpenRequest;
import emaki.jiuwu.craft.corelib.gui.GuiPagination;
import emaki.jiuwu.craft.corelib.gui.GuiRenderer;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.corelib.gui.GuiSlot;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.inventory.InventoryItemUtil;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.api.math.Numbers;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.skills.EmakiSkillsPlugin;
import emaki.jiuwu.craft.skills.model.PlayerSkillProfile;

import emaki.jiuwu.craft.skills.model.SkillDefinition;
import emaki.jiuwu.craft.skills.model.SkillSlotBinding;
import emaki.jiuwu.craft.skills.model.SkillUpgradeConfig;
import emaki.jiuwu.craft.skills.model.UnlockedSkillEntry;
import emaki.jiuwu.craft.skills.service.SkillUpgradeService.CurrencyCost;
import emaki.jiuwu.craft.skills.service.SkillUpgradeService.MaterialCost;
import emaki.jiuwu.craft.skills.service.SkillUpgradeService.UpgradePreview;
import emaki.jiuwu.craft.skills.service.CastModeService;
import emaki.jiuwu.craft.skills.service.PlayerSkillDataStore;
import emaki.jiuwu.craft.skills.service.PlayerSkillStateService;
import emaki.jiuwu.craft.skills.service.SkillLevelService;

import emaki.jiuwu.craft.skills.service.SkillRegistryService;
import emaki.jiuwu.craft.skills.service.SkillUpgradeService;
import emaki.jiuwu.craft.skills.trigger.SkillTriggerDefinition;
import emaki.jiuwu.craft.skills.trigger.TriggerCategory;
import emaki.jiuwu.craft.skills.trigger.TriggerRegistry;

public final class SkillsGuiService {

    private static final String TEMPLATE_SKILLS_GUI = "skills_gui";
    private static final String TEMPLATE_TRIGGER_SELECT = "trigger_select_gui";
    private static final String TEMPLATE_UPGRADE = SkillUpgradeConfig.DEFAULT_GUI_TEMPLATE;

    private final EmakiSkillsPlugin plugin;
    private final GuiService guiService;
    private final GuiTemplateLoader guiTemplateLoader;
    private final PlayerSkillStateService stateService;
    private final PlayerSkillDataStore dataStore;
    private final SkillRegistryService registryService;
    private final TriggerRegistry triggerRegistry;
    private final CastModeService castModeService;
    private final SkillLevelService skillLevelService;

    private final SkillUpgradeService skillUpgradeService;
    private final MessageService messageService;

    public SkillsGuiService(EmakiSkillsPlugin plugin,
            GuiService guiService,
            GuiTemplateLoader guiTemplateLoader,
            PlayerSkillStateService stateService,
            PlayerSkillDataStore dataStore,
            SkillRegistryService registryService,
            TriggerRegistry triggerRegistry,
            CastModeService castModeService,
            SkillLevelService skillLevelService,
            SkillUpgradeService skillUpgradeService,
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
        this.skillUpgradeService = skillUpgradeService;
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
                plugin, player, template, replacements, renderer, handler);
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
                plugin, player, template, replacements, renderer, handler);
        GuiSession session = guiService.open(request);
        return session != null;
    }

    /**
     * 打开技能升级界面。
     *
     * <p>模板取自该技能 {@code upgrade.gui_template} 声明的 id，未配置或该 id
     * 不存在时回落到内置 {@value #TEMPLATE_UPGRADE}。</p>
     *
     * @param player  查看者
     * @param skillId 要升级的技能 id
     * @return 会话是否成功打开
     */
    public boolean openUpgradeGui(Player player, String skillId) {
        if (player == null) {
            return false;
        }
        String normalizedSkillId = Texts.normalizeId(skillId);
        SkillDefinition definition = registryService.getDefinition(normalizedSkillId);
        if (definition == null) {
            return false;
        }
        GuiTemplate template = resolveUpgradeTemplate(definition);
        if (template == null) {
            plugin.getLogger().warning("[SkillsGui] Template '" + TEMPLATE_UPGRADE + "' not found");
            return false;
        }

        Runnable onBack = () -> open(player);

        UpgradeGuiHandler handler = new UpgradeGuiHandler(
                plugin, normalizedSkillId, skillUpgradeService,
                messageService, this, onBack);

        Map<String, Object> replacements = new LinkedHashMap<>();
        replacements.put(UpgradeGuiHandler.KEY_SKILL_ID, normalizedSkillId);

        GuiRenderer renderer = (session, slot) -> renderUpgradeSlot(session, slot, normalizedSkillId);

        GuiOpenRequest request = new GuiOpenRequest(
                plugin, player, template, replacements, renderer, handler);
        GuiSession session = guiService.open(request);
        return session != null;
    }

    /**
     * {@return 该技能声明的升级模板，缺失时回落内置模板；两者都不存在时为 {@code null}}
     */
    private GuiTemplate resolveUpgradeTemplate(SkillDefinition definition) {
        SkillUpgradeConfig upgrade = definition == null ? null : definition.upgrade();
        String configured = upgrade == null ? null : Texts.normalizeId(upgrade.guiTemplate());
        if (Texts.isNotBlank(configured) && !TEMPLATE_UPGRADE.equals(configured)) {
            GuiTemplate custom = guiTemplateLoader.get(configured);
            if (custom != null) {
                return custom;
            }
            plugin.getLogger().warning("[SkillsGui] Skill '" + definition.id() + "' declares upgrade template '"
                    + configured + "' which does not exist; falling back to '" + TEMPLATE_UPGRADE + "'");
        }
        return guiTemplateLoader.get(TEMPLATE_UPGRADE);
    }

    public void clearAllSessions() {
        clearAllSessionsAsync().exceptionally(throwable -> {
            plugin.getLogger().warning("Failed to close skill GUI sessions: " + throwable.getMessage());
            return null;
        });
    }

    public CompletableFuture<Void> clearAllSessionsAsync() {
        List<CompletableFuture<Void>> closes = new ArrayList<>();
        for (Player player : List.copyOf(Bukkit.getOnlinePlayers())) {
            CompletableFuture<Void> close = new CompletableFuture<>();
            closes.add(close);
            try {
                var scheduled = plugin.executionDispatcher().runEntity(plugin, player, () -> {
                    try {
                        GuiSession session = guiService.getSession(player.getUniqueId());
                        if (session != null && session.owner() == plugin) {
                            guiService.close(player.getUniqueId());
                        }
                        close.complete(null);
                    } catch (Throwable throwable) {
                        close.completeExceptionally(throwable);
                    }
                }, () -> close.completeExceptionally(new RejectedExecutionException(
                        "Skills GUI close operation retired before execution.")));
                if (scheduled == null) {
                    close.completeExceptionally(new RejectedExecutionException(
                            "Skills GUI close operation scheduling was rejected."));
                }
            } catch (Throwable throwable) {
                close.completeExceptionally(throwable);
            }
        }
        return CompletableFuture.allOf(closes.toArray(CompletableFuture[]::new));
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

        String triggerPlain = binding.triggerId() == null || binding.triggerId().isBlank()
                ? messageService.message("gui.trigger_unbound")
                : triggerRegistry.getDisplayName(binding.triggerId());
        // Slot presentation belongs to gui/skills_gui.yml. A Java-side fallback lore
        // would be silently discarded whenever the template configures its own lore,
        // so any hint written here could never be relied upon.
        Map<String, Object> replacements = activeSlotReplacements(player, slotIndex, definition, binding, triggerPlain);
        return buildConfiguredItem(slot, definition.iconMaterial(), "<gold>" + definition.displayName(),
                List.of(), replacements);
    }

    private ItemStack emptySlotItem(GuiSlot slot, int slotIndex) {
        Map<String, Object> replacements = new LinkedHashMap<>();
        replacements.put("slot", slotIndex);
        replacements.put("skill", messageService.message("gui.slot_empty"));
        replacements.put("skill_id", "");
        replacements.put("trigger", messageService.message("gui.trigger_unbound"));
        replacements.put("trigger_id", "");
        replacements.put("level", 0);
        replacements.put("max_level", 0);
        replacements.put("cooldown", "0.0");
        replacements.put("cooldown_ticks", 0);
        // An empty slot deliberately reuses the configured active_slot presentation:
        // its name and lore come from gui/skills_gui.yml, not from this class.
        return buildConfiguredItem(slot, "gray_stained_glass_pane",
                messageService.message("gui.slot_empty") + " <dark_gray>#" + slotIndex,
                List.of(), replacements);
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
        appendLevelLore(lore, player, definition);
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
        int totalPages = GuiPagination.totalPages(unlocked.size(), poolSize);

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
        // The %status% placeholder carries the bound/conflicting/available wording so
        // gui/trigger_select_gui.yml owns the layout. A Java-side lore would be
        // dropped whenever that template defines its own lore.
        String status = currentlyBound
                ? messageService.message("gui.trigger_bound_current")
                : hasConflict
                        ? messageService.message("gui.trigger_conflict_hint", Map.of("trigger", Texts.toStringSafe(conflict)))
                        : messageService.message("gui.click_to_bind");
        Map<String, Object> replacements = Map.of(
                "target_slot", targetSlot,
                "trigger", trigger.displayName(),
                "trigger_id", trigger.id(),
                "description", Texts.toStringSafe(trigger.description()),
                "conflict", Texts.toStringSafe(conflict),
                "bound", currentlyBound,
                "available", !currentlyBound && !hasConflict,
                "status", status
        );
        return buildConfiguredItem(slot, fallbackItem, nameColor + trigger.displayName(), List.of(), replacements);
    }


    public void renderUpgradeGui(GuiSession session) {
        if (session == null) {
            return;
        }
        session.refresh();
    }

    private ItemStack renderUpgradeSlot(GuiSession session, GuiTemplate.ResolvedSlot resolved, String skillId) {
        if (resolved == null || resolved.definition() == null) {
            return null;
        }
        String type = resolved.definition().type();
        if (type == null) {
            return null;
        }
        Player player = session.viewer();
        SkillDefinition definition = registryService.getDefinition(skillId);
        if (definition == null) {
            return null;
        }
        // 每个槽位各自取一次预览：预览是纯读操作，且升级结算后必须反映新等级。
        UpgradePreview preview = skillUpgradeService == null ? null : skillUpgradeService.preview(player, definition);
        GuiSlot slot = resolved.definition();
        return switch (type) {
            case "skill_display" -> renderUpgradeSkillDisplay(slot, definition, preview);
            case "cost_currency" -> renderUpgradeCurrencyCost(slot, definition, preview);
            case "cost_material" -> renderUpgradeMaterialCost(player, slot, definition, preview);
            case "success_rate" -> renderUpgradeSuccessRate(slot, definition, preview);
            case "confirm" -> renderUpgradeConfirm(player, slot, definition, preview);
            default -> null;
        };
    }

    private ItemStack renderUpgradeSkillDisplay(GuiSlot slot, SkillDefinition definition, UpgradePreview preview) {
        Map<String, Object> replacements = upgradeBaseReplacements(definition, preview);
        return buildConfiguredItem(slot, definition.iconMaterial(),
                "<gold>" + definition.displayName(), List.of(), replacements);
    }

    private ItemStack renderUpgradeCurrencyCost(GuiSlot slot,
            SkillDefinition definition,
            UpgradePreview preview) {
        Map<String, Object> replacements = upgradeBaseReplacements(definition, preview);
        List<CurrencyCost> currencies = preview == null ? List.of() : preview.currencies();
        List<String> entries = new ArrayList<>();
        for (CurrencyCost currency : currencies) {
            if (currency == null) {
                continue;
            }
            entries.add(messageService.message("gui.upgrade_currency_entry", Map.of(
                    "display_name", currency.displayName(),
                    "amount", Numbers.formatNumber(currency.amount(), "0.##")
            )));
        }
        replacements.put("currencies", entries.isEmpty()
                ? List.of(messageService.message("gui.upgrade_cost_free"))
                : List.copyOf(entries));
        replacements.put("currency_count", entries.size());
        return buildConfiguredItem(slot, "gold_ingot",
                messageService.message("gui.upgrade_currency_title"), List.of(), replacements);
    }

    private ItemStack renderUpgradeMaterialCost(Player player,
            GuiSlot slot,
            SkillDefinition definition,
            UpgradePreview preview) {
        Map<String, Object> replacements = upgradeBaseReplacements(definition, preview);
        List<MaterialCost> materials = preview == null ? List.of() : preview.materials();
        List<String> entries = new ArrayList<>();
        int missing = 0;
        for (MaterialCost material : materials) {
            if (material == null) {
                continue;
            }
            long available = countMaterial(player, material);
            boolean satisfied = available >= material.amount();
            if (!satisfied) {
                missing++;
            }
            entries.add(messageService.message(satisfied
                    ? "gui.upgrade_material_entry"
                    : "gui.upgrade_material_entry_missing", Map.of(
                    "material", material.displayName(),
                    "required", material.amount(),
                    "available", available
            )));
        }
        replacements.put("materials", entries.isEmpty()
                ? List.of(messageService.message("gui.upgrade_cost_free"))
                : List.copyOf(entries));
        replacements.put("material_count", entries.size());
        replacements.put("missing_count", missing);
        return buildConfiguredItem(slot, "chest",
                messageService.message("gui.upgrade_material_title"), List.of(), replacements);
    }

    private ItemStack renderUpgradeSuccessRate(GuiSlot slot, SkillDefinition definition, UpgradePreview preview) {
        Map<String, Object> replacements = upgradeBaseReplacements(definition, preview);
        return buildConfiguredItem(slot, "experience_bottle",
                messageService.message("gui.upgrade_success_rate_title", replacements), List.of(), replacements);
    }

    /**
     * 渲染确认槽。
     *
     * <p>成本不足与已满级都<b>保留槽位</b>只改文案与图标（不隐藏）：隐藏会让玩家
     * 无法区分「不能升」与「界面坏了」。实际能否升级仍由
     * {@code SkillUpgradeService.upgrade} 判定，这里只是提示。</p>
     */
    private ItemStack renderUpgradeConfirm(Player player,
            GuiSlot slot,
            SkillDefinition definition,
            UpgradePreview preview) {
        Map<String, Object> replacements = upgradeBaseReplacements(definition, preview);
        boolean upgradeEnabled = definition.upgrade() != null && definition.upgrade().enabled();
        boolean maxLevel = preview != null && preview.currentLevel() >= preview.maxLevel();
        boolean affordable = upgradeEnabled && !maxLevel && hasAllMaterials(player, preview);

        String statusKey;
        String titleKey;
        String fallbackItem;
        if (!upgradeEnabled) {
            statusKey = "gui.upgrade_disabled_status";
            titleKey = "gui.upgrade_unavailable_title";
            fallbackItem = "barrier";
        } else if (maxLevel) {
            statusKey = "gui.upgrade_max_level_status";
            titleKey = "gui.upgrade_unavailable_title";
            fallbackItem = "barrier";
        } else if (!affordable) {
            statusKey = "gui.upgrade_insufficient_status";
            titleKey = "gui.upgrade_insufficient_title";
            fallbackItem = "redstone_block";
        } else {
            statusKey = "gui.upgrade_confirm_status";
            titleKey = "gui.upgrade_confirm_title";
            fallbackItem = "anvil";
        }
        String title = messageService.message(titleKey, replacements);
        replacements.put("confirm_title", title);
        replacements.put("confirm_status", messageService.message(statusKey, replacements));
        replacements.put("upgradable", affordable);
        return buildConfiguredItem(slot, fallbackItem, title, List.of(), replacements);
    }

    private boolean hasAllMaterials(Player player, UpgradePreview preview) {
        if (preview == null) {
            return false;
        }
        for (MaterialCost material : preview.materials()) {
            if (material != null && countMaterial(player, material) < material.amount()) {
                return false;
            }
        }
        return true;
    }

    private long countMaterial(Player player, MaterialCost material) {
        if (player == null || material == null || Texts.isBlank(material.item())) {
            return 0L;
        }
        return InventoryItemUtil.countItems(player,
                plugin.coreLib().itemSourceService(),
                ItemSourceUtil.parse(material.item()));
    }

    private Map<String, Object> upgradeBaseReplacements(SkillDefinition definition, UpgradePreview preview) {
        Map<String, Object> replacements = new LinkedHashMap<>();
        replacements.put("skill", definition == null ? "" : definition.displayName());
        replacements.put("skill_id", definition == null ? "" : definition.id());
        int level = preview == null ? 1 : preview.currentLevel();
        int targetLevel = preview == null ? 1 : preview.targetLevel();
        int maxLevel = preview == null ? 1 : preview.maxLevel();
        replacements.put("level", level);
        replacements.put("current_level", level);
        replacements.put("target_level", targetLevel);
        replacements.put("max_level", maxLevel);
        replacements.put("success_rate", preview == null
                ? "0"
                : Numbers.formatNumber(preview.successRate(), "0.##"));
        replacements.put("failure_penalty", failurePenaltyText(definition));
        return replacements;
    }

    private String failurePenaltyText(SkillDefinition definition) {
        SkillUpgradeConfig upgrade = definition == null ? null : definition.upgrade();
        String penalty = upgrade == null ? "none" : Texts.lower(upgrade.failurePenalty());
        return switch (penalty) {
            case "downgrade" -> messageService.message("gui.upgrade_penalty_downgrade");
            case "reset" -> messageService.message("gui.upgrade_penalty_reset");
            default -> messageService.message("gui.upgrade_penalty_none");
        };
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

    private void appendLevelLore(List<String> lore, Player player, SkillDefinition definition) {
        if (lore == null || player == null || definition == null || skillLevelService == null) {
            return;
        }
        int level = skillLevelService.currentLevel(player, definition);
        int maxLevel = skillLevelService.maxLevel(definition);
        if (maxLevel > 1) {
            lore.add("<gray>等级: <gold>" + level + "</gold><dark_gray>/</dark_gray><yellow>" + maxLevel + "</yellow>");
        }
    }

    private ItemStack buildConfiguredItem(GuiSlot slot,
            String fallbackItem,
            String fallbackName,
            List<String> fallbackLore,
            Map<String, ?> replacements) {
        String configuredItem = slot == null ? null : slot.item();
        String item = Texts.isBlank(configuredItem) ? fallbackItem : configuredItem;
        return GuiItemBuilder.build(
                slot,
                Texts.isBlank(item) ? "nether_star" : item,
                fallbackName,
                fallbackLore == null ? List.of() : fallbackLore,
                replacements == null ? Map.of() : replacements,
                plugin.coreLib().configuredItemService()
        );
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
        replacements.put("source_type", entry == null || entry.sourceType() == null ? "" : entry.sourceType().name().toLowerCase(Locale.ROOT));
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
        return String.format(Locale.ROOT, "%.1f", cooldownTicks / 20.0D);
    }
}
