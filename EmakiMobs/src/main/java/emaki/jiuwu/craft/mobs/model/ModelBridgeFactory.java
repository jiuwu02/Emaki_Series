package emaki.jiuwu.craft.mobs.model;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.util.Locale;
import java.util.logging.Logger;
import emaki.jiuwu.craft.corelib.log.LogTags;
import emaki.jiuwu.craft.corelib.log.LogTag;
import java.util.Map;
import java.util.logging.Level;

public final class ModelBridgeFactory {

    private static final String MODEL_ENGINE_PLUGIN = "ModelEngine";
    private static final String BETTER_MODEL_PLUGIN = "BetterModel";
    private static final String MODEL_ENGINE_BRIDGE = "emaki.jiuwu.craft.mobs.model.ModelEngineBridge";
    private static final String BETTER_MODEL_BRIDGE = "emaki.jiuwu.craft.mobs.model.BetterModelBridge";

    private final Logger logger;

    public ModelBridgeFactory(@NotNull Plugin plugin) {
        this.logger = plugin.getLogger();
    }

    public @NotNull MobModelBridge create(@Nullable String configuredApi) {
        String preference = configuredApi == null ? "auto" : configuredApi.trim().toLowerCase(Locale.ROOT);
        return switch (preference) {
            case "model_engine", "modelengine" -> createNamed(MODEL_ENGINE_PLUGIN, MODEL_ENGINE_BRIDGE);
            case "better_model", "bettermodel" -> createNamed(BETTER_MODEL_PLUGIN, BETTER_MODEL_BRIDGE);
            default -> createAuto();
        };
    }

    private MobModelBridge createAuto() {
        if (Bukkit.getPluginManager().isPluginEnabled(MODEL_ENGINE_PLUGIN)) {
            MobModelBridge bridge = createNamed(MODEL_ENGINE_PLUGIN, MODEL_ENGINE_BRIDGE);
            if (bridge.available()) {
                return bridge;
            }
        }
        if (Bukkit.getPluginManager().isPluginEnabled(BETTER_MODEL_PLUGIN)) {
            MobModelBridge bridge = createNamed(BETTER_MODEL_PLUGIN, BETTER_MODEL_BRIDGE);
            if (bridge.available()) {
                return bridge;
            }
        }
        LogTags.send(logger, LogTag.INTEGRATION, Level.INFO, "integration.model_backend_not_found");
        return NoopModelBridge.INSTANCE;
    }

    private MobModelBridge createNamed(String pluginName, String bridgeClassName) {
        if (!Bukkit.getPluginManager().isPluginEnabled(pluginName)) {
            LogTags.send(logger, LogTag.INTEGRATION, Level.WARNING, "integration.model_backend_not_installed", Map.of("name", pluginName));
            return NoopModelBridge.INSTANCE;
        }
        try {
            Class<?> bridgeType = Class.forName(bridgeClassName, true, ModelBridgeFactory.class.getClassLoader());
            Constructor<?> constructor = bridgeType.getConstructor();
            Object value = constructor.newInstance();
            if (value instanceof MobModelBridge bridge) {
                return bridge;
            }
            LogTags.send(logger, LogTag.INTEGRATION, Level.WARNING, "integration.model_backend_bridge_invalid", Map.of("name", pluginName));
        } catch (ClassNotFoundException | LinkageError exception) {
            LogTags.send(logger, LogTag.INTEGRATION, Level.INFO, "integration.model_backend_class_unavailable", Map.of("name", pluginName));
        } catch (ReflectiveOperationException | SecurityException exception) {
            LogTags.send(logger, LogTag.INTEGRATION, Level.WARNING, "integration.model_backend_init_failed", Map.of("name", pluginName, "error", exception.getMessage()));
        }
        return NoopModelBridge.INSTANCE;
    }
}
