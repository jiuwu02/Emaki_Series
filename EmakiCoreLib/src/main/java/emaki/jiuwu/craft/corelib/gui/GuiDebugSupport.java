package emaki.jiuwu.craft.corelib.gui;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.debug.DebugLoggerProvider;

public final class GuiDebugSupport {

    private static final String MODULE = "gui";

    private GuiDebugSupport() {
    }

    public static void log(Plugin plugin, Player player, String message) {
        if (!(plugin instanceof DebugLoggerProvider provider)) {
            return;
        }
        DebugLogger logger = provider.debugLogger();
        if (logger != null) {
            logger.logRaw(MODULE, player, message);
        }
    }

    public static String describeItem(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return "air";
        }
        return itemStack.getType().getKey() + "x" + itemStack.getAmount();
    }

    public static String describeSession(GuiSession session) {
        if (session == null) {
            return "session=null";
        }
        String owner = session.owner() == null ? "unknown" : session.owner().getName();
        String backend = session.backend() == null ? "unknown" : session.backend().name();
        return "session=" + Integer.toHexString(System.identityHashCode(session))
                + " owner=" + owner
                + " backend=" + backend
                + " title=" + summarize(session.plainTitle());
    }

    private static String summarize(String value) {
        if (value == null || value.isBlank()) {
            return "<empty>";
        }
        String normalized = value.replace('\n', ' ').replace('\r', ' ');
        return normalized.length() <= 80 ? normalized : normalized.substring(0, 77) + "...";
    }
}
