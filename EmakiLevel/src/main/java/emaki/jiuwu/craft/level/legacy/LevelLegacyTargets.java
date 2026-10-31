package emaki.jiuwu.craft.level.legacy;

import java.util.List;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.legacy.LegacyTargetSpec;

public final class LevelLegacyTargets {

    private static final List<LegacyTargetSpec> SPECS = List.of(
            LegacyTargetSpec.replace("sources", "sources.*.rules[]",
                    "result_item_sources", "item_sources"),
            // 等级类型的升级奖励曾把物品源写成 item_sources 列表 (且只允许一个来源),
            // 现在统一为单值 item_source; 旧写法仍在 LevelTypeConfig#ItemReward 里兼容读取.
            LegacyTargetSpec.replace("types", "upgrade.rewards.items[]",
                    "item_sources", "item_source"));

    private LevelLegacyTargets() {
    }

    public static @NotNull List<LegacyTargetSpec> specs() {
        return SPECS;
    }
}
