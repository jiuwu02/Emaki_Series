package emaki.jiuwu.craft.corelib.gui.packet;

import java.util.logging.Logger;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.gui.GuiBackendRegistry;

/**
 * Installs the packet-driven GUI backend into CoreLib's
 * {@link GuiBackendRegistry} when the optional PacketEvents plugin is present.
 *
 * <p>This class is the single entry point that touches PacketEvents protocol
 * types. CoreLib only references it after confirming the PacketEvents plugin is
 * enabled, and wraps the call in a {@code try/catch} for {@link LinkageError}
 * and {@link RuntimeException}. That keeps the packet protocol classes from
 * being loaded at all when PacketEvents is absent, so CoreLib retains zero hard
 * dependency on PacketEvents and silently falls back to the Bukkit backend.</p>
 */
public final class PacketBackendInstaller {

    /** The backend name registered into {@link GuiBackendRegistry}. */
    public static final String BACKEND_NAME = "packet";

    private PacketBackendInstaller() {
    }

    /**
     * Attempts to register the packet backend. Must only be called once the
     * PacketEvents plugin is confirmed present and enabled.
     *
     * @return {@code true} when the packet backend was registered, {@code false}
     *         when the runtime is unsupported (older than 1.19.4).
     */
    public static boolean install(JavaPlugin coreLib,
            GuiBackendRegistry registry,
            ExecutionDispatcher executionDispatcher) {
        Logger logger = coreLib.getLogger();
        if (!PacketGuiBackend.isRuntimeSupported()) {
            logger.warning("The packet GUI backend requires Minecraft 1.19.4 or newer; packet backend not registered. EmakiCoreLib will use the Bukkit (entity) backend.");
            return false;
        }
        registry.register(BACKEND_NAME, new PacketGuiBackend(coreLib, executionDispatcher));
        logger.info("Registered the packet GUI backend (PacketEvents detected).");
        return true;
    }
}
