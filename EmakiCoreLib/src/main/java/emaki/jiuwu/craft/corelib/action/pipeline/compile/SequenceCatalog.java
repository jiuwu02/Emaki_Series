package emaki.jiuwu.craft.corelib.action.pipeline.compile;

import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface SequenceCatalog {

    boolean contains(@Nullable String name);

    @NotNull
    Set<String> requiredParameters(@Nullable String name);

    @NotNull
    Set<String> calls(@Nullable String name);

    @NotNull
    List<String> names();

    static @NotNull SequenceCatalog empty() {
        return new SequenceCatalog() {

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
