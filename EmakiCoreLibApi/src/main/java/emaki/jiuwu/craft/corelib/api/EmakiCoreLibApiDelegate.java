package emaki.jiuwu.craft.corelib.api;

import emaki.jiuwu.craft.corelib.api.contract.BridgeState;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import emaki.jiuwu.craft.corelib.api.action.CoreActionGate;
import emaki.jiuwu.craft.corelib.api.action.CoreActionSource;
import emaki.jiuwu.craft.corelib.api.action.CoreActionStage;
import emaki.jiuwu.craft.corelib.api.action.CoreActionTrigger;
import emaki.jiuwu.craft.corelib.api.action.CoreStageKind;
import emaki.jiuwu.craft.corelib.api.action.CoreStageRebuildRegistration;
import emaki.jiuwu.craft.corelib.api.action.CoreStageRegistration;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetCondition;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetIdentityProvider;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRegistration;
import emaki.jiuwu.craft.corelib.api.action.CoreTriggerDispatch;
import emaki.jiuwu.craft.corelib.api.action.CoreTriggerRegistration;
import emaki.jiuwu.craft.corelib.api.action.descriptor.CoreActionStageDescriptor;
import emaki.jiuwu.craft.corelib.api.action.descriptor.CoreActionTriggerDescriptor;
import emaki.jiuwu.craft.corelib.api.action.execution.CoreActionExecutionContext;
import emaki.jiuwu.craft.corelib.api.action.execution.CoreActionExecutionResult;
import emaki.jiuwu.craft.corelib.api.animation.AnimationDefinition;
import emaki.jiuwu.craft.corelib.api.animation.AnimationListener;
import emaki.jiuwu.craft.corelib.api.animation.AnimationPlaybackHandle;
import emaki.jiuwu.craft.corelib.api.animation.AnimationRegistration;
import emaki.jiuwu.craft.corelib.api.capability.ApiCapability;
import emaki.jiuwu.craft.corelib.api.capability.CapabilityRegistration;
import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.Unit;
import emaki.jiuwu.craft.corelib.api.dialog.CoreLibDialogs;
import emaki.jiuwu.craft.corelib.api.item.ConfiguredItemDefinition;
import emaki.jiuwu.craft.corelib.api.item.ItemBuildResult;
import emaki.jiuwu.craft.corelib.api.item.ItemComponentCapability;
import emaki.jiuwu.craft.corelib.api.readiness.ModuleReadinessListener;
import emaki.jiuwu.craft.corelib.api.readiness.ModuleReadinessPhase;
import emaki.jiuwu.craft.corelib.api.readiness.ReadinessRegistration;
import emaki.jiuwu.craft.corelib.api.scheduling.EmakiScheduling;

final class EmakiCoreLibApiDelegate {

    private final EmakiCoreLibApi ownerRef;

    EmakiCoreLibApiDelegate(EmakiCoreLibApi ownerRef) {
        this.ownerRef = ownerRef;
    }

    static void install(@NotNull EmakiCoreLibApi.Bridge bridge) {
        EmakiCoreLibApi.bridge.activate(bridge);
    }

    static void uninstall(@Nullable EmakiCoreLibApi.Bridge bridge) {
        EmakiCoreLibApi.bridge.deactivate(bridge);
    }

    static <T> T requireService(@NotNull Class<T> type) {
        EmakiCoreLibApi.Bridge resolved = EmakiCoreLibApi.bridge.get();
        if (resolved != null) {
            return resolved.requireService(type);
        }
        Plugin plugin = Bukkit.getPluginManager().getPlugin(EmakiCoreLibApi.RUNTIME_PLUGIN_NAME);
        if (type.isInstance(plugin)) {
            return type.cast(plugin);
        }
        throw new IllegalStateException("插件 " + EmakiCoreLibApi.RUNTIME_PLUGIN_NAME + " 未加载");
    }

    static @NotNull emaki.jiuwu.craft.corelib.api.contract.ApiStatus status() {
        EmakiCoreLibApi.Bridge resolved = EmakiCoreLibApi.bridge.get();
        return resolved == null
                ? emaki.jiuwu.craft.corelib.api.contract.ApiStatus.notInstalled()
                : resolved.status();
    }

    static @NotNull CoreLibDialogs dialogs() {
        EmakiCoreLibApi.Bridge resolved = EmakiCoreLibApi.bridge.get();
        return resolved == null ? UnavailableDialogs.INSTANCE : resolved.dialogs();
    }

    static @NotNull EmakiScheduling scheduling() {
        EmakiCoreLibApi.Bridge resolved = EmakiCoreLibApi.bridge.get();
        return resolved == null ? UnavailableScheduling.INSTANCE : resolved.scheduling();
    }

    static @NotNull EmakiResult<String> itemDisplayName(@Nullable String itemSource) {
        EmakiCoreLibApi.Bridge resolved = EmakiCoreLibApi.bridge.get();
        return resolved == null ? EmakiResult.unavailable() : resolved.itemDisplayName(itemSource);
    }

    static @NotNull EmakiResult<String> itemDisplayName(@Nullable ItemStack itemStack) {
        EmakiCoreLibApi.Bridge resolved = EmakiCoreLibApi.bridge.get();
        return resolved == null ? EmakiResult.unavailable() : resolved.itemDisplayName(itemStack);
    }

    static @NotNull ItemBuildResult createConfiguredItem(@Nullable ConfiguredItemDefinition definition) {
        return EmakiCoreLibApi.createConfiguredItem(definition, Map.of());
    }

    static @NotNull ItemBuildResult createConfiguredItem(@Nullable ConfiguredItemDefinition definition, @Nullable Map<String, ?> replacements) {
        EmakiCoreLibApi.Bridge resolved = EmakiCoreLibApi.bridge.get();
        return resolved == null
                ? ItemBuildResult.unavailable("EmakiCoreLib 不可用")
                : resolved.createConfiguredItem(definition, replacements == null ? Map.of() : replacements);
    }

    static @NotNull ItemBuildResult applyConfiguredItem(@Nullable ItemStack itemStack, @Nullable ConfiguredItemDefinition definition) {
        return EmakiCoreLibApi.applyConfiguredItem(itemStack, definition, Map.of());
    }

    static @NotNull ItemBuildResult applyConfiguredItem(@Nullable ItemStack itemStack, @Nullable ConfiguredItemDefinition definition, @Nullable Map<String, ?> replacements) {
        EmakiCoreLibApi.Bridge resolved = EmakiCoreLibApi.bridge.get();
        return resolved == null
                ? ItemBuildResult.unavailable("EmakiCoreLib 不可用")
                : resolved.applyConfiguredItem(itemStack, definition, replacements == null ? Map.of() : replacements);
    }

    static @NotNull List<ItemComponentCapability> itemComponentCapabilities() {
        EmakiCoreLibApi.Bridge resolved = EmakiCoreLibApi.bridge.get();
        return resolved == null ? List.of() : List.copyOf(resolved.itemComponentCapabilities());
    }

    static @NotNull Optional<ItemComponentCapability> itemComponentCapability(@Nullable String componentId) {
        if (componentId == null || componentId.isBlank()) {
            return Optional.empty();
        }
        String trimmed = componentId.trim().toLowerCase(Locale.ROOT);
        String normalized = trimmed.contains(":") ? trimmed : "minecraft:" + trimmed;
        return EmakiCoreLibApi.itemComponentCapabilities().stream()
                .filter(capability -> capability.componentId().equals(normalized))
                .findFirst();
    }

    static @NotNull CompletableFuture<CoreActionExecutionResult> executeActionLineAsync( @Nullable Plugin owner, @Nullable String line, @Nullable CoreActionExecutionContext context) {
        EmakiCoreLibApi.Bridge resolved = EmakiCoreLibApi.bridge.get();
        return resolved == null
                ? CompletableFuture.completedFuture(
                        CoreActionExecutionResult.unavailable("action.execution.corelib_unavailable"))
                : resolved.executeActionLineAsync(owner, line, context);
    }

    static @NotNull List<CoreActionStageDescriptor> actionStages() {
        EmakiCoreLibApi.Bridge resolved = EmakiCoreLibApi.bridge.get();
        return resolved == null ? List.of() : List.copyOf(resolved.actionStages());
    }

    static @NotNull Optional<CoreActionStageDescriptor> actionStage(@Nullable String stageId) {
        EmakiCoreLibApi.Bridge resolved = EmakiCoreLibApi.bridge.get();
        return resolved == null ? Optional.empty() : resolved.actionStage(stageId);
    }

    static @NotNull List<CoreActionTriggerDescriptor> actionTriggers() {
        EmakiCoreLibApi.Bridge resolved = EmakiCoreLibApi.bridge.get();
        return resolved == null ? List.of() : List.copyOf(resolved.actionTriggers());
    }

    static @NotNull Optional<CoreActionTriggerDescriptor> actionTrigger(@Nullable String triggerId) {
        EmakiCoreLibApi.Bridge resolved = EmakiCoreLibApi.bridge.get();
        return resolved == null ? Optional.empty() : resolved.actionTrigger(triggerId);
    }

    static @NotNull CoreStageRegistration registerActionStage(@Nullable Plugin owner, @Nullable CoreActionStage stage) {
        EmakiCoreLibApi.Bridge resolved = EmakiCoreLibApi.bridge.get();
        return resolved == null
                ? CoreStageRegistration.unavailable(CoreStageKind.ACTION, "corelib_unavailable")
                : resolved.registerActionStage(owner, stage);
    }

    static @NotNull CoreStageRegistration registerActionSource(@Nullable Plugin owner, @Nullable CoreActionSource source) {
        EmakiCoreLibApi.Bridge resolved = EmakiCoreLibApi.bridge.get();
        return resolved == null
                ? CoreStageRegistration.unavailable(CoreStageKind.SOURCE, "corelib_unavailable")
                : resolved.registerActionSource(owner, source);
    }

    static @NotNull CoreStageRegistration registerActionGate(@Nullable Plugin owner, @Nullable CoreActionGate gate) {
        EmakiCoreLibApi.Bridge resolved = EmakiCoreLibApi.bridge.get();
        return resolved == null
                ? CoreStageRegistration.unavailable(CoreStageKind.GATE, "corelib_unavailable")
                : resolved.registerActionGate(owner, gate);
    }

    static @NotNull CoreTriggerRegistration registerActionTrigger(@Nullable Plugin owner, @Nullable CoreActionTrigger trigger) {
        EmakiCoreLibApi.Bridge resolved = EmakiCoreLibApi.bridge.get();
        return resolved == null
                ? CoreTriggerRegistration.unavailable("corelib_unavailable")
                : resolved.registerActionTrigger(owner, trigger);
    }

    static @NotNull CompletableFuture<EmakiResult<Unit>> dispatchTriggerAsync( @Nullable Plugin owner, @Nullable String triggerId, @Nullable CoreTriggerDispatch dispatch) {
        EmakiCoreLibApi.Bridge resolved = EmakiCoreLibApi.bridge.get();
        return resolved == null
                ? CompletableFuture.completedFuture(EmakiResult.unavailable())
                : resolved.dispatchTriggerAsync(owner, triggerId, dispatch);
    }

    static boolean onStageRegistryRebuilt(@Nullable Plugin owner, @Nullable Runnable reregister) {
        EmakiCoreLibApi.Bridge resolved = EmakiCoreLibApi.bridge.get();
        return resolved != null && resolved.onStageRegistryRebuilt(owner, reregister);
    }

    static @NotNull CoreStageRebuildRegistration addStageRegistryRebuildListener( @Nullable Plugin owner, @Nullable Runnable reregister) {
        EmakiCoreLibApi.Bridge resolved = EmakiCoreLibApi.bridge.get();
        return resolved == null
                ? CoreStageRebuildRegistration.inactive()
                : resolved.addStageRegistryRebuildListener(owner, reregister);
    }

    static @NotNull CoreTargetRegistration registerTargetCondition(@Nullable Plugin owner, @Nullable CoreTargetCondition condition) {
        EmakiCoreLibApi.Bridge resolved = EmakiCoreLibApi.bridge.get();
        return resolved == null
                ? CoreTargetRegistration.unavailable(condition == null ? "" : condition.id(),
                        "action.register.runtime_unsupported")
                : resolved.registerTargetCondition(owner, condition);
    }

    static @NotNull CoreTargetRegistration registerTargetIdentityProvider(@Nullable Plugin owner, @Nullable CoreTargetIdentityProvider provider) {
        EmakiCoreLibApi.Bridge resolved = EmakiCoreLibApi.bridge.get();
        return resolved == null
                ? CoreTargetRegistration.unavailable(provider == null ? "" : provider.systemId(),
                        "action.register.runtime_unsupported")
                : resolved.registerTargetIdentityProvider(owner, provider);
    }

    static @NotNull CapabilityRegistration publishCapabilities(@Nullable Plugin owner, @Nullable Set<ApiCapability> capabilities) {
        EmakiCoreLibApi.Bridge resolved = EmakiCoreLibApi.bridge.get();
        return resolved == null
                ? CapabilityRegistration.unavailable("corelib_unavailable")
                : resolved.publishCapabilities(owner, capabilities);
    }

    static int revokeCapabilities(@Nullable Plugin owner) {
        EmakiCoreLibApi.Bridge resolved = EmakiCoreLibApi.bridge.get();
        return resolved == null ? 0 : resolved.revokeCapabilities(owner);
    }

    static boolean hasCapability(@Nullable ApiCapability capability) {
        EmakiCoreLibApi.Bridge resolved = EmakiCoreLibApi.bridge.get();
        return resolved != null && resolved.hasCapability(capability);
    }

    static @NotNull Set<ApiCapability> capabilities() {
        EmakiCoreLibApi.Bridge resolved = EmakiCoreLibApi.bridge.get();
        return resolved == null ? Set.of() : Set.copyOf(resolved.capabilities());
    }

    static @NotNull Set<ApiCapability> capabilitiesOf(@Nullable String pluginName) {
        EmakiCoreLibApi.Bridge resolved = EmakiCoreLibApi.bridge.get();
        return resolved == null ? Set.of() : Set.copyOf(resolved.capabilitiesOf(pluginName));
    }

    static @NotNull ReadinessRegistration whenReady(@Nullable Plugin owner, @Nullable String moduleName, @Nullable Runnable callback) {
        EmakiCoreLibApi.Bridge resolved = EmakiCoreLibApi.bridge.get();
        return resolved == null
                ? ReadinessRegistration.inactive()
                : resolved.whenReady(owner, moduleName, callback);
    }

    static boolean isModuleReady(@Nullable String moduleName) {
        EmakiCoreLibApi.Bridge resolved = EmakiCoreLibApi.bridge.get();
        return resolved != null && resolved.isModuleReady(moduleName);
    }

    static @NotNull ReadinessRegistration addModuleListener(@Nullable Plugin owner, @Nullable String moduleName, @Nullable ModuleReadinessListener listener) {
        EmakiCoreLibApi.Bridge resolved = EmakiCoreLibApi.bridge.get();
        return resolved == null
                ? ReadinessRegistration.inactive()
                : resolved.addModuleListener(owner, moduleName, listener);
    }

    static @NotNull AnimationRegistration registerAnimation(@Nullable Plugin owner, @Nullable AnimationDefinition definition) {
        EmakiCoreLibApi.Bridge resolved = EmakiCoreLibApi.bridge.get();
        return resolved == null
                ? AnimationRegistration.unavailable("corelib_unavailable")
                : resolved.registerAnimation(owner, definition);
    }

    static @NotNull CompletableFuture<EmakiResult<AnimationPlaybackHandle>> playAnimationAsync( @Nullable Plugin owner, @Nullable Entity entity, @Nullable String definitionId, @Nullable CoreActionExecutionContext context) {
        EmakiCoreLibApi.Bridge resolved = EmakiCoreLibApi.bridge.get();
        return resolved == null
                ? CompletableFuture.completedFuture(EmakiResult.unavailable())
                : resolved.playAnimationAsync(owner, entity, definitionId, context);
    }

    static boolean stopAnimation(@Nullable Plugin owner, @Nullable Entity entity, @Nullable String definitionId) {
        EmakiCoreLibApi.Bridge resolved = EmakiCoreLibApi.bridge.get();
        return resolved != null && resolved.stopAnimation(owner, entity, definitionId);
    }

    static @NotNull AnimationRegistration addAnimationListener(@Nullable Plugin owner, @Nullable AnimationListener listener) {
        EmakiCoreLibApi.Bridge resolved = EmakiCoreLibApi.bridge.get();
        return resolved == null
                ? AnimationRegistration.unavailable("corelib_unavailable")
                : resolved.addAnimationListener(owner, listener);
    }

}
