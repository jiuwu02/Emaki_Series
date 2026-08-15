package emaki.jiuwu.craft.corelib.gui;

import java.util.Map;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.item.ConfiguredItemService;

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

    }
}
