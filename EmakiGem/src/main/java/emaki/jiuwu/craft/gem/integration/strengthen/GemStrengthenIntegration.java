package emaki.jiuwu.craft.gem.integration.strengthen;

import java.util.logging.Level;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.readiness.ModuleReadinessPhase;
import emaki.jiuwu.craft.corelib.api.readiness.ReadinessRegistration;
import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;
import emaki.jiuwu.craft.gem.EmakiGemPlugin;
import emaki.jiuwu.craft.strengthen.api.EmakiStrengthenApi;

/**
 * 负责把 {@link GemEnhancementTargetProvider} 注册进 EmakiStrengthen 的强化目标注册中心。
 *
 * <p>用常驻就绪监听而非一次性 {@code whenReady}：Strengthen 的注册中心在其自身 shutdown/重载时会
 * 被清空，只注册一次会导致 Strengthen 重载后宝石目标静默消失。监听每次 READY 转换都重新注册，
 * 注册中心按 Provider ID 覆盖写入，因此重复注册是幂等的。
 *
 * <p>按 {@code ModuleReadinessListener} 的约定，回调内不再注册 {@code whenReady}，也不假设回调
 * 运行在任何特定线程——注册中心自身是同步的，可从任意线程调用。
 */
public final class GemStrengthenIntegration {

    /**
     * 目标模块名以字面量书写，而非引用 EmakiStrengthen API 的常量：引用常量会把该类拖入类加载
     * 期，Strengthen 缺失时直接 NoClassDefFoundError。
     */
    private static final String STRENGTHEN_MODULE = "EmakiStrengthen";

    private final EmakiGemPlugin plugin;
    private ReadinessRegistration registration = ReadinessRegistration.inactive();

    public GemStrengthenIntegration(EmakiGemPlugin plugin) {
        this.plugin = plugin;
    }

    /** 挂载就绪监听；若 Strengthen 当前已就绪，立即补一次注册。 */
    public void initialize() {
        if (plugin == null) {
            return;
        }
        registration = EmakiCoreLibApi.addModuleListener(plugin, STRENGTHEN_MODULE, phase -> {
            if (phase == ModuleReadinessPhase.READY) {
                registerProvider();
            }
        });
        // 已就绪时注册监听刻意不会立即回调（见 ModuleReadinessListener 约定），所以这里主动补一次。
        if (EmakiCoreLibApi.isModuleReady(STRENGTHEN_MODULE)) {
            registerProvider();
        }
    }

    /** 撤销就绪监听，并在 Strengthen 仍在线时移除本插件注册的 Provider。 */
    public void close() {
        registration.close();
        registration = ReadinessRegistration.inactive();
        try {
            EmakiStrengthenApi.operations().unregisterEnhancementTarget(GemEnhancementTargetProvider.PROVIDER_ID);
        } catch (RuntimeException | LinkageError _) {
            // Strengthen 可能已先于 Gem 卸载；此处无需处理。
        }
    }

    private void registerProvider() {
        try {
            EmakiResult<?> result = EmakiStrengthenApi.operations()
                    .registerEnhancementTarget(new GemEnhancementTargetProvider(plugin));
            if (!result.isSuccess() && plugin.getLogger() != null) {
                plugin.getLogger().warning("注册 gem 强化目标 Provider 未成功: " + result);
            }
        } catch (RuntimeException | LinkageError exception) {
            if (plugin.getLogger() != null) {
                plugin.getLogger().log(Level.WARNING,
                        "注册 gem 强化目标 Provider 失败；宝石升级将无法通过强化框架执行。", exception);
            }
        }
    }
}
