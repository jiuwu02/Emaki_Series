package emaki.jiuwu.craft.skills.service;

import java.util.Map;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.async.FoliaSchedulerAdapter;
import emaki.jiuwu.craft.corelib.async.TaskHandle;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.text.AdventureSupport;
import emaki.jiuwu.craft.skills.config.AppConfig;
import emaki.jiuwu.craft.skills.model.PlayerCastTimingState;
import emaki.jiuwu.craft.skills.model.PlayerSkillProfile;
import emaki.jiuwu.craft.skills.model.SkillDefinition;
import emaki.jiuwu.craft.skills.model.SkillSlotBinding;
import emaki.jiuwu.craft.skills.trigger.TriggerRegistry;

public final class ActionBarService {

    private final JavaPlugin plugin;
    private final PlayerSkillDataStore dataStore;
    private final CastModeService castModeService;
    private final Supplier<AppConfig> configSupplier;
    private final TriggerRegistry triggerRegistry;
    private final Supplier<Map<String, SkillDefinition>> skillDefsSupplier;
    private final MessageService messageService;
    private TaskHandle refreshTask;

    public ActionBarService(JavaPlugin plugin,
            PlayerSkillDataStore dataStore,
            CastModeService castModeService,
            Supplier<AppConfig> configSupplier,
            TriggerRegistry triggerRegistry,
            Supplier<Map<String, SkillDefinition>> skillDefsSupplier,
            MessageService messageService) {
        this.plugin = plugin;
        this.dataStore = dataStore;
        this.castModeService = castModeService;
        this.configSupplier = configSupplier;
        this.triggerRegistry = triggerRegistry;
        this.skillDefsSupplier = skillDefsSupplier;
        this.messageService = messageService;
    }

    public void startRefreshTask() {
        stopRefreshTask();
        AppConfig config = configSupplier.get();
        if (config == null || !config.actionBar().enabled()) {
            return;
        }
        int interval = config.actionBar().refreshIntervalTicks();
        refreshTask = FoliaSchedulerAdapter.runTaskTimer(plugin, this::refreshAll, interval, interval);
    }

    public void stopRefreshTask() {
        if (refreshTask != null) {
            FoliaSchedulerAdapter.cancelTask(refreshTask);
            refreshTask = null;
        }
    }

    public void refreshPlayer(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (!castModeService.isCastModeEnabled(player)) {
            return;
        }
        String text = buildActionBarText(player);
        if (text == null || text.isBlank()) {
            return;
        }
        AdventureSupport.sendActionBar(plugin, player, text);
    }

    public void refreshAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                refreshPlayer(player);
            } catch (Exception exception) {
                plugin.getLogger().warning("[ActionBar] Failed to refresh for "
                        + player.getName() + ": " + exception.getMessage());
            }
        }
    }

    public String buildActionBarText(Player player) {
        if (player == null) {
            return "";
        }
        AppConfig config = configSupplier.get();
        if (config == null) {
            return "";
        }
        if (!castModeService.isCastModeEnabled(player)) {
            return config.actionBar().templateIdle();
        }

        PlayerSkillProfile profile = dataStore.get(player);
        if (profile == null) {
            return config.actionBar().templateIdle();
        }

        String template = config.actionBar().templateCastMode();
        Map<String, SkillDefinition> defs = skillDefsSupplier.get();

        StringBuilder slotDisplay = new StringBuilder();
        for (int i = 0; i < profile.bindings().size(); i++) {
            SkillSlotBinding binding = profile.getBinding(i);
            if (i > 0) {
                slotDisplay.append(" ");
            }
            if (binding == null || binding.isEmpty()) {
                slotDisplay.append(messageService.message("gui.slot_empty_short"));
            } else {
                String skillName = resolveSkillName(binding.skillId(), defs);
                String triggerName = triggerRegistry.getDisplayName(
                        binding.triggerId() != null ? binding.triggerId() : "");
                slotDisplay.append(skillName).append(triggerName);
            }

            String slotText = null;
            String percentSlotPlaceholder = "%slot_" + (i + 1) + "%";
            if (template.contains(percentSlotPlaceholder)) {
                slotText = slotText(binding, defs);
                template = template.replace(percentSlotPlaceholder, slotText);
            }
            String legacySlotPlaceholder = "{slot_" + (i + 1) + "}";
            if (template.contains(legacySlotPlaceholder)) {
                if (slotText == null) {
                    slotText = slotText(binding, defs);
                }
                template = template.replace(legacySlotPlaceholder, slotText);
            }
        }

        template = template.replace("%slot_display%", slotDisplay.toString());

        PlayerCastTimingState timing = profile.timingState();
        long remaining = timing.forcedGlobalCastDelayUntil() - System.currentTimeMillis();
        String delayText = remaining > 0
                ? String.format("%.1fs", remaining / 1000.0)
                : "0s";
        template = template.replace("%forced_delay%", delayText);

        return template;
    }

    private String slotText(SkillSlotBinding binding, Map<String, SkillDefinition> defs) {
        if (binding == null || binding.isEmpty()) {
            return messageService.message("gui.slot_empty_short");
        }
        String skillName = resolveSkillName(binding.skillId(), defs);
        String triggerName = triggerRegistry.getDisplayName(
                binding.triggerId() != null ? binding.triggerId() : "");
        return skillName + triggerName;
    }

    private String resolveSkillName(String skillId, Map<String, SkillDefinition> defs) {
        if (skillId == null || defs == null) {
            return "???";
        }
        SkillDefinition def = defs.get(skillId);
        return def != null ? def.displayName() : skillId;
    }
}
