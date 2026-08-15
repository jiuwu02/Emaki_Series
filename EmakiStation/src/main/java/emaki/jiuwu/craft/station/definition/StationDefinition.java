package emaki.jiuwu.craft.station.definition;

import java.util.List;

import emaki.jiuwu.craft.corelib.condition.ConditionBlock;
import emaki.jiuwu.craft.station.api.model.MaterialChannel;
import emaki.jiuwu.craft.station.api.model.OutputRouting;
import emaki.jiuwu.craft.station.api.model.ProgressMode;
import emaki.jiuwu.craft.station.api.model.StationView;
import emaki.jiuwu.craft.station.config.QueueSettings;

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

    public static final String DEFAULT_PREVIEW_LAYOUT = "station_preview";

    public static final String DEFAULT_QUEUE_LAYOUT = "station_queue";

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

    public ProgressMode progressMode() {
        return queueSettings.progressMode();
    }

    public boolean hasOwnPermission() {
        return !permission.isBlank();
    }

    public String effectivePermission(String fallback) {
        if (hasOwnPermission()) {
            return permission;
        }
        return fallback == null ? "" : fallback;
    }

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
