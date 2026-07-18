package emaki.jiuwu.craft.corelib.gui;

import java.util.Map;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.item.ConfiguredItemService;

/**
 * A {@link GuiBackend} that delegates to whichever backend is currently active
 * in the {@link GuiBackendRegistry}.
 *
 * <p>This is the instance returned by {@code EmakiCoreLibPlugin#guiBackend()}.
 * Keeping the public type stable means the six business plugins can keep passing
 * {@code coreLib.guiBackend()} straight into their {@code new GuiService(...)}
 * with no change.</p>
 *
 * <p>{@link GuiService} unwraps this proxy when it creates a session and binds
 * the resolved real backend to that session, so each open menu keeps a stable
 * backend for its whole lifetime. The forwarding methods here are a defensive
 * fallback for any code path that calls a proxy directly.</p>
 */
public final class RegistryBackedGuiBackend implements GuiBackend {

    private final GuiBackendRegistry registry;
    private final ConfiguredItemService configuredItemService;

    public RegistryBackedGuiBackend(GuiBackendRegistry registry) {
        this(registry, null);
    }

    public RegistryBackedGuiBackend(GuiBackendRegistry registry, ConfiguredItemService configuredItemService) {
        this.registry = registry;
        this.configuredItemService = configuredItemService;
    }

    /** {@return the backend currently selected by {@code gui.backend}} */
    public GuiBackend resolveActive() {
        return registry.activeBackend();
    }

    @Override
    public void open(GuiSession session, Map<Integer, ItemStack> renderedSlots) {
        resolveActive().open(session, renderedSlots);
    }

    @Override
    public void applySlots(GuiSession session, Map<Integer, ItemStack> renderedSlots) {
        resolveActive().applySlots(session, renderedSlots);
    }

    @Override
    public void close(GuiSession session) {
        resolveActive().close(session);
    }

    @Override
    public String name() {
        return resolveActive().name();
    }

    @Override
    public ConfiguredItemService configuredItemService() {
        return configuredItemService;
    }

    @Override
    public void shutdown() {
        // No-op: backend lifecycles are managed by the registry's shutdownAll().
    }
}
