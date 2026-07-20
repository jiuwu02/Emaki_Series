package emaki.jiuwu.craft.corelib.gui.packet;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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

import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.gui.GuiBackend;
import emaki.jiuwu.craft.corelib.gui.GuiClickType;
import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.corelib.gui.GuiSessionRegistry;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.gui.SoundParser;

























public final class PacketGuiBackend implements GuiBackend, Listener {


    private static final int PLAYER_INVENTORY_SLOTS = 36;

    private final JavaPlugin plugin;
    private final ExecutionDispatcher executionDispatcher;
    private final AtomicInteger windowIdCounter = new AtomicInteger(1);
    private final Map<UUID, PacketWindow> windows = new ConcurrentHashMap<>();
    private final ClickListener clickListener = new ClickListener();
    private boolean registered;

    public PacketGuiBackend(JavaPlugin plugin, ExecutionDispatcher executionDispatcher) {
        if (executionDispatcher == null) {
            throw new IllegalArgumentException("executionDispatcher");
        }
        this.plugin = plugin;
        this.executionDispatcher = executionDispatcher;
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
        PacketWindow window = windows.get(viewer.getUniqueId());
        if (window == null || window.session != session) {
            return;
        }
        returnCursor(viewer, window);
        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, new WrapperPlayServerCloseWindow(window.windowId));
        windows.remove(viewer.getUniqueId(), window);
    }

    @Override
    public void shutdown() {
        if (!registered) {
            return;
        }
        Map<UUID, PacketWindow> snapshot = Map.copyOf(windows);
        CountDownLatch shutdownLatch = new CountDownLatch(snapshot.size());
        for (Map.Entry<UUID, PacketWindow> entry : snapshot.entrySet()) {
            closeWindowDuringShutdown(entry.getKey(), entry.getValue(), shutdownLatch);
        }
        try {
            shutdownLatch.await(2L, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        windows.clear();
        try {
            PacketEvents.getAPI().getEventManager().unregisterListener(clickListener);
        } catch (RuntimeException | LinkageError ignored) {

        }
        HandlerList.unregisterAll(this);
        registered = false;
    }

    private void closeWindowDuringShutdown(UUID viewerId, PacketWindow window, CountDownLatch shutdownLatch) {
        if (window == null) {
            shutdownLatch.countDown();
            return;
        }
        GuiSession session = window.session;
        Player viewer = session.viewer();
        if (viewer != null && viewer.isOnline()) {
            Runnable task = () -> {
                try {
                    cleanupWindow(viewerId, window, viewer, true, true);
                } finally {
                    shutdownLatch.countDown();
                }
            };
            Runnable retired = () -> {
                try {
                    cleanupWindow(viewerId, window, viewer, false, false);
                } finally {
                    shutdownLatch.countDown();
                }
            };
            try {
                if (executionDispatcher.runEntity(plugin, viewer, task, retired) != null) {
                    return;
                }
            } catch (RuntimeException | LinkageError exception) {

            }
            retired.run();
            return;
        }
        try {
            cleanupWindow(viewerId, window, viewer, false, true);
        } finally {
            shutdownLatch.countDown();
        }
    }

    private void cleanupWindow(UUID viewerId,
            PacketWindow window,
            Player viewer,
            boolean touchPlayer,
            boolean notifyHandler) {
        GuiSession session = window.session;
        if (touchPlayer && viewer != null && viewer.isOnline()) {
            returnCursor(viewer, window);
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, new WrapperPlayServerCloseWindow(window.windowId));
        }
        if (notifyHandler) {
            session.handler().onClose(session, new PacketGuiCloseContext(viewer, window));
        }
        GuiSessionRegistry registry = session.registry();
        if (registry != null && viewer != null) {
            registry.removeSession(viewer.getUniqueId(), session);
        }
        windows.remove(viewerId, window);
    }

    private int nextWindowId() {

        int id = windowIdCounter.getAndUpdate(current -> current >= 100 ? 1 : current + 1);
        return id;
    }

    private int topSize(GuiSession session) {
        int size = session.getInventory().getSize();
        return Math.max(9, size);
    }

    private void sendOpenWindow(Player viewer, PacketWindow window) {
        int rows = Math.max(1, Math.min(6, window.topSize / 9));
        int type = rows - 1;
        WrapperPlayServerOpenWindow open = new WrapperPlayServerOpenWindow(
                window.windowId,
                type,
                window.session.titleComponent()
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

    private void dispatchViewerEvent(Player viewer, Runnable task, Runnable retired) {
        try {
            if (executionDispatcher.runEntity(plugin, viewer, task, retired) != null) {
                return;
            }
        } catch (RuntimeException | LinkageError exception) {

        }
        if (retired != null) {
            retired.run();
        }
    }




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


        sendWindowItems(viewer, window);
    }

    private void handleClose(Player viewer, int windowId) {
        PacketWindow window = windows.get(viewer.getUniqueId());
        if (window == null || window.windowId != windowId) {
            return;
        }
        GuiSession session = window.session;
        returnCursor(viewer, window);
        session.handler().onClose(session, new PacketGuiCloseContext(viewer, window));
        GuiSessionRegistry registry = session.registry();
        if (registry != null) {
            registry.removeSession(viewer.getUniqueId(), session);
        }
        windows.remove(viewer.getUniqueId(), window);
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
                dispatchViewerEvent(viewer,
                        () -> handleClick(viewer, packet),
                        () -> windows.remove(viewer.getUniqueId()));
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
                dispatchViewerEvent(viewer,
                        () -> handleClose(viewer, windowId),
                        () -> windows.remove(viewer.getUniqueId()));
            }
        }
    }
}
