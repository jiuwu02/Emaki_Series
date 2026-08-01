package emaki.jiuwu.craft.corelib.apiimpl;

import java.util.List;
import java.util.Map;

import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.action.v2.registry.StageRegistry;
import emaki.jiuwu.craft.corelib.api.CompatibilityReport;
import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionGate;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionSource;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionStage;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageKind;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageRegistration;
import emaki.jiuwu.craft.corelib.api.contract.ApiStatus;
import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.dialog.CoreLibDialogs;
import emaki.jiuwu.craft.corelib.api.item.ConfiguredItemDefinition;
import emaki.jiuwu.craft.corelib.api.item.ItemBuildResult;
import emaki.jiuwu.craft.corelib.api.item.ItemComponentCapability;
import emaki.jiuwu.craft.corelib.api.scheduling.EmakiScheduling;
import emaki.jiuwu.craft.corelib.execution.PlatformCapabilities;
import emaki.jiuwu.craft.corelib.item.ConfiguredItemService;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class DefaultEmakiCoreLibApi implements EmakiCoreLibApi.Bridge {

    private static final CoreLibDialogs UNAVAILABLE_DIALOGS = new UnavailableDialogs();

    private final EmakiCoreLibPlugin plugin;
    private final PlatformCapabilities platformCapabilities;
    private volatile CoreLibDialogs dialogBridge;
    private volatile EmakiScheduling schedulingBridge;

    public DefaultEmakiCoreLibApi(EmakiCoreLibPlugin plugin, PlatformCapabilities platformCapabilities) {
        this.plugin = plugin;
        this.platformCapabilities = java.util.Objects.requireNonNull(platformCapabilities, "platformCapabilities");
    }

    @Override
    public ApiStatus status() {
        if (!plugin.isEnabled()) {
            return ApiStatus.notInstalled();
        }
        String pluginName = plugin.getName();
        String version = plugin.getDescription().getVersion();
        return plugin.messageService() == null
                ? ApiStatus.loading(pluginName, version, version)
                : ApiStatus.ready(pluginName, version, version);
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
    public CompatibilityReport compatibilityReport() {
        return platformCapabilities.compatibilityReport(status().apiVersion());
    }

    @Override
    public EmakiResult<String> itemDisplayName(String itemSource) {
        ItemSource source = ItemSourceUtil.parse(itemSource);
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
        ItemSource source = plugin.itemSourceService().identifyItem(itemStack);
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
                ? CoreStageRegistration.unavailable(CoreStageKind.ACTION, "action.v2.register.registry_unavailable")
                : registry.registerAction(owner, stage);
    }

    @Override
    public CoreStageRegistration registerActionSource(Plugin owner, CoreActionSource source) {
        StageRegistry registry = plugin.stageRegistry();
        return registry == null
                ? CoreStageRegistration.unavailable(CoreStageKind.SOURCE, "action.v2.register.registry_unavailable")
                : registry.registerSource(owner, source);
    }

    @Override
    public CoreStageRegistration registerActionGate(Plugin owner, CoreActionGate gate) {
        StageRegistry registry = plugin.stageRegistry();
        return registry == null
                ? CoreStageRegistration.unavailable(CoreStageKind.GATE, "action.v2.register.registry_unavailable")
                : registry.registerGate(owner, gate);
    }

    @Override
    public boolean onStageRegistryRebuilt(Plugin owner, Runnable reregister) {
        return plugin.stageRebuildListeners().register(owner, reregister);
    }
}
