package emaki.jiuwu.craft.corelib.action.pipeline.registry;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.action.CoreStageKind;

/**
 * Result of looking a stage up by id.
 *
 * <p>The three cases are kept distinct because they need different diagnostics. Reporting "unknown
 * stage" when the real cause is a disabled owner sends the server owner hunting for a typo that does
 * not exist.</p>
 */
public sealed interface StageLookup {

    /** The stage exists and its owner is enabled. */
    record Found(@NotNull RegisteredStage entry) implements StageLookup {}

    /** The stage id was never registered in this table. */
    record Unknown(@NotNull String id, @NotNull CoreStageKind kind) implements StageLookup {}

    /** The stage was registered once, but its owner is currently disabled. */
    record OwnerDisabled(@NotNull String id,
            @NotNull CoreStageKind kind,
            @NotNull String ownerName) implements StageLookup {}

    /** {@return the live entry, or {@code null} for the other two cases} */
    default @Nullable RegisteredStage entryOrNull() {
        return this instanceof Found found ? found.entry() : null;
    }
}
