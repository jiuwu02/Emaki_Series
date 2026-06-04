package emaki.jiuwu.craft.level.service;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.level.config.AppConfig;
import emaki.jiuwu.craft.level.config.LevelTypeConfig;
import emaki.jiuwu.craft.level.model.PlayerLevelData;
import emaki.jiuwu.craft.level.model.PlayerLevelEntry;

public final class LevelAttributeBridge {

    private final JavaPlugin plugin;
    private final LevelTypeRegistry typeRegistry;
    private final PlayerLevelDataStore dataStore;
    private AppConfig config;
    private Object facade;
    private Object providerProxy;
    private Method unregisterMethod;
    private Constructor<?> contributionConstructor;

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
        Plugin attributePlugin = Bukkit.getPluginManager().getPlugin("EmakiAttribute");
        if (attributePlugin == null) {
            return false;
        }
        try {
            ClassLoader attributeClassLoader = attributePlugin.getClass().getClassLoader();
            Class<?> facadeType = Class.forName("emaki.jiuwu.craft.attribute.service.AttributeServiceFacade", true, attributeClassLoader);
            Class<?> providerType = Class.forName("emaki.jiuwu.craft.attribute.api.AttributeContributionProvider", true, attributeClassLoader);
            Class<?> contributionType = Class.forName("emaki.jiuwu.craft.attribute.api.AttributeContribution", true, attributeClassLoader);
            RegisteredServiceProvider<?> provider = Bukkit.getServicesManager().getRegistration(facadeType);
            if (provider == null || provider.getProvider() == null) {
                return false;
            }
            contributionConstructor = contributionType.getConstructor(String.class, double.class, String.class);
            facade = provider.getProvider();
            providerProxy = Proxy.newProxyInstance(attributeClassLoader, new Class<?>[]{providerType}, (proxy, method, args) -> {
                String methodName = method.getName();
                if (method.getDeclaringClass() == Object.class) {
                    return switch (methodName) {
                        case "toString" -> "EmakiLevelAttributeProvider{" + id() + "}";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == (args == null || args.length == 0 ? null : args[0]);
                        default -> method.invoke(this, args);
                    };
                }
                return switch (methodName) {
                    case "id" -> id();
                    case "priority" -> priority();
                    case "collect" -> collect(args == null || args.length == 0 ? null : args[0]);
                    default -> throw new UnsupportedOperationException("Unsupported AttributeContributionProvider method: " + methodName);
                };
            });
            Method registerMethod = facadeType.getMethod("registerContributionProvider", providerType);
            unregisterMethod = facadeType.getMethod("unregisterContributionProvider", String.class);
            registerMethod.invoke(facade, providerProxy);
            return true;
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().fine("EmakiAttribute bridge skipped: " + exception.getMessage());
            facade = null;
            providerProxy = null;
            unregisterMethod = null;
            contributionConstructor = null;
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
            providerProxy = null;
            unregisterMethod = null;
            contributionConstructor = null;
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

    public String id() {
        return config == null ? "emakilevel" : config.attributeProviderId();
    }

    public int priority() {
        return 100;
    }

    private Collection<?> collect(Object entity) {
        if (!(entity instanceof Player player) || config == null || !config.attributeEnabled() || contributionConstructor == null) {
            return List.of();
        }
        PlayerLevelData data = dataStore.cached(player.getUniqueId());
        if (data == null) {
            data = dataStore.getOrLoad(player.getUniqueId(), typeRegistry.asMap());
        }
        List<Object> contributions = new ArrayList<>();
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
                    addContribution(contributions, attribute.getKey(), value, id() + ":" + type.id());
                }
            }
        }
        return contributions;
    }

    private void addContribution(Collection<Object> contributions, String attributeId, double value, String sourceId) {
        try {
            contributions.add(contributionConstructor.newInstance(attributeId, value, sourceId));
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().fine("EmakiAttribute contribution skipped: " + exception.getMessage());
        }
    }
}
