package emaki.jiuwu.craft.level.legacy;

import java.util.List;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.legacy.LegacyTargetSpec;

public final class LevelLegacyTargets {

    private static final List<LegacyTargetSpec> SPECS = List.of(
            LegacyTargetSpec.replace("sources", "sources.*.rules[]", "result_item_sources"));

    private LevelLegacyTargets() {
    }

    public static @NotNull List<LegacyTargetSpec> specs() {
        return SPECS;
    }
}
