package emaki.jiuwu.craft.corelib.display;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import emaki.jiuwu.craft.corelib.api.text.MiniMessages;
import emaki.jiuwu.craft.corelib.api.text.Texts;

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
 * @param motion        运动轨迹。{@link DisplayMotion#NONE} 表示原地静止；
 *                      激活时由后端通过客户端插值驱动，实体本身不移动
 */
public record TextDisplaySpec(DisplayKey key,
        String text,
        Location baseLocation,
        DisplayGeometry.TextProfile profile,
        int lifetimeTicks,
        Set<UUID> viewers,
        DisplayMotion motion) {

    public TextDisplaySpec {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(baseLocation, "baseLocation");
        text = Texts.toStringSafe(text);
        profile = profile == null ? DisplayGeometry.TextProfile.defaults() : profile;
        baseLocation = baseLocation.clone();
        lifetimeTicks = Math.max(0, lifetimeTicks);
        viewers = viewers == null || viewers.isEmpty() ? Set.of() : Set.copyOf(viewers);
        motion = motion == null ? DisplayMotion.NONE : motion;
    }

    /** 常驻、非定向、无运动的简写构造。 */
    public TextDisplaySpec(DisplayKey key,
            String text,
            Location baseLocation,
            DisplayGeometry.TextProfile profile) {
        this(key, text, baseLocation, profile, 0, Set.of(), DisplayMotion.NONE);
    }

    /** 无运动的简写构造。 */
    public TextDisplaySpec(DisplayKey key,
            String text,
            Location baseLocation,
            DisplayGeometry.TextProfile profile,
            int lifetimeTicks,
            Set<UUID> viewers) {
        this(key, text, baseLocation, profile, lifetimeTicks, viewers, DisplayMotion.NONE);
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

    public boolean hasMotion() {
        return motion.isActive();
    }

    /**
     * 构建叠加了运动状态的变换。
     *
     * @param translation 位移偏移，{@code null} 视为零
     * @param scaleFactor 缩放系数，乘在 profile 原始缩放上
     */
    public Transformation transformation(DisplayGeometry.Vector3 translation, double scaleFactor) {
        DisplayGeometry.Vector3 offset = translation == null ? DisplayGeometry.Vector3.ZERO : translation;
        DisplayGeometry.Vector3 scale = profile.scale();
        return new Transformation(
                new Vector3f((float) offset.x(), (float) offset.y(), (float) offset.z()),
                new Quaternionf(),
                new Vector3f(
                        (float) (scale.x() * scaleFactor),
                        (float) (scale.y() * scaleFactor),
                        (float) (scale.z() * scaleFactor)
                ),
                new Quaternionf()
        );
    }
}
