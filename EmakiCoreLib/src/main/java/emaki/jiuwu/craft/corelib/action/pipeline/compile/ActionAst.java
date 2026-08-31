package emaki.jiuwu.craft.corelib.action.pipeline.compile;

import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.api.action.CoreStageKind;

public sealed interface ActionAst {

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

        public @NotNull Stage withKind(@NotNull CoreStageKind resolved) {
            return new Stage(id, resolved, arguments, positional, column);
        }
    }

    record Branch(@NotNull String condition,
            @NotNull List<ActionAst> thenBranch,
            @NotNull List<ActionAst> elseBranch,
            int column) implements ActionAst {

        public Branch {
            condition = condition == null ? "" : condition;
            thenBranch = thenBranch == null ? List.of() : List.copyOf(thenBranch);
            elseBranch = elseBranch == null ? List.of() : List.copyOf(elseBranch);
        }

        public boolean hasElse() {
            return !elseBranch.isEmpty();
        }
    }

    record Weighted(@NotNull List<Option> options, int column) implements ActionAst {

        public Weighted {
            options = options == null ? List.of() : List.copyOf(options);
        }

        public record Option(@NotNull String weight, @NotNull List<ActionAst> body) {

            public Option {
                weight = weight == null ? "" : weight;
                body = body == null ? List.of() : List.copyOf(body);
            }
        }
    }

    record SequenceCall(@NotNull String sequence,
            @NotNull Map<String, String> parameters,
            int column) implements ActionAst {

        public SequenceCall {
            sequence = sequence == null ? "" : sequence;
            parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        }
    }

    int column();
}
