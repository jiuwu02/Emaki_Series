package emaki.jiuwu.craft.corelib.gui.packet;

import java.util.logging.Logger;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.gui.GuiBackendRegistry;
import emaki.jiuwu.craft.corelib.log.LogTags;
import emaki.jiuwu.craft.corelib.log.LogTag;
import java.util.logging.Level;

public final class PacketBackendInstaller {

    public static final String BACKEND_NAME = "packet";

    private PacketBackendInstaller() {
    }

    public static boolean install(JavaPlugin coreLib,
            GuiBackendRegistry registry,
            ExecutionDispatcher executionDispatcher) {
        Logger logger = coreLib.getLogger();
        registry.register(BACKEND_NAME, new PacketGuiBackend(coreLib, executionDispatcher));
        LogTags.send(logger, LogTag.GUI, Level.INFO, "gui.packet_backend_registered");
        return true;
    }
}
