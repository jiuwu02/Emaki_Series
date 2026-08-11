package emaki.jiuwu.craft.corelib.display;

import java.util.Objects;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;

/**
 * 一个物品展示实体的期望状态。
 *
 * @param key            身份
 * @param itemStack      展示的物品，内部会克隆并把数量固定为 1
 * @param baseLocation   基准位置
 * @param profile        位置与变换参数
 * @param layoutOffset   在 profile 偏移之上的额外布局偏移，用于多物品环形排布
 * @param transformation 变换覆盖；为空时取 profile 的变换
 * @param lifetimeTicks  存活时长(tick)。{@code 0} 表示常驻，大于 0 时由后端到期自动回收
 */
public record ItemDisplaySpec(DisplayKey key,
        ItemStack itemStack,
        Location baseLocation,
        DisplayGeometry.ItemProfile profile,
        DisplayGeometry.Vector3 layoutOffset,
        Transformation transformation,
        int lifetimeTicks) {

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

    /** 常驻、无变换覆盖的简写构造。 */
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
