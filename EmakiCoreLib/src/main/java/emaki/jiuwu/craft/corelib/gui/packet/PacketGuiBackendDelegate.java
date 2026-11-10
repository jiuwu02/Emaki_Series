package emaki.jiuwu.craft.corelib.gui.packet;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow.WindowClickType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientCloseWindow;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerCloseWindow;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerOpenWindow;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.gui.GuiBackend;
import emaki.jiuwu.craft.corelib.gui.GuiClickThrottle;
import emaki.jiuwu.craft.corelib.gui.GuiClickType;
import emaki.jiuwu.craft.corelib.gui.GuiDebugSupport;
import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.corelib.gui.GuiSessionRegistry;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.gui.SoundParser;

final class PacketGuiBackendDelegate {

    private final PacketGuiBackend ownerRef;

    PacketGuiBackendDelegate(PacketGuiBackend ownerRef) {
        this.ownerRef = ownerRef;
    }

    String name() {
        return "packet";
    }

    boolean supportsInPlaceSwitch() {
        return true;
    }

    void shutdown() {
        ownerRef.shutdownAsync();
    }

    boolean shutdownRequiresOwnerThread() {
        return false;
    }

    void completeWindowCleanup(UUID viewerId, PacketGuiBackend.PacketWindow window, Player viewer, boolean touchPlayer, boolean notifyHandler, AtomicBoolean cleanupStarted, CompletableFuture<Void> completion) {
        if (!cleanupStarted.compareAndSet(false, true)) {
            return;
        }
        try {
            if (ownerRef.windows.remove(viewerId, window)) {
                ownerRef.cleanupWindow(viewerId, window, viewer, touchPlayer, notifyHandler);
            }
            completion.complete(null);
        } catch (Throwable throwable) {
            ownerRef.debug(null, "common.gui.packet_shutdown_cleanup_failed", GuiDebugSupport.errorFields(
                    throwable,
                    windowFields(window)
            ));
            completion.completeExceptionally(throwable);
        }
    }

    void cleanupWindow(UUID viewerId, PacketGuiBackend.PacketWindow window, Player viewer, boolean touchPlayer, boolean notifyHandler) {
        GuiSession session = window.session;
        try {
            if (touchPlayer && viewer != null && viewer.isOnline()) {
                ownerRef.returnCursor(viewer, window);
                PacketEvents.getAPI().getPlayerManager().sendPacket(viewer,
                        new WrapperPlayServerCloseWindow(window.windowId));
            }
            if (notifyHandler) {
                try {
                    session.handler().onClose(session, new PacketGuiCloseContext(viewer, window));
                } catch (Throwable throwable) {
                    ownerRef.debug(viewer, "common.gui.packet_shutdown_close_handler_failed", GuiDebugSupport.errorFields(
                            throwable,
                            windowFields(window)
                    ));
                }
            }
        } finally {
            GuiSessionRegistry registry = session.registry();
            if (registry != null && viewerId != null) {
                registry.removeSession(viewerId, session);
            }
            ownerRef.windows.remove(viewerId, window);
        }
    }

    int nextWindowId() {
        return ownerRef.windowIdCounter.getAndUpdate(current -> current >= 100 ? 1 : current + 1);
    }

    int topSize(GuiSession session) {
        return Math.max(9, session.getInventory().getSize());
    }

    void sendOpenWindow(Player viewer, PacketGuiBackend.PacketWindow window) {
        int rows = Math.max(1, Math.min(6, window.topSize / 9));
        WrapperPlayServerOpenWindow open = new WrapperPlayServerOpenWindow(
                window.windowId,
                rows - 1,
                window.session.titleComponent()
        );
        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, open);
        ownerRef.debug(viewer, "common.gui.packet_send_open", ownerRef.windowFields(window));
    }

    void applyTopItems(PacketGuiBackend.PacketWindow window, Map<Integer, ItemStack> renderedSlots) {
        ItemStack[] top = new ItemStack[window.topSize];
        if (renderedSlots != null) {
            for (Map.Entry<Integer, ItemStack> entry : renderedSlots.entrySet()) {
                int slot = entry.getKey();
                if (slot >= 0 && slot < window.topSize) {
                    top[slot] = entry.getValue();
                }
            }
        }
        window.topItems = top;
    }

    void sendWindowItems(Player viewer, PacketGuiBackend.PacketWindow window) {
        List<com.github.retrooper.packetevents.protocol.item.ItemStack> items =
                new ArrayList<>(window.topSize + PacketGuiBackend.PLAYER_INVENTORY_SLOTS);
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
        ownerRef.debug(viewer, "common.gui.packet_send_items", ownerRef.windowFields(
                window,
                GuiDebugSupport.replacements("sent_state_id", stateId)
        ));
    }

    void returnCursor(Player viewer, PacketGuiBackend.PacketWindow window) {
        if (viewer == null || window == null || window.cursor == null || window.cursor.getType().isAir()) {
            return;
        }
        ItemStack returned = window.cursor.clone();
        Map<Integer, ItemStack> overflow =
                viewer.getInventory().addItem(returned);
        for (ItemStack leftover : overflow.values()) {
            if (leftover != null && !leftover.getType().isAir()) {
                viewer.getWorld().dropItemNaturally(viewer.getLocation(), leftover);
            }
        }
        window.cursor = null;
        ownerRef.debug(viewer, "common.gui.packet_cursor_return", ownerRef.windowFields(
                window,
                GuiDebugSupport.replacements(
                        "cursor_item_type", GuiDebugSupport.itemType(returned),
                        "cursor_item_amount", GuiDebugSupport.itemAmount(returned)
                )
        ));
    }

    void retireWindow(UUID viewerId, PacketGuiBackend.PacketWindow window) {
        if (ownerRef.windows.remove(viewerId, window)) {
            Player viewer = window.session.viewer();
            if (viewer != null && viewer.isOnline()) {
                ownerRef.returnCursor(viewer, window);
            }
        }
        GuiSessionRegistry registry = window.session.registry();
        if (registry != null) {
            registry.removeSession(viewerId, window.session);
        }
    }

    ItemStack toBukkitItem(com.github.retrooper.packetevents.protocol.item.ItemStack item) {
        if (item == null) {
            return null;
        }
        ItemStack converted = SpigotConversionUtil.toBukkitItemStack(item);
        return converted == null || converted.getType().isAir() ? null : converted;
    }

    Map<Integer, ItemStack> toBukkitItems( Optional<Map<Integer, com.github.retrooper.packetevents.protocol.item.ItemStack>> items) {
        if (items == null || items.isEmpty()) {
            return Map.of();
        }
        Map<Integer, ItemStack> converted = new LinkedHashMap<>();
        for (Map.Entry<Integer, com.github.retrooper.packetevents.protocol.item.ItemStack> entry
                : items.get().entrySet()) {
            if (entry.getKey() == null || entry.getKey() < 0) {
                continue;
            }
            ItemStack item = ownerRef.toBukkitItem(entry.getValue());
            converted.put(entry.getKey(), item == null ? new ItemStack(Material.AIR) : item);
        }
        return Map.copyOf(converted);
    }

    void applyPlayerInventoryChanges(Player viewer, int containerTopSize, Map<Integer, ItemStack> changedItems) {
        if (viewer == null || changedItems == null || changedItems.isEmpty()) {
            return;
        }
        for (Map.Entry<Integer, ItemStack> entry : changedItems.entrySet()) {
            int playerSlot = ownerRef.toPlayerInventorySlot(entry.getKey(), containerTopSize);
            if (playerSlot < 0) {
                continue;
            }
            ItemStack item = entry.getValue();
            viewer.getInventory().setItem(playerSlot,
                    item == null || item.getType().isAir() ? null : item.clone());
        }
    }

    int toPlayerInventorySlot(int rawSlot, int containerTopSize) {
        int offset = rawSlot - containerTopSize;
        if (offset < 0 || offset >= PacketGuiBackend.PLAYER_INVENTORY_SLOTS) {
            return -1;
        }
        return offset < 27 ? offset + 9 : offset - 27;
    }

    boolean isKnownClickRange(PacketGuiBackend.ClickSnapshot click, int containerTopSize) {
        if (click == null || containerTopSize <= 0
                || click.rawSlot() < 0
                || click.rawSlot() >= containerTopSize + PacketGuiBackend.PLAYER_INVENTORY_SLOTS) {
            return false;
        }
        return switch (click.clickType()) {
            case PICKUP, QUICK_MOVE, THROW -> click.button() == 0 || click.button() == 1;
            case SWAP -> (click.button() >= 0 && click.button() < 9) || click.button() == 40;
            case CLONE -> click.button() == 2;
            case PICKUP_ALL -> click.button() == 0;
            case QUICK_CRAFT, UNKNOWN -> false;
        };
    }

    boolean isCurrentWindow(UUID viewerId, PacketGuiBackend.PacketWindow window) {
        return viewerId != null && window != null && ownerRef.windows.get(viewerId) == window;
    }

    boolean isCurrentSession(UUID viewerId, PacketGuiBackend.PacketWindow window) {
        if (!ownerRef.isCurrentWindow(viewerId, window)) {
            return false;
        }
        GuiSessionRegistry registry = window.session.registry();
        return registry != null && registry.activeSession(viewerId) == window.session;
    }

    void playClickSound(GuiSession session, GuiTemplate.ResolvedSlot slot, GuiClickType clickType) {
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

    void onPlayerQuit(PlayerQuitEvent event) {
        Player viewer = event.getPlayer();
        PacketGuiBackend.PacketWindow window = ownerRef.windows.get(viewer.getUniqueId());
        if (window != null) {
            ownerRef.returnCursor(viewer, window);
            ownerRef.windows.remove(viewer.getUniqueId(), window);
            ownerRef.debug(viewer, "common.gui.packet_quit_cleanup", ownerRef.windowFields(window));
        }
    }

    void debug(Player viewer, String langKey, Map<String, ?> replacements) {
        GuiDebugSupport.log(ownerRef.plugin, viewer, langKey, replacements);
    }

    Map<String, Object> windowFields(PacketGuiBackend.PacketWindow window) {
        return ownerRef.windowFields(window, Map.of());
    }

    Map<String, Object> windowFields(PacketGuiBackend.PacketWindow window, Map<String, ?> fields) {
        return GuiDebugSupport.windowFields(
                window.windowId,
                window.lastSentStateId(),
                window.topSize,
                window.session,
                fields
        );
    }

}
