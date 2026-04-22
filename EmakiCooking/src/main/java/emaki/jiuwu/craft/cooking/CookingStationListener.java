package emaki.jiuwu.craft.cooking;

import java.lang.reflect.Method;

import emaki.jiuwu.craft.cooking.model.StationBreakContext;
import emaki.jiuwu.craft.cooking.model.StationInteraction;
import emaki.jiuwu.craft.cooking.service.ChoppingBoardRuntimeService;
import emaki.jiuwu.craft.cooking.service.GrinderRuntimeService;
import emaki.jiuwu.craft.cooking.service.SteamerRuntimeService;
import emaki.jiuwu.craft.cooking.service.WokRuntimeService;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

final class CookingStationListener implements Listener {

    private static final String CRAFTENGINE_INTERACT_EVENT = "net.momirealms.craftengine.bukkit.api.event.CustomBlockInteractEvent";
    private static final String CRAFTENGINE_BREAK_EVENT = "net.momirealms.craftengine.bukkit.api.event.CustomBlockBreakEvent";

    private final EmakiCookingPlugin plugin;
    private final ChoppingBoardRuntimeService choppingBoardRuntimeService;
    private final WokRuntimeService wokRuntimeService;
    private final GrinderRuntimeService grinderRuntimeService;
    private final SteamerRuntimeService steamerRuntimeService;
    private boolean reflectiveEventsRegistered;

    CookingStationListener(EmakiCookingPlugin plugin,
            ChoppingBoardRuntimeService choppingBoardRuntimeService,
            WokRuntimeService wokRuntimeService,
            GrinderRuntimeService grinderRuntimeService,
            SteamerRuntimeService steamerRuntimeService) {
        this.plugin = plugin;
        this.choppingBoardRuntimeService = choppingBoardRuntimeService;
        this.wokRuntimeService = wokRuntimeService;
        this.grinderRuntimeService = grinderRuntimeService;
        this.steamerRuntimeService = steamerRuntimeService;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null || event.getHand() == null) {
            return;
        }
        StationInteraction interaction = new StationInteraction(
                event.getPlayer(),
                event.getClickedBlock(),
                event.getAction() == Action.LEFT_CLICK_BLOCK,
                event.getAction() == Action.RIGHT_CLICK_BLOCK,
                event.getHand() == EquipmentSlot.HAND,
                event::setCancelled
        );
        dispatchInteraction(interaction);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        dispatchBreak(new StationBreakContext(
                event.getPlayer(),
                event.getBlock(),
                event::setCancelled
        ));
    }

    public void registerReflectiveEvents() {
        if (reflectiveEventsRegistered) {
            return;
        }
        reflectiveEventsRegistered = true;
        registerReflectiveEvent(CRAFTENGINE_INTERACT_EVENT, this::handleCraftEngineInteraction);
        registerReflectiveEvent(CRAFTENGINE_BREAK_EVENT, this::handleCraftEngineBreak);
    }

    private void dispatchInteraction(StationInteraction interaction) {
        if (choppingBoardRuntimeService.handleInteraction(interaction)) {
            return;
        }
        if (wokRuntimeService.handleInteraction(interaction)) {
            return;
        }
        if (steamerRuntimeService.handleInteraction(interaction)) {
            return;
        }
        grinderRuntimeService.handleInteraction(interaction);
    }

    private void dispatchBreak(StationBreakContext context) {
        if (choppingBoardRuntimeService.handleBreak(context)) {
            return;
        }
        if (wokRuntimeService.handleBreak(context)) {
            return;
        }
        if (steamerRuntimeService.handleBreak(context)) {
            return;
        }
        grinderRuntimeService.handleBreak(context);
    }

    private void handleCraftEngineInteraction(Object rawEvent) {
        Player player = invoke(rawEvent, "player", Player.class);
        Block block = invoke(rawEvent, "bukkitBlock", Block.class);
        Object action = invokeRaw(rawEvent, "action");
        Object hand = invokeRaw(rawEvent, "hand");
        if (player == null || block == null) {
            return;
        }
        StationInteraction interaction = new StationInteraction(
                player,
                block,
                "LEFT_CLICK".equals(String.valueOf(action)),
                "RIGHT_CLICK".equals(String.valueOf(action)),
                "MAIN_HAND".equals(String.valueOf(hand)),
                cancelled -> setCancelled(rawEvent, cancelled)
        );
        dispatchInteraction(interaction);
    }

    private void handleCraftEngineBreak(Object rawEvent) {
        Player player = invoke(rawEvent, "player", Player.class);
        Block block = invoke(rawEvent, "bukkitBlock", Block.class);
        if (player == null || block == null) {
            return;
        }
        dispatchBreak(new StationBreakContext(
                player,
                block,
                cancelled -> setCancelled(rawEvent, cancelled)
        ));
    }

    private void registerReflectiveEvent(String className, java.util.function.Consumer<Object> handler) {
        try {
            Class<?> rawEventClass = Class.forName(className);
            if (!Event.class.isAssignableFrom(rawEventClass)) {
                return;
            }
            @SuppressWarnings("unchecked")
            Class<? extends Event> eventClass = (Class<? extends Event>) rawEventClass;
            plugin.getServer().getPluginManager().registerEvent(
                    eventClass,
                    this,
                    EventPriority.HIGHEST,
                    (listener, event) -> handler.accept(event),
                    plugin,
                    true
            );
        } catch (Throwable ignored) {
        }
    }

    private <T> T invoke(Object target, String methodName, Class<T> returnType) {
        Object value = invokeRaw(target, methodName);
        return returnType.isInstance(value) ? returnType.cast(value) : null;
    }

    private Object invokeRaw(Object target, String methodName) {
        if (target == null || methodName == null || methodName.isBlank()) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void setCancelled(Object target, boolean cancelled) {
        if (target == null) {
            return;
        }
        try {
            Method method = target.getClass().getMethod("setCancelled", boolean.class);
            method.invoke(target, cancelled);
        } catch (Exception ignored) {
        }
    }
}
