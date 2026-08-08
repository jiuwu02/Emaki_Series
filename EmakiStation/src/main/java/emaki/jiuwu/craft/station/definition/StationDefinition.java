package emaki.jiuwu.craft.station.definition;

import java.util.List;

import emaki.jiuwu.craft.corelib.condition.ConditionBlock;
import emaki.jiuwu.craft.station.api.model.MaterialChannel;
import emaki.jiuwu.craft.station.api.model.OutputRouting;
import emaki.jiuwu.craft.station.api.model.ProgressMode;
import emaki.jiuwu.craft.station.api.model.StationView;
import emaki.jiuwu.craft.station.config.QueueSettings;

/**
 * One loaded crafting station.
 *
 * <p>A station carries no recipe set of its own: membership is declared on the recipe side via
 * {@code station_ids}, and {@link StationRegistry} resolves it once both directories are loaded.
 *
 * @param id               the station id, which is also its file name
 * @param displayName      the configured display name, unrendered
 * @param layoutId         the catalog layout to open
 * @param previewLayoutId  the material-preview layout
 * @param queueLayoutId    the craft-queue layout
 * @param permission       the access permission, or an empty string to inherit the base node
 * @param queueSettings    the effective queue parameters for this station
 * @param allowPurchase    whether currency may extend this station's queue
 * @param backpackChannel  whether the inventory channel is enabled
 * @param storageChannel   whether the warehouse channel is enabled
 * @param defaultChannel   the channel a freshly opened GUI starts on
 * @param outputRouting    the default output destination
 * @param playerSwitchable whether players may change the output destination
 * @param condition        the gate evaluated before the GUI opens
 */
public record StationDefinition(String id,
        String displayName,
        String layoutId,
        String previewLayoutId,
        String queueLayoutId,
        String permission,
        QueueSettings queueSettings,
        boolean allowPurchase,
        boolean backpackChannel,
        boolean storageChannel,
        MaterialChannel defaultChannel,
        OutputRouting outputRouting,
        boolean playerSwitchable,
        ConditionBlock condition) {

    /** Layout id used when a station declares no {@code preview_layout}. */
    public static final String DEFAULT_PREVIEW_LAYOUT = "station_preview";

    /** Layout id used when a station declares no {@code queue_layout}. */
    public static final String DEFAULT_QUEUE_LAYOUT = "station_queue";

    /**
     * Creates a station with defensively copied collections.
     *
     * @param id                the station id
     * @param displayName       the display name; {@code null} becomes the id
     * @param layoutId          the catalog layout id; {@code null} becomes the id
     * @param previewLayoutId   the preview layout id; {@code null} becomes
     *                          {@link #DEFAULT_PREVIEW_LAYOUT}
     * @param queueLayoutId     the queue layout id; {@code null} becomes
     *                          {@link #DEFAULT_QUEUE_LAYOUT}
     * @param permission        the access permission; {@code null} becomes an empty string
     * @param queueSettings     the queue parameters; {@code null} becomes the shipped defaults
     * @param allowPurchase     whether currency may extend the queue
     * @param backpackChannel   whether the inventory channel is enabled
     * @param storageChannel    whether the warehouse channel is enabled
     * @param defaultChannel    the starting channel; {@code null} becomes
     *                          {@link MaterialChannel#BACKPACK}
     * @param outputRouting     the default output destination; {@code null} becomes
     *                          {@link OutputRouting#STORAGE_FIRST}
     * @param playerSwitchable  whether players may change the output destination
     * @param condition         the open gate; {@code null} becomes an empty block
     */
    public StationDefinition {
        displayName = displayName == null ? id : displayName;
        layoutId = layoutId == null || layoutId.isBlank() ? id : layoutId;
        previewLayoutId = previewLayoutId == null || previewLayoutId.isBlank()
                ? DEFAULT_PREVIEW_LAYOUT
                : previewLayoutId;
        queueLayoutId = queueLayoutId == null || queueLayoutId.isBlank()
                ? DEFAULT_QUEUE_LAYOUT
                : queueLayoutId;
        permission = permission == null ? "" : permission;
        queueSettings = queueSettings == null ? QueueSettings.defaults() : queueSettings.normalized();
        defaultChannel = defaultChannel == null ? MaterialChannel.BACKPACK : defaultChannel;
        outputRouting = outputRouting == null ? OutputRouting.STORAGE_FIRST : outputRouting;
        condition = condition == null ? ConditionBlock.empty() : condition;
    }

    /** {@return this station's progress mode} */
    public ProgressMode progressMode() {
        return queueSettings.progressMode();
    }

    /** {@return whether this station declares its own access permission} */
    public boolean hasOwnPermission() {
        return !permission.isBlank();
    }

    /**
     * Resolves the permission a player needs to open this station.
     *
     * @param fallback the node to require when this station declares none
     * @return the station's own node when configured, otherwise {@code fallback}
     */
    public String effectivePermission(String fallback) {
        if (hasOwnPermission()) {
            return permission;
        }
        return fallback == null ? "" : fallback;
    }

    /**
     * Resolves the channel a session should start on, given what is actually available.
     *
     * <p>The configured default is only honoured when that channel is enabled; otherwise the other
     * enabled channel is used. A station with neither channel enabled cannot be opened and is rejected
     * at load time, so this never has to invent a third answer.
     *
     * @param storageUsable whether the warehouse is reachable right now
     * @return the channel to start on
     */
    public MaterialChannel startingChannel(boolean storageUsable) {
        boolean storageReady = storageChannel && storageUsable;
        if (defaultChannel == MaterialChannel.STORAGE && storageReady) {
            return MaterialChannel.STORAGE;
        }
        if (defaultChannel == MaterialChannel.BACKPACK && backpackChannel) {
            return MaterialChannel.BACKPACK;
        }
        return backpackChannel ? MaterialChannel.BACKPACK : MaterialChannel.STORAGE;
    }

    /**
     * Builds an API view of this station.
     *
     * @param recipeIds the recipe ids resolved for this station
     * @return the view
     */
    public StationView toView(List<String> recipeIds) {
        return new StationView(id,
                displayName,
                layoutId,
                permission,
                recipeIds,
                queueSettings.baseLength(),
                queueSettings.progressMode(),
                outputRouting,
                backpackChannel,
                storageChannel);
    }
}
