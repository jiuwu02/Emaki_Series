package emaki.jiuwu.craft.corelib.gui.packet;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientCloseWindow;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerCloseWindow;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerOpenWindow;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems;

import emaki.jiuwu.craft.corelib.async.FoliaSchedulerAdapter;
import emaki.jiuwu.craft.corelib.gui.GuiBackend;
import emaki.jiuwu.craft.corelib.gui.GuiClickType;
import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.corelib.gui.GuiSessionRegistry;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.gui.SoundParser;
import emaki.jiuwu.craft.corelib.text.MiniMessages;

/**
 * Packet-driven virtual GUI backend.
 *
 * <p>Unlike the built-in Bukkit backend this never opens a server-side
 * container. It sends the open-screen / window-items packets directly (the same
 * approach Hypixel and InvUI v2 take) so the same window id can be re-used when
 * the row count changes, which keeps the player's cursor from resetting.</p>
 *
 * <p>The server is authoritative: after every client click the full window
 * contents and the (virtual) carried item are re-sent, so the client's
 * optimistic prediction is always overwritten. The client's reported item data
 * is therefore ignored entirely, which also avoids the 1.21.5+ hashed-stack
 * representation. Only the clicked slot, button and click mode are consumed.</p>
 *
 * <p>This backend is a CoreLib-wide singleton shared by every plugin's
 * {@code GuiService}; per-viewer routing is resolved through the
 * {@link GuiSessionRegistry} carried by the session it opened.</p>
 *
 * <p><b>Caveat:</b> this is protocol-level code that cannot be validated in the
 * build environment. It only loads when the optional PacketEvents plugin is
 * present (CoreLib declares it as a soft-dependency) and is only active when
 * {@code gui.backend} selects {@code packet}/{@code auto}; the default
 * {@code bukkit} backend is unaffected.</p>
 */
public final class PacketGuiBackend implements GuiBackend, Listener {

    /** Player inventory raw slots that follow the top container (main + hotbar). */
    private static final int PLAYER_INVENTORY_SLOTS = 36;

    private final JavaPlugin plugin;
    private final AtomicInteger windowIdCounter = new AtomicInteger(1);
    private final Map<UUID, PacketWindow> windows = new ConcurrentHashMap<>();
    private final ClickListener clickListener = new ClickListener();
    private boolean registered;

    public PacketGuiBackend(JavaPlugin plugin) {
        this.plugin = plugin;
        PacketEvents.getAPI().getEventManager().registerListener(clickListener);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        this.registered = true;
    }

    public static boolean isRuntimeSupported() {
        return PacketEvents.getAPI()
                .getServerManager()
                .getVersion()
                .isNewerThanOrEquals(ServerVersion.V_1_19_4);
    }

    @Override
    public String name() {
        return "packet";
    }

    @Override
    public void open(GuiSession session, Map<Integer, org.bukkit.inventory.ItemStack> renderedSlots) {
        if (session == null || session.viewer() == null) {
            return;
        }
        Player viewer = session.viewer();
        int windowId = nextWindowId();
        int topSize = topSize(session);
        PacketWindow window = new PacketWindow(windowId, topSize, session);
        windows.put(viewer.getUniqueId(), window);
        sendOpenWindow(viewer, window);
        applyTopItems(window, renderedSlots);
        sendWindowItems(viewer, window);
    }

    @Override
    public void applySlots(GuiSession session, Map<Integer, org.bukkit.inventory.ItemStack> renderedSlots) {
        if (session == null || session.viewer() == null) {
            return;
        }
        Player viewer = session.viewer();
        PacketWindow window = windows.get(viewer.getUniqueId());
        if (window == null || window.session != session) {
            return;
        }
        int desiredSize = topSize(session);
        if (desiredSize != window.topSize) {
            // Row count changed: reuse the same window id and re-open in place so
            // the cursor is preserved, then refill.
            window.topSize = desiredSize;
            window.topItems = new org.bukkit.inventory.ItemStack[desiredSize];
            sendOpenWindow(viewer, window);
        }
        applyTopItems(window, renderedSlots);
        sendWindowItems(viewer, window);
    }

    @Override
    public void close(GuiSession session) {
        if (session == null || session.viewer() == null) {
            return;
        }
        Player viewer = session.viewer();
        PacketWindow window = windows.remove(viewer.getUniqueId());
        if (window == null || window.session != session) {
            return;
        }
        returnCursor(viewer, window);
        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, new WrapperPlayServerCloseWindow(window.windowId));
        GuiSessionRegistry registry = session.registry();
        if (registry != null) {
            registry.removeSession(viewer.getUniqueId(), session);
        }
    }

    @Override
    public void shutdown() {
        if (!registered) {
            return;
        }
        for (UUID viewerId : Map.copyOf(windows).keySet()) {
            PacketWindow window = windows.remove(viewerId);
            if (window == null) {
                continue;
            }
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer != null && viewer.isOnline()) {
                returnCursor(viewer, window);
                PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, new WrapperPlayServerCloseWindow(window.windowId));
            }
        }
        windows.clear();
        try {
            PacketEvents.getAPI().getEventManager().unregisterListener(clickListener);
        } catch (RuntimeException | LinkageError ignored) {
            // PacketEvents may already be tearing down on server stop.
        }
        HandlerList.unregisterAll(this);
        registered = false;
    }

    private int nextWindowId() {
        // Window id 0 is the player inventory; keep ids in the 1..100 byte range.
        int id = windowIdCounter.getAndUpdate(current -> current >= 100 ? 1 : current + 1);
        return id;
    }

    private int topSize(GuiSession session) {
        int size = session.getInventory().getSize();
        return Math.max(9, size);
    }

    private void sendOpenWindow(Player viewer, PacketWindow window) {
        int rows = Math.max(1, Math.min(6, window.topSize / 9));
        int type = rows - 1; // generic_9x1..9x6 => 0..5 on 1.14+
        WrapperPlayServerOpenWindow open = new WrapperPlayServerOpenWindow(
                window.windowId,
                type,
                MiniMessages.parse(window.session.title())
        );
        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, open);
    }

    private void applyTopItems(PacketWindow window, Map<Integer, org.bukkit.inventory.ItemStack> renderedSlots) {
        org.bukkit.inventory.ItemStack[] top = new org.bukkit.inventory.ItemStack[window.topSize];
        if (renderedSlots != null) {
            for (Map.Entry<Integer, org.bukkit.inventory.ItemStack> entry : renderedSlots.entrySet()) {
                int slot = entry.getKey();
                if (slot >= 0 && slot < window.topSize) {
                    top[slot] = entry.getValue();
                }
            }
        }
        window.topItems = top;
    }

    private void sendWindowItems(Player viewer, PacketWindow window) {
        List<com.github.retrooper.packetevents.protocol.item.ItemStack> items =
                new ArrayList<>(window.topSize + PLAYER_INVENTORY_SLOTS);
        for (int slot = 0; slot < window.topSize; slot++) {
            items.add(PacketItems.toPacket(window.topItems[slot]));
        }
        PlayerInventory inventory = viewer.getInventory();
        // Player inventory layout in a container: main slots 9..35 then hotbar 0..8.
        for (int slot = 9; slot < 36; slot++) {
            items.add(PacketItems.toPacket(inventory.getItem(slot)));
        }
        for (int slot = 0; slot < 9; slot++) {
            items.add(PacketItems.toPacket(inventory.getItem(slot)));
        }
        int stateId = window.nextStateId();
        WrapperPlayServerWindowItems packet = new WrapperPlayServerWindowItems(
                window.windowId,
                stateId,
                items,
                PacketItems.toPacket(window.cursor)
        );
        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, packet);
    }

    private void returnCursor(Player viewer, PacketWindow window) {
        if (window.cursor == null || window.cursor.getType().isAir()) {
            return;
        }
        Map<Integer, org.bukkit.inventory.ItemStack> overflow =
                viewer.getInventory().addItem(window.cursor.clone());
        for (org.bukkit.inventory.ItemStack leftover : overflow.values()) {
            if (leftover != null && !leftover.getType().isAir()) {
                viewer.getWorld().dropItemNaturally(viewer.getLocation(), leftover);
            }
        }
        window.cursor = null;
    }

    /**
     * Handles an inbound click on a packet window on the main thread.
     */
    private void handleClick(Player viewer, WrapperPlayClientClickWindow packet) {
        PacketWindow window = windows.get(viewer.getUniqueId());
        if (window == null || packet.getWindowId() != window.windowId) {
            return;
        }
        GuiSession session = window.session;
        GuiSessionRegistry registry = session.registry();
        if (registry == null || registry.activeSession(viewer.getUniqueId()) != session) {
            windows.remove(viewer.getUniqueId(), window);
            return;
        }
        int rawSlot = packet.getSlot();
        boolean top = rawSlot >= 0 && rawSlot < window.topSize;
        PacketGuiClickContext click = new PacketGuiClickContext(viewer, window, packet, top, this);
        if (top) {
            GuiTemplate.ResolvedSlot slot = session.template().resolvedSlotAt(rawSlot);
            playClickSound(session, slot, GuiClickType.from(packet.getWindowClickType().ordinal(), packet.getButton()));
            session.handler().onSlotClick(session, click, slot);
        } else {
            session.handler().onPlayerInventoryClick(session, click);
        }
        // Always re-assert authoritative state so the client never keeps an
        // optimistic prediction (and the cursor stays correct).
        sendWindowItems(viewer, window);
    }

    private void handleClose(Player viewer, int windowId) {
        PacketWindow window = windows.get(viewer.getUniqueId());
        if (window == null || window.windowId != windowId) {
            return;
        }
        windows.remove(viewer.getUniqueId(), window);
        GuiSession session = window.session;
        returnCursor(viewer, window);
        GuiSessionRegistry registry = session.registry();
        if (registry != null) {
            registry.removeSession(viewer.getUniqueId(), session);
        }
        session.handler().onClose(session, new PacketGuiCloseContext(viewer, window));
    }

    private void playClickSound(GuiSession session, GuiTemplate.ResolvedSlot slot, GuiClickType clickType) {
        if (session == null || slot == null) {
            return;
        }
        SoundParser.SoundDefinition sound = slot.definition().soundFor(clickType);
        if (sound == null) {
            return;
        }
        var resolved = SoundParser.resolve(sound);
        if (resolved != null) {
            session.viewer().playSound(session.viewer().getLocation(), resolved, sound.volume(), sound.pitch());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (event.getPlayer() != null) {
            windows.remove(event.getPlayer().getUniqueId());
        }
    }

    /**
     * Mutable virtual window state for one viewer.
     */
    static final class PacketWindow {

        private final int windowId;
        private final GuiSession session;
        private int topSize;
        private org.bukkit.inventory.ItemStack[] topItems;
        private org.bukkit.inventory.ItemStack cursor;
        private final AtomicInteger stateId = new AtomicInteger(1);

        PacketWindow(int windowId, int topSize, GuiSession session) {
            this.windowId = windowId;
            this.topSize = topSize;
            this.session = session;
            this.topItems = new org.bukkit.inventory.ItemStack[topSize];
        }

        int nextStateId() {
            return stateId.incrementAndGet();
        }

        int topSize() {
            return topSize;
        }

        org.bukkit.inventory.ItemStack topItem(int slot) {
            return slot >= 0 && slot < topSize ? topItems[slot] : null;
        }

        org.bukkit.inventory.ItemStack cursor() {
            return cursor;
        }

        void setCursor(org.bukkit.inventory.ItemStack cursor) {
            this.cursor = cursor;
        }

        GuiSession session() {
            return session;
        }
    }

    /**
     * PacketEvents listener translating client window packets into handler calls
     * on the server main thread.
     */
    private final class ClickListener extends PacketListenerAbstract {

        private ClickListener() {
            super(PacketListenerPriority.NORMAL);
        }

        @Override
        public void onPacketReceive(PacketReceiveEvent event) {
            if (event.getPacketType() == PacketType.Play.Client.CLICK_WINDOW) {
                Object playerObject = event.getPlayer();
                if (!(playerObject instanceof Player viewer)) {
                    return;
                }
                if (!windows.containsKey(viewer.getUniqueId())) {
                    return;
                }
                WrapperPlayClientClickWindow packet = new WrapperPlayClientClickWindow(event);
                if (packet.getWindowId() == 0) {
                    return;
                }
                event.setCancelled(true);
                FoliaSchedulerAdapter.runEntityTask(plugin, viewer, () -> handleClick(viewer, packet));
            } else if (event.getPacketType() == PacketType.Play.Client.CLOSE_WINDOW) {
                Object playerObject = event.getPlayer();
                if (!(playerObject instanceof Player viewer)) {
                    return;
                }
                if (!windows.containsKey(viewer.getUniqueId())) {
                    return;
                }
                WrapperPlayClientCloseWindow packet = new WrapperPlayClientCloseWindow(event);
                int windowId = packet.getWindowId();
                FoliaSchedulerAdapter.runEntityTask(plugin, viewer, () -> handleClose(viewer, windowId));
            }
        }
    }
}
