package emaki.jiuwu.craft.gem.legacy;

import java.util.List;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.legacy.LegacyTargetSpec;

public final class GemLegacyTargets {

    private static final List<LegacyTargetSpec> SPECS = List.of(
            LegacyTargetSpec.replace("config.yml", "socket_openers.*", "item_sources"),
            LegacyTargetSpec.replace("gems", "", "item_sources").retainingLegacyKey());

    private GemLegacyTargets() {
    }

    public static @NotNull List<LegacyTargetSpec> specs() {
        return SPECS;
    }
}
