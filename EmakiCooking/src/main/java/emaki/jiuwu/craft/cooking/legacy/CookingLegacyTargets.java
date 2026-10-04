package emaki.jiuwu.craft.cooking.legacy;

import java.util.List;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.legacy.LegacyTargetSpec;

public final class CookingLegacyTargets {

    private static final String CONFIG = "config.yml";

    private static final List<LegacyTargetSpec> SPECS = List.of(
            // 工位判定键的旧扁平写法（嵌套 tool/spatula/container/input 节点为现行规范），
            // 转换产物 tool_matcher 等由 CookingMatchers 的 legacy 回退路径读取。
            LegacyTargetSpec.replaceAnd(CONFIG, "stations.chopping_board",
                    "tool_item_sources", "tool_matcher"),
            LegacyTargetSpec.replaceAnd(CONFIG, "stations.wok",
                    "spatula_item_sources", "spatula_matcher"),
            LegacyTargetSpec.replaceAnd(CONFIG, "stations.juicer",
                    "container_item_sources", "container_matcher"),
            LegacyTargetSpec.replaceAnd(CONFIG, "stations.steamer.moisture_rules[]",
                    "input_item_sources", "input_matcher"),
            // fuels[]/food_sources[] 与配方 input/ingredients/inputs/container 下的
            // item_sources 是现行规范键，不参与旧格式扫描；
            // 此处只迁移结果产物中旧的复数 item_sources 列表（唯一物品源）到规范单数 item_source。
            LegacyTargetSpec.replace("recipes", "result.*.outputs[]",
                    "item_sources", "item_source"));

    private CookingLegacyTargets() {
    }

    public static @NotNull List<LegacyTargetSpec> specs() {
        return SPECS;
    }
}
