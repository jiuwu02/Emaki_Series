package emaki.jiuwu.craft.skills.papi;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.skills.EmakiSkillsPlugin;
import emaki.jiuwu.craft.skills.loader.LocalResourceDefinitionLoader;
import emaki.jiuwu.craft.skills.model.LocalResourceDefinition;
import emaki.jiuwu.craft.skills.model.PlayerCastTimingState;
import emaki.jiuwu.craft.skills.model.PlayerLocalResourceState;
import emaki.jiuwu.craft.skills.model.PlayerSkillLevelState;
import emaki.jiuwu.craft.skills.model.PlayerSkillProfile;
import emaki.jiuwu.craft.skills.model.SkillDefinition;
import emaki.jiuwu.craft.skills.model.SkillSlotBinding;
import emaki.jiuwu.craft.skills.service.PlayerSkillDataStore;
import emaki.jiuwu.craft.skills.service.SkillRegistryService;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;

public final class SkillsPlaceholderExpansion extends PlaceholderExpansion {

    private final EmakiSkillsPlugin plugin;
    private final PlayerSkillDataStore dataStore;
    private final SkillRegistryService registryService;
    private final LocalResourceDefinitionLoader resourceLoader;

    public SkillsPlaceholderExpansion(EmakiSkillsPlugin plugin,
            PlayerSkillDataStore dataStore,
            SkillRegistryService registryService,
            LocalResourceDefinitionLoader resourceLoader) {
        this.plugin = plugin;
        this.dataStore = dataStore;
        this.registryService = registryService;
        this.resourceLoader = resourceLoader;
    }

    @Override
    public String getIdentifier() {
        return "emakiskills";
    }

    @Override
    public String getAuthor() {
        return "Emaki";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (player == null || Texts.isBlank(params)) {
            return "";
        }
        String normalized = params.trim().toLowerCase(Locale.ROOT);
        PlayerSkillProfile profile = dataStore.get(player);
        if (profile == null) {
            return "";
        }

        // %emakiskills_cast_mode%
        if ("cast_mode".equals(normalized)) {
            return String.valueOf(profile.castModeEnabled());
        }

        // %emakiskills_global_cooldown%
        if ("global_cooldown".equals(normalized)) {
            PlayerCastTimingState timing = profile.timingState();
            double remaining = Math.max(0, (timing.globalCooldownUntil() - System.currentTimeMillis())) / 1000.0;
            return Numbers.formatNumber(remaining, "0.#");
        }

        // %emakiskills_unlocked_count%
        if ("unlocked_count".equals(normalized)) {
            return String.valueOf(countUnlocked(profile));
        }

        // %emakiskills_level_<skill_id>%
        if (normalized.startsWith("level_")) {
            String skillId = normalized.substring("level_".length());
            PlayerSkillLevelState levelState = profile.skillLevels().get(skillId);
            return String.valueOf(levelState != null ? levelState.level() : 0);
        }

        // %emakiskills_slot_<n>_...%
        if (normalized.startsWith("slot_")) {
            return slotPlaceholder(profile, normalized.substring("slot_".length()));
        }

        // %emakiskills_cooldown_<skill_id>%
        if (normalized.startsWith("cooldown_")) {
            String skillId = normalized.substring("cooldown_".length());
            PlayerCastTimingState timing = profile.timingState();
            Long until = timing.skillCooldownUntilBySkillId().get(skillId);
            double remaining = until == null ? 0.0 : Math.max(0, (until - System.currentTimeMillis())) / 1000.0;
            return Numbers.formatNumber(remaining, "0.#");
        }

        // %emakiskills_resource_<id>_current/max/percent%
        if (normalized.startsWith("resource_")) {
            return resourcePlaceholder(profile, normalized.substring("resource_".length()));
        }

        return "";
    }

    private String slotPlaceholder(PlayerSkillProfile profile, String remainder) {
        // remainder = "<n>_skill" / "<n>_name" / "<n>_trigger" / "<n>_empty"
        int underscoreIndex = remainder.indexOf('_');
        if (underscoreIndex < 0) {
            return "";
        }
        String slotStr = remainder.substring(0, underscoreIndex);
        String field = remainder.substring(underscoreIndex + 1);

        int slotIndex;
        try {
            slotIndex = Integer.parseInt(slotStr);
        } catch (NumberFormatException ignored) {
            return "";
        }

        List<SkillSlotBinding> bindings = profile.bindings();
        if (slotIndex < 0 || slotIndex >= bindings.size()) {
            return "";
        }
        SkillSlotBinding binding = bindings.get(slotIndex);

        return switch (field) {
            case "skill" -> binding.isEmpty() ? "" : (binding.skillId() != null ? binding.skillId() : "");
            case "name" -> {
                if (binding.isEmpty() || Texts.isBlank(binding.skillId())) {
                    yield "";
                }
                SkillDefinition def = registryService.getDefinition(binding.skillId());
                yield def != null ? def.displayName() : "";
            }
            case "trigger" -> binding.isEmpty() ? "" : (binding.triggerId() != null ? binding.triggerId() : "");
            case "empty" -> String.valueOf(binding.isEmpty());
            default -> "";
        };
    }

    private String resourcePlaceholder(PlayerSkillProfile profile, String remainder) {
        // remainder = "<id>_current" / "<id>_max" / "<id>_percent"
        int lastUnderscore = remainder.lastIndexOf('_');
        if (lastUnderscore < 0) {
            return "";
        }
        String field = remainder.substring(lastUnderscore + 1);
        String resourceId = remainder.substring(0, lastUnderscore);

        if (Texts.isBlank(resourceId)) {
            return "";
        }

        return switch (field) {
            case "current" -> {
                PlayerLocalResourceState state = profile.localResources().get(resourceId);
                yield Numbers.formatNumber(state != null ? state.currentValue() : 0.0, "0.##");
            }
            case "max" -> {
                Map<String, LocalResourceDefinition> defs = resourceLoader.all();
                LocalResourceDefinition def = defs.get(resourceId);
                yield def != null ? Numbers.formatNumber(def.max(), "0.##") : "0";
            }
            case "percent" -> {
                PlayerLocalResourceState state = profile.localResources().get(resourceId);
                Map<String, LocalResourceDefinition> defs = resourceLoader.all();
                LocalResourceDefinition def = defs.get(resourceId);
                if (state == null || def == null || def.max() <= 0.0) {
                    yield "0";
                }
                double percent = (state.currentValue() / def.max()) * 100.0;
                yield Numbers.formatNumber(percent, "0.##");
            }
            default -> "";
        };
    }

    private int countUnlocked(PlayerSkillProfile profile) {
        Set<String> unlocked = new HashSet<>();
        for (SkillSlotBinding binding : profile.bindings()) {
            if (!binding.isEmpty() && !Texts.isBlank(binding.skillId())) {
                unlocked.add(binding.skillId());
            }
        }
        for (Map.Entry<String, PlayerSkillLevelState> entry : profile.skillLevels().entrySet()) {
            unlocked.add(entry.getKey());
        }
        return unlocked.size();
    }
}
