package emaki.jiuwu.craft.corelib.apiimpl;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.action.pipeline.registry.RegisteredTrigger;
import emaki.jiuwu.craft.corelib.action.pipeline.registry.StageRegistry;
import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;
import emaki.jiuwu.craft.corelib.api.action.CoreActionFailureKind;
import emaki.jiuwu.craft.corelib.api.action.CoreActionGate;
import emaki.jiuwu.craft.corelib.api.action.CoreActionKeys;
import emaki.jiuwu.craft.corelib.api.action.CoreActionSource;
import emaki.jiuwu.craft.corelib.api.action.CoreActionStage;
import emaki.jiuwu.craft.corelib.api.action.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.CoreActionTrigger;
import emaki.jiuwu.craft.corelib.api.action.CoreStageKind;
import emaki.jiuwu.craft.corelib.api.action.CoreStageRebuildRegistration;
import emaki.jiuwu.craft.corelib.api.action.CoreStageRegistration;
import emaki.jiuwu.craft.corelib.api.action.CoreTriggerDispatch;
import emaki.jiuwu.craft.corelib.api.action.CoreTriggerRegistration;
import emaki.jiuwu.craft.corelib.api.action.descriptor.CoreActionStageDescriptor;
import emaki.jiuwu.craft.corelib.api.action.descriptor.CoreActionTriggerDescriptor;
import emaki.jiuwu.craft.corelib.api.action.execution.CoreActionExecutionContext;
import emaki.jiuwu.craft.corelib.api.action.execution.CoreActionExecutionResult;
import emaki.jiuwu.craft.corelib.api.action.execution.CoreActionExecutionStatus;
import emaki.jiuwu.craft.corelib.api.action.pipeline.compile.PhaseContract;
import emaki.jiuwu.craft.corelib.api.action.pipeline.compile.TriggerContract;
import emaki.jiuwu.craft.corelib.api.capability.ApiCapability;
import emaki.jiuwu.craft.corelib.api.capability.CapabilityRegistration;
import emaki.jiuwu.craft.corelib.api.contract.ApiStatus;
import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.FailureKind;
import emaki.jiuwu.craft.corelib.api.contract.Unit;
import emaki.jiuwu.craft.corelib.api.dialog.CoreLibDialogs;
import emaki.jiuwu.craft.corelib.api.item.ConfiguredItemDefinition;
import emaki.jiuwu.craft.corelib.api.item.ItemBuildResult;
import emaki.jiuwu.craft.corelib.api.item.ItemComponentCapability;
import emaki.jiuwu.craft.corelib.api.readiness.ModuleReadinessListener;
import emaki.jiuwu.craft.corelib.api.readiness.ReadinessRegistration;
import emaki.jiuwu.craft.corelib.api.scheduling.EmakiScheduling;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ConfiguredItemService;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.api.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class DefaultEmakiCoreLibApi implements EmakiCoreLibApi.Bridge {

    private static final CoreLibDialogs UNAVAILABLE_DIALOGS = new UnavailableDialogs();

    private final EmakiCoreLibPlugin plugin;
    private final ActionExecutionApiService actionExecution;
    private final ActionDescriptorApiService actionDescriptors;
    private volatile CoreLibDialogs dialogBridge;
    private volatile EmakiScheduling schedulingBridge;

    public DefaultEmakiCoreLibApi(EmakiCoreLibPlugin plugin) {
        this.plugin = plugin;
        actionExecution = new ActionExecutionApiService(plugin);
        actionDescriptors = new ActionDescriptorApiService(plugin);
    }

    @Override
    public ApiStatus status() {
        if (!plugin.isEnabled()) {
            return ApiStatus.notInstalled();
        }
        String pluginName = plugin.getName();
        String version = plugin.getPluginMeta().getVersion();

        return plugin.contentReady()
                ? ApiStatus.ready(pluginName, version, version)
                : ApiStatus.loading(pluginName, version, version);
    }

    @Override
    public CoreLibDialogs dialogs() {
        CoreLibDialogs resolved = dialogBridge;
        return resolved == null ? UNAVAILABLE_DIALOGS : resolved;
    }

    @Override
    public EmakiScheduling scheduling() {
        EmakiScheduling resolved = schedulingBridge;
        if (resolved == null) {
            resolved = new DefaultEmakiScheduling(plugin.executionDispatcher(), plugin.threadOwnership());
            schedulingBridge = resolved;
        }
        return resolved;
    }

    public void installDialogs(CoreLibDialogs dialogs) {
        this.dialogBridge = dialogs;
    }

    @Override
    public EmakiResult<String> itemDisplayName(String itemSource) {
        ItemSourceRef source = ItemSourceUtil.parse(itemSource);
        String displayName = plugin.itemSourceService().displayName(source);
        if (!Texts.isBlank(displayName)) {
            return EmakiResult.success(displayName);
        }
        String echoed = Texts.toStringSafe(itemSource);
        return echoed.isEmpty()
                ? EmakiResult.invalidInput("corelib.item.source_missing")
                : EmakiResult.partial(echoed, "corelib.item.display_name_unresolved");
    }

    @Override
    public EmakiResult<String> itemDisplayName(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return EmakiResult.invalidInput("corelib.item.stack_missing");
        }
        ItemSourceRef source = plugin.itemSourceService().identifyItem(itemStack);
        String displayName = plugin.itemSourceService().displayName(source);
        if (!Texts.isBlank(displayName)) {
            return EmakiResult.success(displayName);
        }
        String effective = ItemTextBridge.effectiveNameText(itemStack);
        return Texts.isBlank(effective)
                ? EmakiResult.notFound("corelib.item.display_name_unresolved")
                : EmakiResult.partial(effective, "corelib.item.display_name_unresolved");
    }

    @Override
    public ItemBuildResult createConfiguredItem(ConfiguredItemDefinition definition, Map<String, ?> replacements) {
        ConfiguredItemService service = plugin.configuredItemService();
        return service == null
                ? ItemBuildResult.unavailable("CoreLib configured item service is unavailable.")
                : service.create(definition, replacements);
    }

    @Override
    public ItemBuildResult applyConfiguredItem(ItemStack itemStack,
            ConfiguredItemDefinition definition,
            Map<String, ?> replacements) {
        ConfiguredItemService service = plugin.configuredItemService();
        return service == null
                ? ItemBuildResult.unavailable("CoreLib configured item service is unavailable.")
                : service.apply(itemStack, definition, replacements);
    }

    @Override
    public List<ItemComponentCapability> itemComponentCapabilities() {
        ConfiguredItemService service = plugin.configuredItemService();
        return service == null ? List.of() : service.capabilities();
    }

    @Override
    public CompletableFuture<CoreActionExecutionResult> executeActionLineAsync(Plugin owner,
            String line,
            CoreActionExecutionContext context) {
        return actionExecution.execute(owner, line, context);
    }

    @Override
    public List<CoreActionStageDescriptor> actionStages() {
        return actionDescriptors.stages();
    }

    @Override
    public Optional<CoreActionStageDescriptor> actionStage(String stageId) {
        return actionDescriptors.stage(stageId);
    }

    @Override
    public List<CoreActionTriggerDescriptor> actionTriggers() {
        return actionDescriptors.triggers();
    }

    @Override
    public Optional<CoreActionTriggerDescriptor> actionTrigger(String triggerId) {
        return actionDescriptors.trigger(triggerId);
    }

    @Override
    public CoreStageRegistration registerActionStage(Plugin owner, CoreActionStage stage) {
        StageRegistry registry = plugin.stageRegistry();
        return registry == null
                ? CoreStageRegistration.unavailable(CoreStageKind.ACTION, "action.register.registry_unavailable")
                : registry.registerAction(owner, stage);
    }

    @Override
    public CoreStageRegistration registerActionSource(Plugin owner, CoreActionSource source) {
        StageRegistry registry = plugin.stageRegistry();
        return registry == null
                ? CoreStageRegistration.unavailable(CoreStageKind.SOURCE, "action.register.registry_unavailable")
                : registry.registerSource(owner, source);
    }

    @Override
    public CoreStageRegistration registerActionGate(Plugin owner, CoreActionGate gate) {
        StageRegistry registry = plugin.stageRegistry();
        return registry == null
                ? CoreStageRegistration.unavailable(CoreStageKind.GATE, "action.register.registry_unavailable")
                : registry.registerGate(owner, gate);
    }

    @Override
    public CoreTriggerRegistration registerActionTrigger(Plugin owner, CoreActionTrigger trigger) {
        return plugin.triggerRegistry().register(owner, trigger);
    }

    @Override
    public CompletableFuture<EmakiResult<Unit>> dispatchTriggerAsync(Plugin owner,
            String triggerId,
            CoreTriggerDispatch dispatch) {
        if (owner == null) {
            return CompletableFuture.completedFuture(
                    EmakiResult.invalidInput("action.trigger.dispatch.no_owner"));
        }
        if (Texts.isBlank(triggerId)) {
            return CompletableFuture.completedFuture(
                    EmakiResult.invalidInput("action.trigger.dispatch.blank_id"));
        }
        if (dispatch == null) {
            return CompletableFuture.completedFuture(
                    EmakiResult.invalidInput("action.trigger.dispatch.no_dispatch"));
        }

        RegisteredTrigger registered = plugin.triggerRegistry().lookup(triggerId);
        if (registered == null) {
            return CompletableFuture.completedFuture(
                    EmakiResult.notFound("action.trigger.dispatch.unknown_trigger"));
        }
        if (registered.owner() != owner) {
            return CompletableFuture.completedFuture(
                    EmakiResult.failure(FailureKind.REJECTED, "action.trigger.dispatch.owner_mismatch"));
        }
        if (dispatch.lines().isEmpty()) {
            return CompletableFuture.completedFuture(EmakiResult.ok());
        }

        CompletableFuture<Player> caster = dispatch.casterId() == null
                ? CompletableFuture.completedFuture(null)
                : plugin.executionDispatcher().submitGlobal(owner, () -> Bukkit.getPlayer(dispatch.casterId()));
        TriggerContract contract = registered.contract();
        return caster.thenCompose(player -> {
            CoreActionExecutionContext.Builder contextBuilder = CoreActionExecutionContext.builder()
                    .caster(player == null ? null : CoreActionSubject.of(player))
                    .phase(dispatch.phase())
                    .silent(dispatch.silent())
                    .variables(dispatch.variables());
            if (dispatch.hasTriggerName()) {
                contextBuilder.data(CoreActionKeys.TRIGGER, dispatch.triggerName());
            }
            return executeTriggerLines(owner, dispatch.lines(), contextBuilder.build(),
                    contract.phase(dispatch.phase()), 0, false, null);
        }).thenApply(DefaultEmakiCoreLibApi::triggerResult)
                .exceptionally(throwable -> EmakiResult.failure(FailureKind.INTERNAL_ERROR,
                        "action.trigger.dispatch.exception",
                        Map.of("error", Texts.toStringSafe(unwrap(throwable).getMessage()))));
    }

    @Override
    public boolean onStageRegistryRebuilt(Plugin owner, Runnable reregister) {
        return plugin.stageRebuildListeners().register(owner, reregister);
    }

    @Override
    public CoreStageRebuildRegistration addStageRegistryRebuildListener(Plugin owner, Runnable reregister) {
        return plugin.stageRebuildListeners().add(owner, reregister);
    }

    @Override
    public CapabilityRegistration publishCapabilities(Plugin owner, Set<ApiCapability> capabilities) {
        return plugin.capabilityRegistry().publish(owner, capabilities);
    }

    @Override
    public int revokeCapabilities(Plugin owner) {
        return plugin.capabilityRegistry().revokeAll(owner);
    }

    @Override
    public boolean hasCapability(ApiCapability capability) {
        return plugin.capabilityRegistry().has(capability);
    }

    @Override
    public Set<ApiCapability> capabilities() {
        return plugin.capabilityRegistry().all();
    }

    @Override
    public Set<ApiCapability> capabilitiesOf(String pluginName) {
        return plugin.capabilityRegistry().ownedBy(pluginName);
    }

    @Override
    public ReadinessRegistration whenReady(Plugin owner, String moduleName, Runnable callback) {
        return plugin.moduleReadinessRegistry().whenReady(owner, moduleName, callback,
                failure -> plugin.getLogger().warning("Readiness callback failed for " + failure.owner()
                        + " waiting on " + failure.moduleName() + ": " + failure.error()));
    }

    @Override
    public boolean isModuleReady(String moduleName) {
        return plugin.moduleReadinessRegistry().isReady(moduleName);
    }

    @Override
    public ReadinessRegistration addModuleListener(Plugin owner,
            String moduleName,
            ModuleReadinessListener listener) {
        return plugin.moduleReadinessRegistry().addListener(owner, moduleName, listener);
    }

    private CompletableFuture<TriggerExecution> executeTriggerLines(Plugin owner,
            List<String> lines,
            CoreActionExecutionContext context,
            PhaseContract phase,
            int index,
            boolean partial,
            CoreActionExecutionResult firstFailure) {
        if (index >= lines.size()) {
            return CompletableFuture.completedFuture(new TriggerExecution(partial, firstFailure));
        }
        String line = lines.get(index);
        if (Texts.isBlank(line)) {
            return executeTriggerLines(owner, lines, context, phase, index + 1, partial, firstFailure);
        }
        return actionExecution.execute(owner, line, context, phase).thenCompose(result -> {
            boolean nextPartial = partial || result.status() == CoreActionExecutionStatus.PARTIAL;
            CoreActionExecutionResult nextFailure = firstFailure == null && triggerFailed(result)
                    ? result
                    : firstFailure;
            return executeTriggerLines(owner, lines, context, phase, index + 1, nextPartial, nextFailure);
        });
    }

    private static boolean triggerFailed(CoreActionExecutionResult result) {
        return switch (result.status()) {
            case COMPILE_FAILED, EXECUTION_FAILED, INVALID_REQUEST, UNAVAILABLE -> true;
            case SUCCESS, SKIPPED, PARTIAL -> false;
        };
    }

    private static EmakiResult<Unit> triggerResult(TriggerExecution execution) {
        CoreActionExecutionResult failure = execution.failure();
        if (failure == null) {
            return execution.partial()
                    ? EmakiResult.partial(Unit.INSTANCE, "action.trigger.dispatch.partial")
                    : EmakiResult.ok();
        }
        FailureKind kind = switch (failure.status()) {
            case UNAVAILABLE -> FailureKind.UNAVAILABLE;
            case INVALID_REQUEST, COMPILE_FAILED -> FailureKind.INVALID_INPUT;
            case EXECUTION_FAILED -> failureKind(failure.failureKind());
            case SUCCESS, SKIPPED, PARTIAL -> FailureKind.INTERNAL_ERROR;
        };
        String reasonKey = Texts.isBlank(failure.reasonKey())
                ? "action.trigger.dispatch.line_failed"
                : failure.reasonKey();
        return EmakiResult.failure(kind, reasonKey, failure.reasonArguments());
    }

    private static FailureKind failureKind(CoreActionFailureKind kind) {
        if (kind == null) {
            return FailureKind.INTERNAL_ERROR;
        }
        return switch (kind) {
            case INVALID_CONFIG -> FailureKind.INVALID_INPUT;
            case MISSING_CONTEXT, REJECTED -> FailureKind.REJECTED;
            case WRONG_THREAD -> FailureKind.WRONG_THREAD;
            case OWNER_DISABLED -> FailureKind.UNAVAILABLE;
            case TIMEOUT, INTERNAL_ERROR -> FailureKind.INTERNAL_ERROR;
        };
    }

    private record TriggerExecution(boolean partial, CoreActionExecutionResult failure) {
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
