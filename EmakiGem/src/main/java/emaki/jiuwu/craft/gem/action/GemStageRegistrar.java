package emaki.jiuwu.craft.gem.action.v2;

import java.util.ArrayList;
import java.util.List;

import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageRegistration;
import emaki.jiuwu.craft.gem.EmakiGemPlugin;

/**
 * Registers this module's pipeline stages into EmakiCoreLib's single stage registry.
 *
 * <p>Registration is replayed after a CoreLib reload, which rebuilds the stage table; without the rebuild
 * callback these stages would disappear the first time a server owner reloaded.</p>
 */
public final class GemStageRegistrar {

    private final EmakiGemPlugin plugin;
    private final List<CoreStageRegistration> handles = new ArrayList<>();

    public GemStageRegistrar(EmakiGemPlugin plugin) {
        this.plugin = plugin;
    }

    /** Registers every stage and asks to be replayed on reload. Safe to call twice. */
    public void register() {
        closeHandles();
        for (GemHeldItemStage.Operation operation : GemHeldItemStage.Operation.values()) {
            CoreStageRegistration registration = EmakiCoreLibApi.registerActionStage(
                    plugin, new GemHeldItemStage(plugin, operation));
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
