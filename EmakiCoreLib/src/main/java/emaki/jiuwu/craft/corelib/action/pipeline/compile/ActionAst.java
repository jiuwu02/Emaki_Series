package emaki.jiuwu.craft.corelib.action.pipeline.compile;

import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.api.action.CoreStageKind;

/**
 * Parsed pipeline node.
 *
 * <p>The AST is a parse product, not a configuration form: a pipeline is written as one line and only
 * ever written that way (decision D3). Having a real tree is still what lets branches nest to any
 * depth and lets bracket pairing be checked, unlike sentinel-marker approaches that only simulate
 * block structure.</p>
 */
public sealed interface ActionAst {

    /**
     * One stage invocation.
     *
     * @param id normalised stage name
     * @param kind which table the id resolved to; unresolved during parsing
     * @param arguments named arguments in written order
     * @param positional bare values written without {@code name=}
     * @param column one-based column where the stage name starts
     */
    record Stage(@NotNull String id,
            @NotNull CoreStageKind kind,
            @NotNull Map<String, String> arguments,
            @NotNull List<String> positional,
            int column) implements ActionAst {

        public Stage {
            id = id == null ? "" : id;
            arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
            positional = positional == null ? List.of() : List.copyOf(positional);
        }

        /** {@return a copy with {@code kind} resolved} */
        public @NotNull Stage withKind(@NotNull CoreStageKind resolved) {
            return new Stage(id, resolved, arguments, positional, column);
        }
    }

    /**
     * A conditional branch.
     *
     * @param condition the condition text, evaluated per target when mid-pipeline
     * @param thenBranch stages to run when the condition holds
     * @param elseBranch stages to run otherwise, empty when {@code else} was omitted
     * @param column one-based column of the {@code if} keyword
     */
    record Branch(@NotNull String condition,
            @NotNull List<ActionAst> thenBranch,
            @NotNull List<ActionAst> elseBranch,
            int column) implements ActionAst {

        public Branch {
            condition = condition == null ? "" : condition;
            thenBranch = thenBranch == null ? List.of() : List.copyOf(thenBranch);
            elseBranch = elseBranch == null ? List.of() : List.copyOf(elseBranch);
        }

        /** {@return whether an {@code else} body was written} */
        public boolean hasElse() {
            return !elseBranch.isEmpty();
        }
    }

    /**
     * A sub-sequence call.
     *
     * @param sequence sequence name
     * @param parameters explicitly passed parameters; the callee sees only these
     * @param column one-based column of the {@code run} keyword
     */
    record SequenceCall(@NotNull String sequence,
            @NotNull Map<String, String> parameters,
            int column) implements ActionAst {

        public SequenceCall {
            sequence = sequence == null ? "" : sequence;
            parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        }
    }

    /** {@return the one-based column this node starts at} */
    int column();
}
