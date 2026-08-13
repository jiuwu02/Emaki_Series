package emaki.jiuwu.craft.corelib.apiimpl;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.action.pipeline.ActionLineRunner;
import emaki.jiuwu.craft.corelib.action.pipeline.PipelineContext;
import emaki.jiuwu.craft.corelib.action.pipeline.registry.StageRegistry;
import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;
import emaki.jiuwu.craft.corelib.api.action.CoreActionGate;
import emaki.jiuwu.craft.corelib.api.action.CoreActionKeys;
import emaki.jiuwu.craft.corelib.api.action.CoreActionSource;
import emaki.jiuwu.craft.corelib.api.action.CoreActionStage;
import emaki.jiuwu.craft.corelib.api.action.CoreActionTrigger;
import emaki.jiuwu.craft.corelib.api.action.CoreStageKind;
import emaki.jiuwu.craft.corelib.api.action.CoreStageRegistration;
import emaki.jiuwu.craft.corelib.api.action.CoreTriggerDispatch;
import emaki.jiuwu.craft.corelib.api.action.CoreTriggerRegistration;
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
    private volatile CoreLibDialogs dialogBridge;
    private volatile EmakiScheduling schedulingBridge;

    public DefaultEmakiCoreLibApi(EmakiCoreLibPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public ApiStatus status() {
        if (!plugin.isEnabled()) {
            return ApiStatus.notInstalled();
        }
        String pluginName = plugin.getName();
        String version = plugin.getPluginMeta().getVersion();
        // Data criterion, not "one service exists": messageService is non-null from initializeServices()
        // onward, so the old check reported ready while a reload was still swapping the stage table.
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

    /**
     * 安装对话框层。由 {@link EmakiCoreLibPlugin} 在对话框子系统就绪后调用。
     *
     * @param dialogs 对话框层实现；{@code null} 表示子系统不可用
     */
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
        if (Texts.isBlank(triggerId)) {
            return CompletableFuture.completedFuture(
                    EmakiResult.invalidInput("action.trigger.dispatch.blank_id"));
        }
        if (dispatch == null) {
            return CompletableFuture.completedFuture(
                    EmakiResult.invalidInput("action.trigger.dispatch.no_dispatch"));
        }
        // An unknown id fails instead of running permissively. A typo would otherwise skip exactly the
        // contract check that registering the trigger exists to provide.
        TriggerContract contract = plugin.triggerRegistry().contractOf(triggerId);
        if (contract == null) {
            return CompletableFuture.completedFuture(
                    EmakiResult.notFound("action.trigger.dispatch.unknown_trigger"));
        }
        if (dispatch.lines().isEmpty()) {
            return CompletableFuture.completedFuture(EmakiResult.ok());
        }
        ActionLineRunner runner = plugin.actionLineRunner(owner);
        if (!runner.available()) {
            return CompletableFuture.completedFuture(EmakiResult.failure(FailureKind.UNAVAILABLE,
                    "action.trigger.dispatch.engine_unavailable"));
        }
        // Resolved from the UUID here rather than carried as an entity: the dispatch carrier holds no live
        // entity reference, matching what CoreActionKeys.TRIGGER already does for the same reason.
        Player caster = dispatch.casterId() == null ? null : Bukkit.getPlayer(dispatch.casterId());
        PipelineContext context = runner.context(caster, dispatch.phase(), dispatch.silent(),
                dispatch.variables());
        if (dispatch.hasTriggerName()) {
            context = context.with(CoreActionKeys.TRIGGER, dispatch.triggerName());
        }
        return runner.run(dispatch.lines(), context, contract, false)
                .thenApply(succeeded -> succeeded
                        ? EmakiResult.ok()
                        : EmakiResult.partial(Unit.INSTANCE, "action.trigger.dispatch.line_failed"));
    }

    @Override
    public boolean onStageRegistryRebuilt(Plugin owner, Runnable reregister) {
        return plugin.stageRebuildListeners().register(owner, reregister);
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
}
