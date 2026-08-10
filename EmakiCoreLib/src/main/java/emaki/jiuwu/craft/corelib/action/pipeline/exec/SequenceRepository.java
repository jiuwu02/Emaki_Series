package emaki.jiuwu.craft.corelib.action.pipeline.exec;

import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.action.pipeline.compile.CompiledPipeline;
import emaki.jiuwu.craft.corelib.action.pipeline.compile.SequenceCatalog;

/** Named, compiled pipelines available to {@code run}. */
public interface SequenceRepository extends SequenceCatalog {

    /**
     * Resolves a compiled sequence.
     *
     * @param name sequence name
     * @return compiled sequence, or {@code null} if it disappeared after validation
     */
    @Nullable
    CompiledPipeline find(@Nullable String name);

    /** {@return an empty repository} */
    static @NotNull SequenceRepository empty() {
        return new SequenceRepository() {

            @Override
            public @Nullable CompiledPipeline find(@Nullable String name) {
                return null;
            }

            @Override
            public boolean contains(@Nullable String name) {
                return false;
            }

            @Override
            public @NotNull Set<String> requiredParameters(@Nullable String name) {
                return Set.of();
            }

            @Override
            public @NotNull Set<String> calls(@Nullable String name) {
                return Set.of();
            }

            @Override
            public @NotNull List<String> names() {
                return List.of();
            }
        };
    }
}
