package emaki.jiuwu.craft.guipacket;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.gui.GuiBackendRegistry;

/**
 * Optional plugin that supplies the packet-driven GUI backend to EmakiCoreLib.
 *
 * <p>EmakiCoreLib ships only the built-in {@code bukkit} backend. This plugin
 * registers a {@code packet} backend into CoreLib's {@link GuiBackendRegistry}
 * on enable, so servers that want the packet backend simply install this plugin
 * (which depends on PacketEvents). When this plugin is absent CoreLib silently
 * uses the Bukkit backend.</p>
 */
public final class EmakiGuiPacketPlugin extends JavaPlugin {

    private static final String BACKEND_NAME = "packet";

    private GuiBackendRegistry registry;

    @Override
    public void onEnable() {
        EmakiCoreLibPlugin coreLib = JavaPlugin.getPlugin(EmakiCoreLibPlugin.class);
        registry = coreLib.guiBackendRegistry();
        if (registry == null) {
            getLogger().warning("EmakiCoreLib GUI backend registry is unavailable; packet backend not registered.");
            return;
        }
        try {
            if (!PacketGuiBackend.isRuntimeSupported()) {
                getLogger().warning("The packet GUI backend requires Minecraft 1.19.4 or newer; packet backend not registered. EmakiCoreLib will use the Bukkit (entity) backend.");
                registry = null;
                return;
            }
            registry.register(BACKEND_NAME, new PacketGuiBackend(this));
            getLogger().info("Registered the packet GUI backend with EmakiCoreLib.");
        } catch (LinkageError | RuntimeException exception) {
            getLogger().warning("Failed to register the packet GUI backend: " + exception.getMessage() + ". EmakiCoreLib will use the Bukkit (entity) backend.");
            registry = null;
        }
    }

    @Override
    public void onDisable() {
        if (registry != null) {
            registry.unregister(BACKEND_NAME);
            registry = null;
        }
    }
}
