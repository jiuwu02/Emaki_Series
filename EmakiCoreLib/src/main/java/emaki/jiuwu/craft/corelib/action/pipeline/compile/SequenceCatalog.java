package emaki.jiuwu.craft.corelib.action.pipeline.compile;

import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * What the validator needs to know about named sequences.
 *
 * <p>Sequence definitions live in each module's own data directory (decision Q1) while stages are all
 * registered in CoreLib (requirement R1). This seam is how the validator checks a {@code run} target
 * without knowing where the definition came from.</p>
 */
public interface SequenceCatalog {

    /**
     * Tests whether a sequence exists.
     *
     * @param name sequence name
     * @return whether it is defined
     */
    boolean contains(@Nullable String name);

    /**
     * Reads the parameters a sequence requires.
     *
     * @param name sequence name
     * @return required parameter names; empty when the sequence is unknown or takes none
     */
    @NotNull
    Set<String> requiredParameters(@Nullable String name);

    /**
     * Reads the sequence names a sequence calls, used for cycle detection.
     *
     * @param name sequence name
     * @return names called directly by that sequence
     */
    @NotNull
    Set<String> calls(@Nullable String name);

    /** {@return every known sequence name, for diagnostics} */
    @NotNull
    List<String> names();

    /** {@return a catalog holding no sequences} */
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
