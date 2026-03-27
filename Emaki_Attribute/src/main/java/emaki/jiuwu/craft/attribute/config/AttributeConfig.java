package emaki.jiuwu.craft.attribute.config;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import java.util.LinkedHashSet;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public record AttributeConfig(boolean debug,
                              boolean hardLockDamage,
                              String defaultDamageType,
                              int regenIntervalTicks,
                              int syncDelayTicks,
                              Set<String> allowedDamageCauses) {

    public static AttributeConfig defaults() {
        return new AttributeConfig(false, true, "physical", 20, 1, Set.of());
    }

    public static AttributeConfig fromConfig(YamlConfiguration configuration) {
        if (configuration == null) {
            return defaults();
        }
        boolean debug = configuration.getBoolean("debug", false);
        boolean hardLockDamage = configuration.getBoolean("hard_lock_damage", true);
        String defaultDamageType = ConfigNodes.string(configuration, "default_damage_type", "physical");
        int regenIntervalTicks = Math.max(1, configuration.getInt("regen_interval_ticks", 20));
        int syncDelayTicks = Math.max(0, configuration.getInt("sync_delay_ticks", 1));
        Set<String> causes = new LinkedHashSet<>();
        causes.addAll(configuration.getStringList("allowed_damage_causes"));
        ConfigurationSection allowedSection = configuration.getConfigurationSection("allowed_damage_causes");
        if (allowedSection != null) {
            causes.addAll(allowedSection.getKeys(false));
        }
        if (causes.isEmpty()) {
            causes.addAll(defaults().allowedDamageCauses());
        }
        Set<String> normalizedCauses = new LinkedHashSet<>();
        for (String cause : causes) {
            if (cause == null || cause.isBlank()) {
                continue;
            }
            normalizedCauses.add(cause.trim().toLowerCase().replace(' ', '_'));
        }
        return new AttributeConfig(debug, hardLockDamage, defaultDamageType, regenIntervalTicks, syncDelayTicks, Set.copyOf(normalizedCauses));
    }

    public boolean allowsDamageCause(String cause) {
        if (cause == null || cause.isBlank()) {
            return false;
        }
        return allowedDamageCauses.contains(cause.trim().toLowerCase().replace(' ', '_'));
    }
}
