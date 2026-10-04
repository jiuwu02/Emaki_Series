package emaki.jiuwu.craft.gem.legacy;

import java.util.List;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.legacy.LegacyTargetSpec;

public final class GemLegacyTargets {

    // socket_openers.* 与宝石识别条件中的 item_sources 是现行规范键，不参与旧格式扫描；
    // 宝石构造源的旧 item_sources 写法由 GemLoader 的 legacy 回退读取并以
    // loader.gem_legacy_item_sources 提示服主，转换器无法输出规范的 base_item_source 标量，
    // 故不再保留会产生重复块的转换规格。
    private static final List<LegacyTargetSpec> SPECS = List.of();

    private GemLegacyTargets() {
    }

    public static @NotNull List<LegacyTargetSpec> specs() {
        return SPECS;
    }
}
