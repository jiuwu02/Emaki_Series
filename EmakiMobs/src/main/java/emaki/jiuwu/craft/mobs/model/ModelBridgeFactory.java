package emaki.jiuwu.craft.mobs.model;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.util.Locale;
import java.util.logging.Logger;

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
        logger.info("[integration] 未找到可用的模型后端，EmakiMobs 模型功能已禁用");
        return NoopModelBridge.INSTANCE;
    }

    private MobModelBridge createNamed(String pluginName, String bridgeClassName) {
        if (!Bukkit.getPluginManager().isPluginEnabled(pluginName)) {
            logger.warning("[integration] 模型后端 '" + pluginName + "' 未安装，模型功能已禁用");
            return NoopModelBridge.INSTANCE;
        }
        try {
            Class<?> bridgeType = Class.forName(bridgeClassName, true, ModelBridgeFactory.class.getClassLoader());
            Constructor<?> constructor = bridgeType.getConstructor();
            Object value = constructor.newInstance();
            if (value instanceof MobModelBridge bridge) {
                return bridge;
            }
            logger.warning("[integration] 模型后端 " + pluginName + " 的桥接实现无效");
        } catch (ClassNotFoundException | LinkageError exception) {
            logger.info("[integration] 模型后端 " + pluginName + " 的类不可用，模型功能已禁用");
        } catch (ReflectiveOperationException | SecurityException exception) {
            logger.warning("[integration] 模型后端 " + pluginName + " 初始化失败: " + exception.getMessage());
        }
        return NoopModelBridge.INSTANCE;
    }
}
