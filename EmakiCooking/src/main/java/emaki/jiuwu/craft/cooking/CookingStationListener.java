package emaki.jiuwu.craft.cooking;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import emaki.jiuwu.craft.cooking.api.event.CookingStationInteractEvent;
import emaki.jiuwu.craft.cooking.model.StationBreakContext;
import emaki.jiuwu.craft.cooking.model.StationInteraction;
import emaki.jiuwu.craft.cooking.model.StationInteractionType;
import emaki.jiuwu.craft.cooking.model.StationType;
import emaki.jiuwu.craft.cooking.service.ChoppingBoardRuntimeService;
import emaki.jiuwu.craft.cooking.service.CookingBlockMatcher;
import emaki.jiuwu.craft.cooking.service.CookingSettingsService;
import emaki.jiuwu.craft.cooking.service.FermentationBarrelRuntimeService;
import emaki.jiuwu.craft.cooking.service.GrinderRuntimeService;
import emaki.jiuwu.craft.cooking.service.JuicerRuntimeService;
import emaki.jiuwu.craft.cooking.service.OvenRuntimeService;
import emaki.jiuwu.craft.cooking.service.SteamerRuntimeService;
import emaki.jiuwu.craft.cooking.service.WokRuntimeService;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

final class CookingStationListener implements Listener {

    private static final long BREAK_DEDUPLICATION_WINDOW_MS = 500L;
    private static final long BREAK_DEDUPLICATION_PRUNE_MS = 5_000L;

    private final ChoppingBoardRuntimeService choppingBoardRuntimeService;
    private final WokRuntimeService wokRuntimeService;
    private final GrinderRuntimeService grinderRuntimeService;
    private final SteamerRuntimeService steamerRuntimeService;
    private final OvenRuntimeService ovenRuntimeService;
    private final JuicerRuntimeService juicerRuntimeService;
    private final FermentationBarrelRuntimeService fermentationBarrelRuntimeService;
    private final CookingBlockMatcher blockMatcher;
    private final CookingSettingsService settingsService;
    private final Map<String, Long> handledBreaks = new ConcurrentHashMap<>();

    CookingStationListener(ChoppingBoardRuntimeService choppingBoardRuntimeService,
            WokRuntimeService wokRuntimeService,
            GrinderRuntimeService grinderRuntimeService,
            SteamerRuntimeService steamerRuntimeService,
            OvenRuntimeService ovenRuntimeService,
            JuicerRuntimeService juicerRuntimeService,
            FermentationBarrelRuntimeService fermentationBarrelRuntimeService,
            CookingBlockMatcher blockMatcher,
            CookingSettingsService settingsService) {
        this.choppingBoardRuntimeService = choppingBoardRuntimeService;
        this.wokRuntimeService = wokRuntimeService;
        this.grinderRuntimeService = grinderRuntimeService;
        this.steamerRuntimeService = steamerRuntimeService;
        this.ovenRuntimeService = ovenRuntimeService;
        this.juicerRuntimeService = juicerRuntimeService;
        this.fermentationBarrelRuntimeService = fermentationBarrelRuntimeService;
        this.blockMatcher = blockMatcher;
        this.settingsService = settingsService;
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

    void dispatchInteraction(StationInteraction interaction) {
        StationType stationType = resolveStationType(interaction);
        if (isInteractionDisabled(stationType, interaction == null ? null : interaction.block())) {
            return;
        }
        fireInteractEvent(interaction, stationType);
        if (choppingBoardRuntimeService.handleInteraction(interaction)) {
            return;
        }
        if (wokRuntimeService.handleInteraction(interaction)) {
            return;
        }
        if (ovenRuntimeService.handleInteraction(interaction)) {
            return;
        }
        if (juicerRuntimeService.handleInteraction(interaction)) {
            return;
        }
        if (fermentationBarrelRuntimeService.handleInteraction(interaction)) {
            return;
        }
        if (steamerRuntimeService.handleInteraction(interaction)) {
            return;
        }
        grinderRuntimeService.handleInteraction(interaction);
    }






    private void fireInteractEvent(StationInteraction interaction, StationType stationType) {
        if (interaction == null) {
            return;
        }
        Block block = interaction.block();
        if (block == null || interaction.player() == null || stationType == null) {
            return;
        }
        StationInteractionType interactionType = interaction.type();
        Bukkit.getPluginManager().callEvent(new CookingStationInteractEvent(
                interaction.player(),
                block.getLocation(),
                stationType.folderName(),
                interactionType == null ? "" : interactionType.configKey()
        ));
    }







    private StationType resolveStationType(StationInteraction interaction) {
        if (blockMatcher == null) {
            return null;
        }
        for (StationType type : StationType.values()) {
            if (blockMatcher.matches(interaction, type)) {
                return type;
            }
        }
        return null;
    }

    private StationType resolveStationType(StationBreakContext context) {
        if (blockMatcher == null) {
            return null;
        }
        for (StationType type : StationType.values()) {
            if (blockMatcher.matches(context, type)) {
                return type;
            }
        }
        return null;
    }

    private boolean isInteractionDisabled(StationType stationType, Block block) {
        if (stationType == null || block == null || block.getWorld() == null || settingsService == null) {
            return false;
        }
        return settingsService.isInteractionDisabled(stationType, block.getWorld().getName());
    }

    void dispatchBreak(StationBreakContext context) {
        StationType stationType = resolveStationType(context);
        if (isInteractionDisabled(stationType, context == null ? null : context.block())) {
            return;
        }
        if (recentlyHandledBreak(context)) {
            return;
        }
        if (choppingBoardRuntimeService.handleBreak(context)) {
            rememberHandledBreak(context);
            return;
        }
        if (wokRuntimeService.handleBreak(context)) {
            rememberHandledBreak(context);
            return;
        }
        if (ovenRuntimeService.handleBreak(context)) {
            rememberHandledBreak(context);
            return;
        }
        if (juicerRuntimeService.handleBreak(context)) {
            rememberHandledBreak(context);
            return;
        }
        if (fermentationBarrelRuntimeService.handleBreak(context)) {
            rememberHandledBreak(context);
            return;
        }
        if (steamerRuntimeService.handleBreak(context)) {
            rememberHandledBreak(context);
            return;
        }
        if (grinderRuntimeService.handleBreak(context)) {
            rememberHandledBreak(context);
        }
    }

    private boolean recentlyHandledBreak(StationBreakContext context) {
        String key = breakKey(context);
        if (key == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        Long handledAt = handledBreaks.get(key);
        if (handledAt == null) {
            return false;
        }
        if (now - handledAt <= BREAK_DEDUPLICATION_WINDOW_MS) {
            return true;
        }
        handledBreaks.remove(key, handledAt);
        return false;
    }

    private void rememberHandledBreak(StationBreakContext context) {
        String key = breakKey(context);
        if (key == null) {
            return;
        }
        long now = System.currentTimeMillis();
        handledBreaks.put(key, now);
        pruneHandledBreaks(now);
    }

    private void pruneHandledBreaks(long now) {
        if (handledBreaks.size() < 512) {
            return;
        }
        handledBreaks.entrySet().removeIf(entry -> now - entry.getValue() > BREAK_DEDUPLICATION_PRUNE_MS);
    }

    private String breakKey(StationBreakContext context) {
        if (context == null) {
            return null;
        }
        Block block = context.block();
        if (block == null || block.getWorld() == null) {
            return null;
        }
        return block.getWorld().getUID() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }
}
