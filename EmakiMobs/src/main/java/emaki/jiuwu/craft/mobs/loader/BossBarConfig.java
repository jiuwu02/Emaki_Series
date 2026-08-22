package emaki.jiuwu.craft.mobs.loader;

import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;

public record BossBarConfig(
        String title,
        BarColor color,
        BarStyle style,
        double range
) {}
