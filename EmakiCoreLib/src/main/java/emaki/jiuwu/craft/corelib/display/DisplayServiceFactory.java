package emaki.jiuwu.craft.corelib.display;

import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.display.bukkit.BukkitItemDisplayService;
import emaki.jiuwu.craft.corelib.display.bukkit.BukkitTextDisplayService;
import emaki.jiuwu.craft.corelib.display.packet.PacketItemDisplayService;
import emaki.jiuwu.craft.corelib.display.packet.PacketTextDisplayService;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class DisplayServiceFactory {

    public static final String BACKEND_BUKKIT = "bukkit";
    public static final String BACKEND_PACKET = "packet";
    public static final String BACKEND_AUTO = "auto";

    private static final String PACKET_EVENTS_PLUGIN = "PacketEvents";

    private DisplayServiceFactory() {
    }

    public static String normalizeBackend(String raw) {
        if (Texts.isBlank(raw)) {
            return BACKEND_AUTO;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case BACKEND_BUKKIT -> BACKEND_BUKKIT;
            case BACKEND_PACKET, "packet_events", "packetevents" -> BACKEND_PACKET;
            default -> BACKEND_AUTO;
        };
    }

    public static TextDisplayService createTextService(Plugin owner,
            String backendName,
            DisplayRuntimeSettings settings,
            ExecutionDispatcher dispatcher) {
        DisplayRuntimeSettings safeSettings = settings == null
                ? DisplayRuntimeSettings.of(48D, 20)
                : settings;
        if (!usePacket(owner, normalizeBackend(backendName))) {
            return new BukkitTextDisplayService(owner, dispatcher);
        }
        try {
            return new PacketTextDisplayService(owner, safeSettings, dispatcher);
        } catch (LinkageError | RuntimeException exception) {
            owner.getLogger().warning("[display] Could not start the packet text backend, "
                    + "falling back to real entities: " + exception.getMessage());
            return new BukkitTextDisplayService(owner, dispatcher);
        }
    }

    public static ItemDisplayService createItemService(Plugin owner,
            String backendName,
            DisplayRuntimeSettings settings,
            ExecutionDispatcher dispatcher) {
        DisplayRuntimeSettings safeSettings = settings == null
                ? DisplayRuntimeSettings.of(48D, 20)
                : settings;
        if (!usePacket(owner, normalizeBackend(backendName))) {
            return new BukkitItemDisplayService(owner, dispatcher);
        }
        try {
            return new PacketItemDisplayService(owner, safeSettings, dispatcher);
        } catch (LinkageError | RuntimeException exception) {
            owner.getLogger().warning("[display] Could not start the packet item backend, "
                    + "falling back to real entities: " + exception.getMessage());
            return new BukkitItemDisplayService(owner, dispatcher);
        }
    }

    private static boolean usePacket(Plugin owner, String backend) {
        if (BACKEND_BUKKIT.equals(backend)) {
            return false;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled(PACKET_EVENTS_PLUGIN)) {
            if (BACKEND_PACKET.equals(backend)) {
                owner.getLogger().warning("[display] The packet backend needs PacketEvents installed, "
                        + "falling back to real entities.");
            }
            return false;
        }
        return true;
    }
}
