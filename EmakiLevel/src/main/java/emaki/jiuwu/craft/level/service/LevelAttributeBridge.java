package emaki.jiuwu.craft.level.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.attribute.api.extension.AttributeContribution;
import emaki.jiuwu.craft.attribute.api.extension.AttributeContributionProvider;
import emaki.jiuwu.craft.attribute.api.extension.ContributionProviderRegistration;
import emaki.jiuwu.craft.attribute.api.EmakiAttributeApi;
import emaki.jiuwu.craft.corelib.api.scheduling.EmakiScheduling;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.level.config.AppConfig;
import emaki.jiuwu.craft.level.config.LevelTypeConfig;
import emaki.jiuwu.craft.level.model.PlayerLevelData;
import emaki.jiuwu.craft.level.model.PlayerLevelEntry;

/** Isolated optional EmakiAttribute integration, loaded only after the plugin-name availability check. */
public final class LevelAttributeBridge implements AttributeContributionProvider, AutoCloseable {

    private final JavaPlugin plugin;
    private final LevelTypeRegistry typeRegistry;
    private final PlayerLevelDataStore dataStore;
    private final EmakiScheduling scheduling;
    private AppConfig config;
    private ContributionProviderRegistration registration = ContributionProviderRegistration.noop();
    private boolean registered;

    public LevelAttributeBridge(JavaPlugin plugin,
            LevelTypeRegistry typeRegistry,
            PlayerLevelDataStore dataStore,
            EmakiScheduling scheduling,
            AppConfig config) {
        this.plugin = plugin;
        this.typeRegistry = typeRegistry;
        this.dataStore = dataStore;
        this.scheduling = scheduling;
        this.config = config;
    }

    public boolean register() {
        closeRegistration();
        if (config == null
                || !config.attributeEnabled()
                || !EmakiAttributeApi.status().usable()
                || id().isBlank()) {
            return false;
        }
        registration = EmakiAttributeApi.extensions().registerContributionProvider(plugin, this);
        registered = true;
        return true;
    }

    public void config(AppConfig config) {
        this.config = config;
    }

    /** Resynchronizes one player on that player's owner thread. */
    public void resync(Player player) {
        if (!registered || player == null || !player.isOnline() || !EmakiAttributeApi.status().usable()) {
            return;
        }
        Runnable task = () -> EmakiAttributeApi.operations().resyncPlayer(player);
        if (scheduling.ownsEntity(player)) {
            task.run();
            return;
        }
        scheduling.runForEntity(plugin, player, task, null);
    }

    /** Dispatches every online player's resynchronization to its own owner thread. */
    public void resyncAll() {
        if (!registered) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            resync(player);
        }
    }

    @Override
    public void close() {
        closeRegistration();
    }

    @Override
    public @NotNull String id() {
        return config == null ? "emakilevel" : config.attributeProviderId();
    }

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public @NotNull Collection<AttributeContribution> collect(@NotNull LivingEntity entity) {
        if (!(entity instanceof Player player) || config == null || !config.attributeEnabled()) {
            return List.of();
        }
        PlayerLevelData data = dataStore.cached(player.getUniqueId());
        if (data == null) {
            return List.of();
        }
        List<AttributeContribution> contributions = new ArrayList<>();
        for (LevelTypeConfig type : typeRegistry.all()) {
            if (!type.enabled() || !type.attributesEnabled() || type.attributes().isEmpty()) {
                continue;
            }
            PlayerLevelEntry entry = data.entry(type.id());
            if (entry == null) {
                continue;
            }
            Map<String, Object> variables = new LinkedHashMap<>();
            variables.put("level", entry.level());
            variables.put("exp", entry.exp());
            variables.put("total_exp", entry.totalExp());
            String sourceId = id() + ":" + type.id();
            for (Map.Entry<String, String> attribute : type.attributes().entrySet()) {
                double value = ExpressionEngine.evaluate(attribute.getValue(), variables);
                if (Math.abs(value) > 1.0E-9D) {
                    contributions.add(new AttributeContribution(attribute.getKey(), value, sourceId));
                }
            }
        }
        return contributions;
    }

    private void closeRegistration() {
        registration.close();
        registration = ContributionProviderRegistration.noop();
        registered = false;
    }
}
