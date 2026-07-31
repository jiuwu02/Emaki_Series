package emaki.jiuwu.craft.corelib.action.v2.compile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageKind;
import emaki.jiuwu.craft.corelib.text.Texts;

/**
 * The one parser for pipeline text.
 *
 * <p>There is no second parsing path: a pipeline is always one line, so the earlier plan's split
 * between a structured-YAML compiler and an inline parser does not exist.</p>
 *
 * <p>Grammar:</p>
 * <pre>
 * line     := node ('|' node)*
 * node     := branch | stage
 * branch   := 'if' condition '[' line ']' ('else' '[' line ']')?
 * stage    := name arg*
 * arg      := key '=' value | bare_value
 * </pre>
 */
public final class PipelineParser {

    /** The branch keyword. */
    public static final String IF = "if";

    /** The alternative-branch keyword. */
    public static final String ELSE = "else";

    /** The sub-sequence call keyword. */
    public static final String RUN = "run";

    private final PipelineLexer lexer = new PipelineLexer();

    /**
     * Parses one pipeline line.
     *
     * @param line raw pipeline text
     * @return the parse result
     */
    public @NotNull Result parse(@Nullable String line) {
        String raw = line == null ? "" : line.trim();
        if (raw.isEmpty() || raw.startsWith("#")) {
            return Result.empty();
        }
        PipelineLexer.Result lexed = lexer.lex(raw);
        if (!lexed.successful()) {
            return Result.failed(lexed.diagnostic());
        }
        if (lexed.tokens().isEmpty()) {
            return Result.empty();
        }
        Cursor cursor = new Cursor(lexed.tokens());
        List<ActionAst> nodes = parseSequence(cursor, false);
        if (cursor.diagnostic != null) {
            return Result.failed(cursor.diagnostic);
        }
        if (cursor.hasNext()) {
            return Result.failed(CompileDiagnostic.at("action.v2.parse.unexpected_token", cursor.peek()));
        }
        if (nodes.isEmpty()) {
            return Result.failed(CompileDiagnostic.at("action.v2.parse.empty_pipeline", cursor.lastConsumed()));
        }
        return Result.ok(List.copyOf(nodes));
    }

    private List<ActionAst> parseSequence(Cursor cursor, boolean insideBracket) {
        List<ActionAst> nodes = new ArrayList<>();
        while (cursor.hasNext() && cursor.diagnostic == null) {
            PipelineToken token = cursor.peek();
            if (token.kind() == PipelineToken.Kind.BRACKET_CLOSE) {
                if (!insideBracket) {
                    cursor.fail(CompileDiagnostic.at("action.v2.parse.unmatched_bracket_close", token));
                }
                return nodes;
            }
            if (token.kind() == PipelineToken.Kind.PIPE) {
                cursor.fail(CompileDiagnostic.at("action.v2.parse.empty_stage", token));
                return nodes;
            }
            if (token.kind() == PipelineToken.Kind.BRACKET_OPEN) {
                cursor.fail(CompileDiagnostic.at("action.v2.parse.unexpected_bracket_open", token));
                return nodes;
            }
            ActionAst node = parseNode(cursor);
            if (node == null) {
                return nodes;
            }
            nodes.add(node);
            if (!cursor.hasNext()) {
                return nodes;
            }
            PipelineToken next = cursor.peek();
            if (next.kind() == PipelineToken.Kind.PIPE) {
                cursor.next();
                if (!cursor.hasNext()) {
                    cursor.fail(CompileDiagnostic.at("action.v2.parse.trailing_pipe", next));
                    return nodes;
                }
                continue;
            }
            if (next.kind() == PipelineToken.Kind.BRACKET_CLOSE) {
                if (!insideBracket) {
                    cursor.fail(CompileDiagnostic.at("action.v2.parse.unmatched_bracket_close", next));
                }
                return nodes;
            }
            cursor.fail(CompileDiagnostic.at("action.v2.parse.missing_pipe", next));
            return nodes;
        }
        return nodes;
    }

    private ActionAst parseNode(Cursor cursor) {
        PipelineToken head = cursor.peek();
        String name = Texts.lower(head.text());
        if (IF.equals(name) && !head.quoted()) {
            return parseBranch(cursor);
        }
        if (RUN.equals(name) && !head.quoted()) {
            return parseSequenceCall(cursor);
        }
        return parseStage(cursor);
    }

    private ActionAst parseBranch(Cursor cursor) {
        PipelineToken ifToken = cursor.next();
        StringBuilder condition = new StringBuilder();
        while (cursor.hasNext() && cursor.peek().kind() == PipelineToken.Kind.WORD) {
            if (!condition.isEmpty()) {
                condition.append(' ');
            }
            condition.append(cursor.next().text());
        }
        if (condition.isEmpty()) {
            cursor.fail(CompileDiagnostic.at("action.v2.parse.branch_missing_condition", ifToken));
            return null;
        }
        List<ActionAst> thenBranch = parseBracketBody(cursor, ifToken);
        if (thenBranch == null) {
            return null;
        }
        List<ActionAst> elseBranch = List.of();
        if (cursor.hasNext()
                && cursor.peek().kind() == PipelineToken.Kind.WORD
                && ELSE.equals(Texts.lower(cursor.peek().text()))
                && !cursor.peek().quoted()) {
            PipelineToken elseToken = cursor.next();
            List<ActionAst> parsed = parseBracketBody(cursor, elseToken);
            if (parsed == null) {
                return null;
            }
            elseBranch = parsed;
        }
        return new ActionAst.Branch(condition.toString(), thenBranch, elseBranch, ifToken.column());
    }

    private List<ActionAst> parseBracketBody(Cursor cursor, PipelineToken keyword) {
        if (!cursor.hasNext()) {
            cursor.fail(CompileDiagnostic.at("action.v2.parse.branch_missing_body", keyword,
                    Map.of("keyword", keyword.text())));
            return null;
        }
        PipelineToken open = cursor.peek();
        if (open.kind() != PipelineToken.Kind.BRACKET_OPEN) {
            cursor.fail(CompileDiagnostic.at("action.v2.parse.branch_missing_body", open,
                    Map.of("keyword", keyword.text())));
            return null;
        }
        cursor.next();
        List<ActionAst> body = parseSequence(cursor, true);
        if (cursor.diagnostic != null) {
            return null;
        }
        if (!cursor.hasNext()) {
            cursor.fail(CompileDiagnostic.at("action.v2.parse.unclosed_bracket", open,
                    Map.of("open_column", open.column(), "keyword", keyword.text())));
            return null;
        }
        PipelineToken close = cursor.next();
        if (close.kind() != PipelineToken.Kind.BRACKET_CLOSE) {
            cursor.fail(CompileDiagnostic.at("action.v2.parse.unclosed_bracket", close,
                    Map.of("open_column", open.column(), "keyword", keyword.text())));
            return null;
        }
        if (body.isEmpty()) {
            cursor.fail(CompileDiagnostic.at("action.v2.parse.empty_branch_body", open,
                    Map.of("open_column", open.column())));
            return null;
        }
        return body;
    }

    private ActionAst parseSequenceCall(Cursor cursor) {
        PipelineToken runToken = cursor.next();
        if (!cursor.hasNext() || cursor.peek().kind() != PipelineToken.Kind.WORD) {
            cursor.fail(CompileDiagnostic.at("action.v2.parse.run_missing_sequence", runToken));
            return null;
        }
        PipelineToken nameToken = cursor.next();
        if (nameToken.isKeyValue()) {
            cursor.fail(CompileDiagnostic.at("action.v2.parse.run_missing_sequence", nameToken));
            return null;
        }
        String sequence = Texts.lower(nameToken.text());
        Map<String, String> parameters = new LinkedHashMap<>();
        while (cursor.hasNext() && cursor.peek().kind() == PipelineToken.Kind.WORD) {
            PipelineToken argument = cursor.peek();
            if (!argument.isKeyValue()) {
                cursor.fail(CompileDiagnostic.at("action.v2.parse.run_positional_parameter", argument));
                return null;
            }
            cursor.next();
            String key = Texts.lower(argument.key());
            if (parameters.containsKey(key)) {
                cursor.fail(CompileDiagnostic.at("action.v2.parse.duplicate_argument", argument,
                        Map.of("argument", key)));
                return null;
            }
            parameters.put(key, argument.value());
        }
        return new ActionAst.SequenceCall(sequence, parameters, runToken.column());
    }

    private ActionAst parseStage(Cursor cursor) {
        PipelineToken nameToken = cursor.next();
        String id = Texts.lower(nameToken.text());
        if (id.isEmpty()) {
            cursor.fail(CompileDiagnostic.at("action.v2.parse.empty_stage", nameToken));
            return null;
        }
        if (nameToken.isKeyValue()) {
            cursor.fail(CompileDiagnostic.at("action.v2.parse.stage_name_is_argument", nameToken));
            return null;
        }
        Map<String, String> arguments = new LinkedHashMap<>();
        List<String> positional = new ArrayList<>();
        while (cursor.hasNext() && cursor.peek().kind() == PipelineToken.Kind.WORD) {
            PipelineToken token = cursor.peek();
            if (ELSE.equals(Texts.lower(token.text())) && !token.quoted()) {
                break;
            }
            cursor.next();
            if (token.isKeyValue()) {
                String key = Texts.lower(token.key());
                if (arguments.containsKey(key)) {
                    cursor.fail(CompileDiagnostic.at("action.v2.parse.duplicate_argument", token,
                            Map.of("argument", key)));
                    return null;
                }
                arguments.put(key, token.value());
                continue;
            }
            positional.add(token.text());
        }
        return new ActionAst.Stage(id, CoreStageKind.ACTION, arguments, positional, nameToken.column());
    }

    private static final class Cursor {

        private final List<PipelineToken> tokens;
        private int index;
        private CompileDiagnostic diagnostic;

        private Cursor(List<PipelineToken> tokens) {
            this.tokens = tokens;
        }

        private boolean hasNext() {
            return index < tokens.size();
        }

        private PipelineToken peek() {
            return tokens.get(index);
        }

        private PipelineToken next() {
            return tokens.get(index++);
        }

        private PipelineToken lastConsumed() {
            if (tokens.isEmpty()) {
                return null;
            }
            return tokens.get(Math.max(0, Math.min(index, tokens.size() - 1)));
        }

        private void fail(CompileDiagnostic value) {
            if (diagnostic == null) {
                diagnostic = value;
            }
        }
    }

    /**
     * Parse outcome.
     *
     * @param nodes parsed nodes in written order
     * @param diagnostic the problem, or {@code null} on success
     * @param blank whether the input was blank or a comment
     */
    public record Result(@NotNull List<ActionAst> nodes, @Nullable CompileDiagnostic diagnostic, boolean blank) {

        public Result {
            nodes = nodes == null ? List.of() : List.copyOf(nodes);
        }

        static Result ok(List<ActionAst> nodes) {
            return new Result(nodes, null, false);
        }

        static Result failed(CompileDiagnostic diagnostic) {
            return new Result(List.of(), diagnostic, false);
        }

        static Result empty() {
            return new Result(List.of(), null, true);
        }

        /** {@return whether parsing produced usable nodes} */
        public boolean successful() {
            return diagnostic == null && !blank;
        }
    }
}
