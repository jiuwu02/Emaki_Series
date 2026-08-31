package emaki.jiuwu.craft.corelib.action.pipeline.compile;

import java.util.List;

import org.jetbrains.annotations.NotNull;

public record CompiledPipeline(@NotNull String source,
        @NotNull List<ActionAst> nodes,
        boolean implicitSelfSource) {

    public CompiledPipeline {
        source = source == null ? "" : source;
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
    }

    public boolean empty() {
        return nodes.isEmpty();
    }
}
