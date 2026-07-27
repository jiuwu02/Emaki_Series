package emaki.jiuwu.craft.corelib.display;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.util.Transformation;

import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.corelib.text.Texts;

import net.kyori.adventure.text.Component;

/**
 * 一个文本展示实体的期望状态。
 *
 * @param key           身份
 * @param text          MiniMessage 文本；空白表示移除该实体
 * @param baseLocation  基准位置，实际位置为其加上 profile 偏移
 * @param profile       渲染参数
 * @param lifetimeTicks 存活时长(tick)。{@code 0} 表示常驻，由调用方显式移除；
 *                      大于 0 时由后端实现在到期后自动回收
 * @param viewers       定向可见的玩家。为空表示按距离的空间可见性；
 *                      非空时仅这些玩家可见，且**只有发包后端支持**，
 *                      真实体后端会忽略该字段并对所有人可见
 */
public record TextDisplaySpec(DisplayKey key,
        String text,
        Location baseLocation,
        DisplayGeometry.TextProfile profile,
        int lifetimeTicks,
        Set<UUID> viewers) {

    public TextDisplaySpec {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(baseLocation, "baseLocation");
        text = Texts.toStringSafe(text);
        profile = profile == null ? DisplayGeometry.TextProfile.defaults() : profile;
        baseLocation = baseLocation.clone();
        lifetimeTicks = Math.max(0, lifetimeTicks);
        viewers = viewers == null || viewers.isEmpty() ? Set.of() : Set.copyOf(viewers);
    }

    /** 常驻、非定向的简写构造。 */
    public TextDisplaySpec(DisplayKey key,
            String text,
            Location baseLocation,
            DisplayGeometry.TextProfile profile) {
        this(key, text, baseLocation, profile, 0, Set.of());
    }

    public String groupKey() {
        return key.groupKey();
    }

    public String runtimeKey() {
        return key.runtimeKey();
    }

    public boolean hasText() {
        return Texts.isNotBlank(text);
    }

    public boolean hasLifetime() {
        return lifetimeTicks > 0;
    }

    public boolean isTargeted() {
        return !viewers.isEmpty();
    }

    public Location displayLocation() {
        return new Location(
                baseLocation.getWorld(),
                baseLocation.getX() + profile.offset().x(),
                baseLocation.getY() + profile.offset().y(),
                baseLocation.getZ() + profile.offset().z()
        );
    }

    public Component component() {
        return MiniMessages.parse(text);
    }

    /** {@return 组件的类型擦除形式，供发包后端跨版本传递} */
    public Object componentObject() {
        return component();
    }

    public Transformation transformation() {
        return profile.transformation();
    }
}
