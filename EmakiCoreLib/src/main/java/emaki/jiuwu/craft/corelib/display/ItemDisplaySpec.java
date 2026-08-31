package emaki.jiuwu.craft.corelib.display;

import java.util.Objects;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;

public record ItemDisplaySpec(DisplayKey key,
        ItemStack itemStack,
        Location baseLocation,
        DisplayGeometry.ItemProfile profile,
        DisplayGeometry.Vector3 layoutOffset,
        Transformation transformation,
        int lifetimeTicks) implements DisplayLifetimeSpec {

    public ItemDisplaySpec {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(itemStack, "itemStack");
        Objects.requireNonNull(baseLocation, "baseLocation");
        profile = profile == null ? DisplayGeometry.ItemProfile.defaults() : profile;
        itemStack = itemStack.clone();
        itemStack.setAmount(1);
        baseLocation = baseLocation.clone();
        layoutOffset = layoutOffset == null ? DisplayGeometry.Vector3.ZERO : layoutOffset;
        transformation = transformation == null ? profile.transformation() : transformation;
        lifetimeTicks = Math.max(0, lifetimeTicks);
    }

    public ItemDisplaySpec(DisplayKey key,
            ItemStack itemStack,
            Location baseLocation,
            DisplayGeometry.ItemProfile profile,
            DisplayGeometry.Vector3 layoutOffset) {
        this(key, itemStack, baseLocation, profile, layoutOffset, null, 0);
    }

    public String groupKey() {
        return key.groupKey();
    }

    public String runtimeKey() {
        return key.runtimeKey();
    }

    public boolean hasLifetime() {
        return lifetimeTicks > 0;
    }

    public Location displayLocation() {
        Location location = profile.applyOffset(baseLocation);
        if (location == null) {
            return null;
        }
        return location.add(layoutOffset.x(), layoutOffset.y(), layoutOffset.z());
    }
}
