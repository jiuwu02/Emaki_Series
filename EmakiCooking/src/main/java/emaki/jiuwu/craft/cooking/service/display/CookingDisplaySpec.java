package emaki.jiuwu.craft.cooking.service.display;

import java.util.Objects;

import emaki.jiuwu.craft.cooking.model.StationCoordinates;
import emaki.jiuwu.craft.cooking.model.StationType;
import emaki.jiuwu.craft.cooking.service.CookingSettingsService;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;

public record CookingDisplaySpec(StationType stationType,
        StationCoordinates stationCoordinates,
        String displayKey,
        ItemStack itemStack,
        Location baseLocation,
        CookingSettingsService.DisplayAdjustmentProfile adjustment,
        CookingSettingsService.Vector3 layoutOffset,
        Transformation transformation) {

    private static final CookingSettingsService.Vector3 ZERO_OFFSET = new CookingSettingsService.Vector3(0D, 0D, 0D);

    public CookingDisplaySpec(StationType stationType,
            StationCoordinates stationCoordinates,
            String displayKey,
            ItemStack itemStack,
            Location baseLocation,
            CookingSettingsService.DisplayAdjustmentProfile adjustment,
            CookingSettingsService.Vector3 layoutOffset) {
        this(stationType, stationCoordinates, displayKey, itemStack, baseLocation, adjustment, layoutOffset, null);
    }

    public CookingDisplaySpec {
        Objects.requireNonNull(stationType, "stationType");
        Objects.requireNonNull(stationCoordinates, "stationCoordinates");
        Objects.requireNonNull(displayKey, "displayKey");
        Objects.requireNonNull(itemStack, "itemStack");
        Objects.requireNonNull(baseLocation, "baseLocation");
        Objects.requireNonNull(adjustment, "adjustment");
        itemStack = itemStack.clone();
        itemStack.setAmount(1);
        baseLocation = baseLocation.clone();
        layoutOffset = layoutOffset == null ? ZERO_OFFSET : layoutOffset;
        transformation = transformation == null ? adjustment.transformation() : transformation;
    }

    public String stationRuntimeKey() {
        return stationType.folderName() + ":" + stationCoordinates.runtimeKey();
    }

    public String runtimeKey() {
        return stationRuntimeKey() + ":" + displayKey;
    }

    public Location displayLocation() {
        Location location = adjustment.applyOffset(baseLocation);
        if (location == null) {
            return null;
        }
        return location.add(layoutOffset.x(), layoutOffset.y(), layoutOffset.z());
    }

}
