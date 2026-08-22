package emaki.jiuwu.craft.corelib.action.pipeline.exec;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.action.pipeline.compile.CompiledPipeline;
import emaki.jiuwu.craft.corelib.action.pipeline.compile.SequenceCatalog;

public interface SequenceRepository extends SequenceCatalog {

    @Nullable
    CompiledPipeline find(@Nullable String name);

    static @NotNull SequenceRepository empty() {
        return ConfiguredSequenceRepository.empty();
    }
}
