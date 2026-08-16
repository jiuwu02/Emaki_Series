package emaki.jiuwu.craft.mobs.loader;

import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;

/**
 * Boss 血条配置，对应 YAML 中的 {@code boss_bar:} 节点。
 *
 * <p>配置示例：
 * <pre>
 * boss_bar:
 *   title: "&lt;red&gt;精英僵尸"
 *   color: RED
 *   style: SOLID
 *   range: 64
 * </pre>
 *
 * <p>{@code title} 支持 MiniMessage 格式；{@code color} 和 {@code style} 不区分大小写；
 * {@code range} 为玩家距离上限（单位：格），超出范围时自动隐藏血条。
 */
public record BossBarConfig(
        String title,
        BarColor color,
        BarStyle style,
        double range
) {}
