package emaki.jiuwu.craft.skills.service;

import java.util.Map;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

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
    private BukkitTask refreshTask;

    public ActionBarService(JavaPlugin plugin,
            PlayerSkillDataStore dataStore,
            CastModeService castModeService,
            Supplier<AppConfig> configSupplier,
            TriggerRegistry triggerRegistry,
            Supplier<Map<String, SkillDefinition>> skillDefsSupplier) {
        this.plugin = plugin;
        this.dataStore = dataStore;
        this.castModeService = castModeService;
        this.configSupplier = configSupplier;
        this.triggerRegistry = triggerRegistry;
        this.skillDefsSupplier = skillDefsSupplier;
    }

    public void startRefreshTask() {
        stopRefreshTask();
        AppConfig config = configSupplier.get();
        if (config == null || !config.actionBar().enabled()) {
            return;
        }
        int interval = config.actionBar().refreshIntervalTicks();
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, interval, interval);
    }

    public void stopRefreshTask() {
        if (refreshTask != null) {
            refreshTask.cancel();
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

        // Build slot display segments
        StringBuilder slotDisplay = new StringBuilder();
        for (int i = 0; i < profile.bindings().size(); i++) {
            SkillSlotBinding binding = profile.getBinding(i);
            if (i > 0) {
                slotDisplay.append(" ");
            }
            if (binding == null || binding.isEmpty()) {
                slotDisplay.append("<gray>[空]");
            } else {
                String skillName = resolveSkillName(binding.skillId(), defs);
                String triggerName = triggerRegistry.getDisplayName(
                        binding.triggerId() != null ? binding.triggerId() : "");
                slotDisplay.append(skillName).append(triggerName);
            }

            // Replace individual slot placeholders
            String slotPlaceholder = "{slot_" + (i + 1) + "}";
            if (template.contains(slotPlaceholder)) {
                String slotText;
                if (binding == null || binding.isEmpty()) {
                    slotText = "<gray>[空]";
                } else {
                    String skillName = resolveSkillName(binding.skillId(), defs);
                    String triggerName = triggerRegistry.getDisplayName(
                            binding.triggerId() != null ? binding.triggerId() : "");
                    slotText = skillName + triggerName;
                }
                template = template.replace(slotPlaceholder, slotText);
            }
        }

        // Replace {slot_display} with combined display
        template = template.replace("{slot_display}", slotDisplay.toString());

        // Replace {forced_delay}
        PlayerCastTimingState timing = profile.timingState();
        long remaining = timing.forcedGlobalCastDelayUntil() - System.currentTimeMillis();
        String delayText = remaining > 0
                ? String.format("%.1fs", remaining / 1000.0)
                : "0s";
        template = template.replace("{forced_delay}", delayText);

        return template;
    }

    private String resolveSkillName(String skillId, Map<String, SkillDefinition> defs) {
        if (skillId == null || defs == null) {
            return "???";
        }
        SkillDefinition def = defs.get(skillId);
        return def != null ? def.displayName() : skillId;
    }
}
