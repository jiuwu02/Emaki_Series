package emaki.jiuwu.craft.gem.integration.strengthen;

import java.util.List;
import java.util.logging.Level;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.readiness.ModuleReadinessPhase;
import emaki.jiuwu.craft.corelib.api.readiness.ReadinessRegistration;
import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;
import emaki.jiuwu.craft.gem.EmakiGemPlugin;
import emaki.jiuwu.craft.strengthen.api.EmakiStrengthenApi;
import emaki.jiuwu.craft.strengthen.api.model.EnhancementAttemptContext;
import emaki.jiuwu.craft.strengthen.api.model.EnhancementAttemptOutcome;
import emaki.jiuwu.craft.corelib.debug.FailureRecorder;
import emaki.jiuwu.craft.corelib.log.LogTags;
import emaki.jiuwu.craft.corelib.log.LogTag;
import java.util.Map;

public final class GemStrengthenIntegration {

    private static final String STRENGTHEN_MODULE = "EmakiStrengthen";

    private final EmakiGemPlugin plugin;
    private ReadinessRegistration registration = ReadinessRegistration.inactive();

    public GemStrengthenIntegration(EmakiGemPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        if (plugin == null) {
            return;
        }
        registration = EmakiCoreLibApi.addModuleListener(plugin, STRENGTHEN_MODULE, phase -> {
            if (phase == ModuleReadinessPhase.READY) {
                registerProvider();
            }
        });
        if (EmakiCoreLibApi.isModuleReady(STRENGTHEN_MODULE)) {
            registerProvider();
        }
    }

    public void close() {
        registration.close();
        registration = ReadinessRegistration.inactive();
        try {
            EmakiStrengthenApi.operations().unregisterEnhancementTarget(GemEnhancementTargetProvider.PROVIDER_ID);
        } catch (RuntimeException | LinkageError swallowed) {
            FailureRecorder.recordIgnoredFailure("EmakiGem", "GemStrengthenIntegration.java#1", swallowed);
        }
    }

    public boolean available() {
        try {
            return EmakiCoreLibApi.isModuleReady(STRENGTHEN_MODULE) && EmakiStrengthenApi.status().usable();
        } catch (RuntimeException | LinkageError _) {
            return false;
        }
    }

    public EmakiResult<EnhancementAttemptOutcome> attemptUpgrade(Player player,
            String recipeId,
            ItemStack target,
            List<ItemStack> materials,
            String operationId) {
        if (!available()) {
            return EmakiResult.unavailable();
        }
        try {
            return EmakiStrengthenApi.operations().attemptEnhancement(player,
                    EnhancementAttemptContext.of(recipeId, target, materials, operationId));
        } catch (RuntimeException | LinkageError exception) {
            if (plugin != null && plugin.getLogger() != null) {
                LogTags.send(plugin, LogTag.INTEGRATION, Level.WARNING, "integration.gem_upgrade_via_strengthen_failed", Map.of(), exception);
            }
            return EmakiResult.internalError("strengthen.enhancement.internal");
        }
    }

    private void registerProvider() {
        try {
            EmakiResult<?> result = EmakiStrengthenApi.operations()
                    .registerEnhancementTarget(new GemEnhancementTargetProvider(plugin));
            if (!result.isSuccess() && plugin.getLogger() != null) {
                LogTags.send(plugin, LogTag.INTEGRATION, Level.WARNING, "integration.register_gem_enhancement_target_provider_unsuccessful", Map.of("result", result));
            }
        } catch (RuntimeException | LinkageError exception) {
            if (plugin.getLogger() != null) {
                LogTags.send(plugin, LogTag.INTEGRATION, Level.WARNING, "integration.register_gem_enhancement_target_provider_failed", Map.of(), exception);
            }
        }
    }
}
