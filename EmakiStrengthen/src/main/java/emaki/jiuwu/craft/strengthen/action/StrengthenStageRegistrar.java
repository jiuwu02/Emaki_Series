package emaki.jiuwu.craft.strengthen.action.v2;

import java.util.ArrayList;
import java.util.List;

import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageRegistration;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;

/**
 * Registers this module's pipeline stages into EmakiCoreLib's single stage registry.
 *
 * <p>The module keeps no registry of its own (requirement R1): a stage registered here is usable from any
 * other module's pipeline without the two plugins depending on each other.</p>
 *
 * <p>Registration is also replayed after a CoreLib reload. CoreLib rebuilds its stage table on reload and
 * retires the previous one, so a module that registered only at {@code onEnable} would lose its stages the
 * first time a server owner reloaded. The rebuild callback is what keeps them alive.</p>
 */
public final class StrengthenStageRegistrar {

    private final EmakiStrengthenPlugin plugin;
    private final List<CoreStageRegistration> handles = new ArrayList<>();

    public StrengthenStageRegistrar(EmakiStrengthenPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Registers every stage and asks to be replayed on reload.
     *
     * <p>Safe to call twice: the previous handles are closed first, so a replay cannot trip the duplicate-id
     * rejection.</p>
     */
    public void register() {
        closeHandles();
        for (StrengthenHeldItemStage.Operation operation : StrengthenHeldItemStage.Operation.values()) {
            CoreStageRegistration registration = EmakiCoreLibApi.registerActionStage(
                    plugin, new StrengthenHeldItemStage(plugin, operation));
            if (registration.successful()) {
                handles.add(registration);
            } else {
                plugin.getLogger().warning("Failed to register pipeline stage '" + operation.id()
                        + "': " + registration.reasonKey());
            }
        }
        EmakiCoreLibApi.onStageRegistryRebuilt(plugin, this::register);
    }

    /** Revokes every stage this registrar installed. */
    public void unregister() {
        closeHandles();
    }

    private void closeHandles() {
        for (CoreStageRegistration handle : handles) {
            handle.close();
        }
        handles.clear();
    }
}
