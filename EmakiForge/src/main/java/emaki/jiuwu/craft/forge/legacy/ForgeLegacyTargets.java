package emaki.jiuwu.craft.forge.legacy;

import java.util.List;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.legacy.LegacyTargetSpec;

public final class ForgeLegacyTargets {

    private static final List<LegacyTargetSpec> SPECS = List.of(

            LegacyTargetSpec.replace("recipes", "result.*.outputs[]", "item_sources", "item_source"));

    private ForgeLegacyTargets() {
    }

    public static @NotNull List<LegacyTargetSpec> specs() {
        return SPECS;
    }
}
