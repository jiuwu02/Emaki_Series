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
        registry.register(BACKEND_NAME, new PacketGuiBackend(coreLib, executionDispatcher));
        logger.info("已注册 packet GUI 后端（检测到 PacketEvents）。");
        return true;
    }
}
