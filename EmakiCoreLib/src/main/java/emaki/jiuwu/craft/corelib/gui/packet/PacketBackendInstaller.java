package emaki.jiuwu.craft.corelib.gui.packet;

import java.util.logging.Logger;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.gui.GuiBackendRegistry;












public final class PacketBackendInstaller {


    public static final String BACKEND_NAME = "packet";

    private PacketBackendInstaller() {
    }








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
