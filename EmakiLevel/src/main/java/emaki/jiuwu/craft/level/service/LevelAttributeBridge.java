package emaki.jiuwu.craft.level.service;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.attribute.api.AttributeContribution;
import emaki.jiuwu.craft.attribute.api.AttributeContributionProvider;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.level.config.AppConfig;
import emaki.jiuwu.craft.level.config.LevelTypeConfig;
import emaki.jiuwu.craft.level.model.PlayerLevelData;
import emaki.jiuwu.craft.level.model.PlayerLevelEntry;

public final class LevelAttributeBridge implements AttributeContributionProvider {

    private final JavaPlugin plugin;
    private final LevelTypeRegistry typeRegistry;
    private final PlayerLevelDataStore dataStore;
    private AppConfig config;
    private Object facade;
    private Method unregisterMethod;

    public LevelAttributeBridge(JavaPlugin plugin, LevelTypeRegistry typeRegistry, PlayerLevelDataStore dataStore, AppConfig config) {
        this.plugin = plugin;
        this.typeRegistry = typeRegistry;
        this.dataStore = dataStore;
        this.config = config;
    }

    public boolean register() {
        if (config == null || !config.attributeEnabled() || !Bukkit.getPluginManager().isPluginEnabled("EmakiAttribute")) {
            return false;
        }
        try {
            Class<?> facadeType = Class.forName("emaki.jiuwu.craft.attribute.service.AttributeServiceFacade");
            RegisteredServiceProvider<?> provider = Bukkit.getServicesManager().getRegistration(facadeType);
            if (provider == null || provider.getProvider() == null) {
                return false;
            }
            facade = provider.getProvider();
            Method registerMethod = facadeType.getMethod("registerContributionProvider", AttributeContributionProvider.class);
            unregisterMethod = facadeType.getMethod("unregisterContributionProvider", String.class);
            registerMethod.invoke(facade, this);
            return true;
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().fine("EmakiAttribute bridge skipped: " + exception.getMessage());
            facade = null;
            unregisterMethod = null;
            return false;
        }
    }

    public void unregister() {
        if (facade == null || unregisterMethod == null) {
            return;
        }
        try {
            unregisterMethod.invoke(facade, id());
        } catch (ReflectiveOperationException ignored) {
        } finally {
            facade = null;
            unregisterMethod = null;
        }
    }

    public void config(AppConfig config) {
        this.config = config;
    }

    public void resync(Player player) {
        if (facade == null || player == null) {
            return;
        }
        try {
            Method method = facade.getClass().getMethod("resyncPlayer", Player.class);
            method.invoke(facade, player);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    public void resyncAll() {
        if (facade == null) {
            return;
        }
        try {
            Method method = facade.getClass().getMethod("resyncAllPlayers");
            method.invoke(facade);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    @Override
    public String id() {
        return config == null ? "emakilevel" : config.attributeProviderId();
    }

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public Collection<AttributeContribution> collect(LivingEntity entity) {
        if (!(entity instanceof Player player) || config == null || !config.attributeEnabled()) {
            return List.of();
        }
        PlayerLevelData data = dataStore.cached(player.getUniqueId());
        if (data == null) {
            data = dataStore.getOrLoad(player.getUniqueId(), typeRegistry.asMap());
        }
        java.util.List<AttributeContribution> contributions = new ArrayList<>();
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
            for (Map.Entry<String, String> attribute : type.attributes().entrySet()) {
                double value = ExpressionEngine.evaluate(attribute.getValue(), variables);
                if (Math.abs(value) > 1.0E-9D) {
                    contributions.add(new AttributeContribution(attribute.getKey(), value, id() + ":" + type.id()));
                }
            }
        }
        return contributions;
    }
}
