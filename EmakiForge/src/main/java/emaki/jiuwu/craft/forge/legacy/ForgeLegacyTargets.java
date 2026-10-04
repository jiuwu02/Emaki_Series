package emaki.jiuwu.craft.forge.legacy;

import java.util.List;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.legacy.LegacyTargetSpec;

public final class ForgeLegacyTargets {

    private static final List<LegacyTargetSpec> SPECS = List.of(
            // materials[]/blueprint_requirements[] 的 item_sources 是现行规范键，不参与旧格式扫描；
            // 此处只迁移 result.*.outputs[] 中旧的复数 item_sources 列表（唯一物品源）到规范单数 item_source。
            LegacyTargetSpec.replace("recipes", "result.*.outputs[]", "item_sources", "item_source"));

    private ForgeLegacyTargets() {
    }

    public static @NotNull List<LegacyTargetSpec> specs() {
        return SPECS;
    }
}
