package emaki.jiuwu.craft.corelib.action.v2.compile;

import java.util.List;

import org.jetbrains.annotations.NotNull;

/**
 * One validated pipeline, ready to execute.
 *
 * <p>Compilation happens at config load time, so the hot path never parses text. This is the single
 * largest structural difference from v1, which called {@code lineParser.parse()} on every execution.</p>
 *
 * @param source the original line, retained for diagnostics
 * @param nodes validated nodes in execution order
 * @param implicitSelfSource whether CoreLib prepended an implicit {@code self} source (decision Q4)
 */
public record CompiledPipeline(@NotNull String source,
        @NotNull List<ActionAst> nodes,
        boolean implicitSelfSource) {

    public CompiledPipeline {
        source = source == null ? "" : source;
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
    }

    /** {@return whether this pipeline has nothing to run} */
    public boolean empty() {
        return nodes.isEmpty();
    }
}
