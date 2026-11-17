package emaki.jiuwu.craft.mobs.provider;

import emaki.jiuwu.craft.mobs.loader.MobSpec;
import emaki.jiuwu.craft.mobs.service.MobIdentifier;
import emaki.jiuwu.craft.corelib.log.LogTags;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Logger;
import emaki.jiuwu.craft.corelib.log.LogTag;
import java.util.logging.Level;

public final class MobAttributeRegistrar {

    private static final String ATTRIBUTE_PLUGIN_NAME = "EmakiAttribute";
    private static final String INTEGRATION_CLASS_NAME =
            "emaki.jiuwu.craft.mobs.provider.MobAttributeIntegration";

    private final Plugin plugin;
    private final MobIdentifier mobIdentifier;
    private final Supplier<Map<String, MobSpec>> registry;
    private final Logger logger;

    private MobOptionalProviderIntegration integration;

    public MobAttributeRegistrar(Plugin plugin,
                                 MobIdentifier mobIdentifier,
                                 Supplier<Map<String, MobSpec>> registry) {
        this.plugin = plugin;
        this.mobIdentifier = mobIdentifier;
        this.registry = registry;
        this.logger = plugin.getLogger();
    }

    public boolean registered() {
        return integration != null && integration.registered();
    }

    public void register() {
        if (!Bukkit.getPluginManager().isPluginEnabled(ATTRIBUTE_PLUGIN_NAME)) {
            return;
        }
        if (integration == null) {
            integration = createIntegration();
        }
        if (integration != null) {
            integration.register();
        }
    }

    public void unregister() {
        if (integration != null) {
            integration.close();
            integration = null;
        }
    }

    private MobOptionalProviderIntegration createIntegration() {
        try {
            Class<?> integrationType = Class.forName(
                    INTEGRATION_CLASS_NAME, true, MobAttributeRegistrar.class.getClassLoader());
            Constructor<?> constructor = integrationType.getConstructor(
                    Plugin.class, MobIdentifier.class, Supplier.class);
            Object value = constructor.newInstance(plugin, mobIdentifier, registry);
            if (value instanceof MobOptionalProviderIntegration created) {
                return created;
            }
            LogTags.send(logger, LogTag.INTEGRATION, Level.WARNING, "integration.emaki_attribute_integration_type_invalid");
        } catch (ClassNotFoundException | LinkageError exception) {
            LogTags.send(logger, LogTag.INTEGRATION, Level.INFO, "integration.emaki_attribute_integration_unavailable");
        } catch (ReflectiveOperationException | SecurityException exception) {
            LogTags.send(logger, LogTag.INTEGRATION, Level.WARNING, "integration.emaki_attribute_integration_failed", Map.of("error", exception.getMessage()));
        }
        return null;
    }
}
