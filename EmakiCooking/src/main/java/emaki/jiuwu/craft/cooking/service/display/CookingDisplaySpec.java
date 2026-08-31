package emaki.jiuwu.craft.cooking.service.display;

import java.util.Objects;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;

import emaki.jiuwu.craft.cooking.model.StationCoordinates;
import emaki.jiuwu.craft.cooking.model.StationType;
import emaki.jiuwu.craft.cooking.service.CookingSettingsService;
import emaki.jiuwu.craft.corelib.display.DisplayGeometry;
import emaki.jiuwu.craft.corelib.display.ItemDisplaySpec;

public record CookingDisplaySpec(StationType stationType,
        StationCoordinates stationCoordinates,
        String displayKey,
        ItemStack itemStack,
        Location baseLocation,
        CookingSettingsService.DisplayAdjustmentProfile adjustment,
        CookingSettingsService.Vector3 layoutOffset,
        Transformation transformation) {

    private static final CookingSettingsService.Vector3 ZERO_OFFSET =
            new CookingSettingsService.Vector3(0D, 0D, 0D);

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

    public ItemDisplaySpec toCoreSpec() {
        return new ItemDisplaySpec(
                CookingDisplayKeys.of(stationType, stationCoordinates, displayKey),
                itemStack,
                baseLocation,
                toCoreProfile(adjustment),
                CookingTextDisplaySpec.toCoreVector(layoutOffset),
                transformation,
                0
        );
    }

    private static DisplayGeometry.ItemProfile toCoreProfile(
            CookingSettingsService.DisplayAdjustmentProfile profile) {
        if (profile == null) {
            return DisplayGeometry.ItemProfile.defaults();
        }
        return new DisplayGeometry.ItemProfile(
                CookingTextDisplaySpec.toCoreVector(profile.offset()),
                toCoreRotation(profile.rotation()),
                CookingTextDisplaySpec.toCoreVector(profile.scale())
        );
    }

    private static DisplayGeometry.RotationProfile toCoreRotation(
            CookingSettingsService.RotationProfile rotation) {
        if (rotation == null) {
            return DisplayGeometry.RotationProfile.none();
        }
        return new DisplayGeometry.RotationProfile(
                toCoreAxis(rotation.x()),
                toCoreAxis(rotation.y()),
                toCoreAxis(rotation.z())
        );
    }

    private static DisplayGeometry.AxisRotation toCoreAxis(CookingSettingsService.AxisRotation axis) {
        return axis == null
                ? DisplayGeometry.AxisRotation.fixed(0D)
                : new DisplayGeometry.AxisRotation(axis.min(), axis.max());
    }
}
