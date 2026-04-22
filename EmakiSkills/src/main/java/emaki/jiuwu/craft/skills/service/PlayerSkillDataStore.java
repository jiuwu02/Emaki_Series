package emaki.jiuwu.craft.skills.service;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.skills.model.PlayerCastTimingState;
import emaki.jiuwu.craft.skills.model.PlayerLocalResourceState;
import emaki.jiuwu.craft.skills.model.PlayerSkillProfile;
import emaki.jiuwu.craft.skills.model.SkillSlotBinding;

public final class PlayerSkillDataStore {

    private final JavaPlugin plugin;
    private final int defaultSlotCount;
    private final Map<UUID, PlayerSkillProfile> cache = new ConcurrentHashMap<>();

    public PlayerSkillDataStore(JavaPlugin plugin, int defaultSlotCount) {
        this.plugin = plugin;
        this.defaultSlotCount = Math.max(1, defaultSlotCount);
    }

    public PlayerSkillProfile load(Player player) {
        if (player == null) {
            return null;
        }
        UUID uuid = player.getUniqueId();
        File file = profileFile(uuid);
        if (!file.exists()) {
            PlayerSkillProfile profile = createDefault(uuid);
            cache.put(uuid, profile);
            return profile;
        }

        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            PlayerSkillProfile profile = readProfile(uuid, yaml);
            cache.put(uuid, profile);
            return profile;
        } catch (Exception exception) {
            plugin.getLogger().log(Level.WARNING,
                    "[SkillDataStore] Failed to load profile for " + uuid, exception);
            PlayerSkillProfile fallback = createDefault(uuid);
            cache.put(uuid, fallback);
            return fallback;
        }
    }

    public PlayerSkillProfile get(Player player) {
        if (player == null) {
            return null;
        }
        PlayerSkillProfile cached = cache.get(player.getUniqueId());
        return cached != null ? cached : load(player);
    }

    public void save(Player player) {
        if (player == null) {
            return;
        }
        PlayerSkillProfile profile = cache.get(player.getUniqueId());
        if (profile == null || !profile.isDirty()) {
            return;
        }
        saveProfile(player.getUniqueId(), profile);
    }

    public void saveAll() {
        for (Map.Entry<UUID, PlayerSkillProfile> entry : cache.entrySet()) {
            PlayerSkillProfile profile = entry.getValue();
            if (profile.isDirty()) {
                saveProfile(entry.getKey(), profile);
            }
        }
    }

    public void unload(UUID uuid) {
        if (uuid == null) {
            return;
        }
        PlayerSkillProfile profile = cache.remove(uuid);
        if (profile != null && profile.isDirty()) {
            saveProfile(uuid, profile);
        }
    }

    public void unloadAll() {
        saveAll();
        cache.clear();
    }

    public PlayerSkillProfile createDefault(UUID uuid) {
        PlayerSkillProfile profile = new PlayerSkillProfile(
                uuid == null ? "" : uuid.toString(), defaultSlotCount);
        profile.setCastModeEnabled(false);
        return profile;
    }

    // ------------------------------------------------------------------
    // File I/O
    // ------------------------------------------------------------------

    private File profileFile(UUID uuid) {
        File dataDir = new File(plugin.getDataFolder(), "data");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        return new File(dataDir, uuid.toString() + ".yml");
    }

    private PlayerSkillProfile readProfile(UUID uuid, YamlConfiguration yaml) {
        int slotCount = defaultSlotCount;
        PlayerSkillProfile profile = new PlayerSkillProfile(uuid.toString(), slotCount);

        // cast_mode_enabled
        profile.setCastModeEnabled(yaml.getBoolean("cast_mode_enabled", false));

        // bindings
        List<?> bindingsList = yaml.getList("bindings");
        if (bindingsList != null) {
            for (Object obj : bindingsList) {
                if (!(obj instanceof Map<?, ?> map)) {
                    continue;
                }
                int slot = toInt(map.get("slot"), -1);
                String skillId = toStringOrNull(map.get("skill_id"));
                String triggerId = toStringOrNull(map.get("trigger_id"));
                if (slot >= 0 && slot < slotCount) {
                    profile.setBinding(slot, new SkillSlotBinding(slot, skillId, triggerId));
                }
            }
        }

        // local_resources
        ConfigurationSection resourcesSection = yaml.getConfigurationSection("local_resources");
        if (resourcesSection != null) {
            for (String key : resourcesSection.getKeys(false)) {
                ConfigurationSection resSection = resourcesSection.getConfigurationSection(key);
                if (resSection == null) {
                    continue;
                }
                double currentValue = resSection.getDouble("current_value", 0D);
                long lastRegenAt = resSection.getLong("last_regen_at", 0L);
                profile.localResources().put(key,
                        new PlayerLocalResourceState(key, currentValue, lastRegenAt));
            }
        }

        // timing
        ConfigurationSection timingSection = yaml.getConfigurationSection("timing");
        if (timingSection != null) {
            PlayerCastTimingState timing = profile.timingState();
            timing.setForcedGlobalCastDelayUntil(
                    timingSection.getLong("forced_global_cast_delay_until", 0L));
            timing.setGlobalCooldownUntil(
                    timingSection.getLong("global_cooldown_until", 0L));
            ConfigurationSection cdSection = timingSection.getConfigurationSection("skill_cooldowns");
            if (cdSection != null) {
                for (String skillId : cdSection.getKeys(false)) {
                    timing.skillCooldownUntilBySkillId().put(skillId, cdSection.getLong(skillId, 0L));
                }
            }
        }

        profile.clearDirty();
        return profile;
    }

    private void saveProfile(UUID uuid, PlayerSkillProfile profile) {
        File file = profileFile(uuid);
        YamlConfiguration yaml = new YamlConfiguration();

        yaml.set("uuid", uuid.toString());
        yaml.set("cast_mode_enabled", profile.castModeEnabled());

        // bindings
        List<Map<String, Object>> bindingsList = new java.util.ArrayList<>();
        for (SkillSlotBinding binding : profile.bindings()) {
            if (binding.isEmpty()) {
                continue;
            }
            Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("slot", binding.slotIndex());
            map.put("skill_id", binding.skillId());
            map.put("trigger_id", binding.triggerId());
            bindingsList.add(map);
        }
        yaml.set("bindings", bindingsList);

        // local_resources
        for (Map.Entry<String, PlayerLocalResourceState> entry : profile.localResources().entrySet()) {
            String path = "local_resources." + entry.getKey();
            PlayerLocalResourceState state = entry.getValue();
            yaml.set(path + ".current_value", state.currentValue());
            yaml.set(path + ".last_regen_at", state.lastRegenAt());
        }

        // timing
        PlayerCastTimingState timing = profile.timingState();
        yaml.set("timing.forced_global_cast_delay_until", timing.forcedGlobalCastDelayUntil());
        yaml.set("timing.global_cooldown_until", timing.globalCooldownUntil());
        for (Map.Entry<String, Long> entry : timing.skillCooldownUntilBySkillId().entrySet()) {
            yaml.set("timing.skill_cooldowns." + entry.getKey(), entry.getValue());
        }

        try {
            yaml.save(file);
            profile.clearDirty();
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING,
                    "[SkillDataStore] Failed to save profile for " + uuid, exception);
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static int toInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return fallback;
    }

    private static String toStringOrNull(Object value) {
        if (value == null) {
            return null;
        }
        String str = value.toString();
        return str.isBlank() ? null : str;
    }
}
