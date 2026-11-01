package emaki.jiuwu.craft.corelib.action.pipeline.compile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.action.CoreStageKind;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import java.util.Set;

public final class PipelineParser {

    public static final String IF = "if";

    public static final String ELSE = "else";

    public static final String RUN = "run";

    public static final String WEIGHT = "weight";

    public static final String ON_ERROR = "on_error";

    public static final String UNION = "union";

    public static final String PARSE_IF = "parse_if";

    public static final String RETRY = "retry";

    public static final String RETRY_DELAY = "retry_delay";

    private static final int MAX_ALIAS_DEPTH = 8;

    private static final Set<String> RESERVED_KEYWORDS = Set.of(
            IF, ELSE, RUN, WEIGHT, ON_ERROR, UNION, PARSE_IF);

    private final PipelineLexer lexer = new PipelineLexer();

    private final Map<String, String> aliases;

    public PipelineParser() {
        this(Map.of());
    }

    public PipelineParser(@Nullable Map<String, String> aliases) {
        this.aliases = aliases == null || aliases.isEmpty()
                ? Map.of()
                : Map.copyOf(aliases);
    }

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
        ExpandResult expanded = expandAliases(lexed.tokens());
        if (expanded.diagnostic() != null) {
            return Result.failed(expanded.diagnostic());
        }
        Cursor cursor = new Cursor(expanded.tokens());
        List<ActionAst> nodes = parseSequence(cursor, false);
        if (cursor.diagnostic != null) {
            return Result.failed(cursor.diagnostic);
        }
        if (cursor.hasNext()) {
            return Result.failed(CompileDiagnostic.at("action.parse.unexpected_token", cursor.peek()));
        }
        if (nodes.isEmpty()) {
            return Result.failed(CompileDiagnostic.at("action.parse.empty_pipeline", cursor.lastConsumed()));
        }
        return Result.ok(List.copyOf(nodes));
    }

    private ExpandResult expandAliases(List<PipelineToken> tokens) {
        if (aliases.isEmpty()) {
            return new ExpandResult(tokens, null);
        }
        return expandAliases(tokens, 0, null);
    }

    private ExpandResult expandAliases(List<PipelineToken> tokens, int depth, PipelineToken site) {
        if (depth > MAX_ALIAS_DEPTH) {
            return new ExpandResult(List.of(), CompileDiagnostic.at(
                    "action.parse.alias_cycle",
                    site == null ? tokens.get(0) : site,
                    Map.of("alias", site == null ? "" : Texts.lower(site.text()),
                            "max", MAX_ALIAS_DEPTH)));
        }
        List<PipelineToken> output = new ArrayList<>(tokens.size());
        PipelineToken expandAt = null;
        String expandName = null;
        for (PipelineToken token : tokens) {
            if (expandAt != null) {
                output.add(token);
                continue;
            }
            if (token.kind() != PipelineToken.Kind.WORD || token.isKeyValue() || token.quoted()) {
                output.add(token);
                continue;
            }
            String name = Texts.lower(token.text());
            if (RESERVED_KEYWORDS.contains(name) || !aliases.containsKey(name)) {
                output.add(token);
                continue;
            }
            expandAt = token;
            expandName = name;
        }
        if (expandAt == null) {
            return new ExpandResult(output, null);
        }
        PipelineLexer.Result lexed = lexer.lex(aliases.get(expandName));
        if (!lexed.successful()) {
            return new ExpandResult(List.of(), lexed.diagnostic());
        }
        if (lexed.tokens().isEmpty()) {
            return new ExpandResult(List.of(), CompileDiagnostic.at(
                    "action.parse.alias_empty", expandAt, Map.of("alias", expandName)));
        }
        List<PipelineToken> replaced = new ArrayList<>(output.size() + lexed.tokens().size());
        boolean placed = false;
        for (PipelineToken token : output) {
            if (!placed && token.equals(expandAt)) {
                for (PipelineToken fragmentToken : lexed.tokens()) {
                    replaced.add(shift(fragmentToken, token.column()));
                }
                placed = true;
                continue;
            }
            replaced.add(token);
        }
        return expandAliases(replaced, depth + 1, expandAt);
    }

    private static PipelineToken shift(PipelineToken token, int baseColumn) {
        return new PipelineToken(token.kind(), token.text(),
                token.column() + baseColumn - 1, token.quoted(), token.keySplit());
    }

    private record ExpandResult(List<PipelineToken> tokens, CompileDiagnostic diagnostic) {
    }

    private List<ActionAst> parseSequence(Cursor cursor, boolean insideBracket) {
        List<ActionAst> nodes = new ArrayList<>();
        while (cursor.hasNext() && cursor.diagnostic == null) {
            PipelineToken token = cursor.peek();
            if (token.kind() == PipelineToken.Kind.BRACKET_CLOSE) {
                if (!insideBracket) {
                    cursor.fail(CompileDiagnostic.at("action.parse.unmatched_bracket_close", token));
                }
                return nodes;
            }
            if (token.kind() == PipelineToken.Kind.PIPE) {
                cursor.fail(CompileDiagnostic.at("action.parse.empty_stage", token));
                return nodes;
            }
            if (token.kind() == PipelineToken.Kind.BRACKET_OPEN) {
                cursor.fail(CompileDiagnostic.at("action.parse.unexpected_bracket_open", token));
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
                    cursor.fail(CompileDiagnostic.at("action.parse.trailing_pipe", next));
                    return nodes;
                }
                continue;
            }
            if (next.kind() == PipelineToken.Kind.BRACKET_CLOSE) {
                if (!insideBracket) {
                    cursor.fail(CompileDiagnostic.at("action.parse.unmatched_bracket_close", next));
                }
                return nodes;
            }
            cursor.fail(CompileDiagnostic.at("action.parse.missing_pipe", next));
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
        if (WEIGHT.equals(name) && !head.quoted()) {
            return parseWeighted(cursor);
        }
        if (ON_ERROR.equals(name) && !head.quoted()) {
            cursor.fail(CompileDiagnostic.at("action.parse.on_error_without_body", head));
            return null;
        }
        if (UNION.equals(name) && !head.quoted()) {
            return parseUnion(cursor);
        }
        if (PARSE_IF.equals(name) && !head.quoted()) {
            return parseParseIf(cursor);
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
            cursor.fail(CompileDiagnostic.at("action.parse.branch_missing_condition", ifToken));
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

    private ActionAst parseUnion(Cursor cursor) {
        PipelineToken unionToken = cursor.next();
        List<ActionAst> sources = parseBracketBody(cursor, unionToken);
        if (sources == null) {
            return null;
        }
        if (sources.isEmpty()) {
            cursor.fail(CompileDiagnostic.at("action.parse.union_empty", unionToken));
            return null;
        }
        for (ActionAst source : sources) {
            if (!(source instanceof ActionAst.Stage)) {
                cursor.fail(CompileDiagnostic.at("action.parse.union_only_sources", unionToken));
                return null;
            }
        }
        return new ActionAst.Union(sources, unionToken.column());
    }

    private ActionAst parseParseIf(Cursor cursor) {
        PipelineToken parseIfToken = cursor.next();
        StringBuilder condition = new StringBuilder();
        while (cursor.hasNext() && cursor.peek().kind() == PipelineToken.Kind.WORD) {
            PipelineToken token = cursor.peek();
            if (token.kind() == PipelineToken.Kind.BRACKET_OPEN) {
                break;
            }
            if (!condition.isEmpty()) {
                condition.append(' ');
            }
            condition.append(cursor.next().text());
        }
        if (condition.isEmpty()) {
            cursor.fail(CompileDiagnostic.at("action.parse.parse_if_missing_condition", parseIfToken));
            return null;
        }
        List<ActionAst> body = parseBracketBody(cursor, parseIfToken);
        if (body == null) {
            return null;
        }
        String raw = condition.toString();
        if (containsPlaceholder(raw)) {
            cursor.fail(CompileDiagnostic.at("action.parse.parse_if_placeholder", parseIfToken,
                    Map.of("condition", raw)));
            return null;
        }
        Boolean evaluated = ExpressionEngine
                .evaluateBoolean(raw);
        if (evaluated == null) {
            cursor.fail(CompileDiagnostic.at("action.parse.parse_if_not_evaluable", parseIfToken,
                    Map.of("condition", raw)));
            return null;
        }
        return evaluated
                ? new ActionAst.Inline(body, parseIfToken.column())
                : new ActionAst.Inline(List.of(), parseIfToken.column());
    }

    private static boolean containsPlaceholder(String value) {
        int first = value.indexOf('%');
        return first >= 0 && value.indexOf('%', first + 1) > first;
    }

    private ActionAst parseWeighted(Cursor cursor) {
        PipelineToken weightToken = cursor.next();
        List<ActionAst.Weighted.Option> options = new ArrayList<>();
        while (cursor.hasNext() && cursor.peek().kind() == PipelineToken.Kind.WORD) {
            PipelineToken valueToken = cursor.peek();
            if (valueToken.isKeyValue()) {
                cursor.fail(CompileDiagnostic.at("action.parse.weight_named_argument", valueToken));
                return null;
            }
            cursor.next();
            List<ActionAst> body = parseBracketBody(cursor, valueToken);
            if (body == null) {
                return null;
            }
            options.add(new ActionAst.Weighted.Option(valueToken.text(), body));
        }
        if (options.isEmpty()) {
            cursor.fail(CompileDiagnostic.at("action.parse.weight_missing_option", weightToken));
            return null;
        }
        return new ActionAst.Weighted(options, weightToken.column());
    }

    private List<ActionAst> parseBracketBody(Cursor cursor, PipelineToken keyword) {
        if (!cursor.hasNext()) {
            cursor.fail(CompileDiagnostic.at("action.parse.branch_missing_body", keyword,
                    Map.of("keyword", keyword.text())));
            return null;
        }
        PipelineToken open = cursor.peek();
        if (open.kind() != PipelineToken.Kind.BRACKET_OPEN) {
            cursor.fail(CompileDiagnostic.at("action.parse.branch_missing_body", open,
                    Map.of("keyword", keyword.text())));
            return null;
        }
        cursor.next();
        List<ActionAst> body = parseSequence(cursor, true);
        if (cursor.diagnostic != null) {
            return null;
        }
        if (!cursor.hasNext()) {
            cursor.fail(CompileDiagnostic.at("action.parse.unclosed_bracket", open,
                    Map.of("open_column", open.column(), "keyword", keyword.text())));
            return null;
        }
        PipelineToken close = cursor.next();
        if (close.kind() != PipelineToken.Kind.BRACKET_CLOSE) {
            cursor.fail(CompileDiagnostic.at("action.parse.unclosed_bracket", close,
                    Map.of("open_column", open.column(), "keyword", keyword.text())));
            return null;
        }
        if (body.isEmpty()) {
            cursor.fail(CompileDiagnostic.at("action.parse.empty_branch_body", open,
                    Map.of("open_column", open.column())));
            return null;
        }
        return body;
    }

    private ActionAst parseSequenceCall(Cursor cursor) {
        PipelineToken runToken = cursor.next();
        if (!cursor.hasNext() || cursor.peek().kind() != PipelineToken.Kind.WORD) {
            cursor.fail(CompileDiagnostic.at("action.parse.run_missing_sequence", runToken));
            return null;
        }
        PipelineToken nameToken = cursor.next();
        if (nameToken.isKeyValue()) {
            cursor.fail(CompileDiagnostic.at("action.parse.run_missing_sequence", nameToken));
            return null;
        }
        String sequence = Texts.lower(nameToken.text());
        Map<String, String> parameters = new LinkedHashMap<>();
        while (cursor.hasNext() && cursor.peek().kind() == PipelineToken.Kind.WORD) {
            PipelineToken argument = cursor.peek();
            if (!argument.isKeyValue()) {
                cursor.fail(CompileDiagnostic.at("action.parse.run_positional_parameter", argument));
                return null;
            }
            cursor.next();
            String key = Texts.lower(argument.key());
            if (parameters.containsKey(key)) {
                cursor.fail(CompileDiagnostic.at("action.parse.duplicate_argument", argument,
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
            cursor.fail(CompileDiagnostic.at("action.parse.empty_stage", nameToken));
            return null;
        }
        if (nameToken.isKeyValue()) {
            cursor.fail(CompileDiagnostic.at("action.parse.stage_name_is_argument", nameToken));
            return null;
        }
        Map<String, String> arguments = new LinkedHashMap<>();
        Map<String, String> decorator = new LinkedHashMap<>();
        List<String> positional = new ArrayList<>();
        while (cursor.hasNext() && cursor.peek().kind() == PipelineToken.Kind.WORD) {
            PipelineToken token = cursor.peek();
            if (ELSE.equals(Texts.lower(token.text())) && !token.quoted()) {
                break;
            }
            if (ON_ERROR.equals(Texts.lower(token.text())) && !token.quoted()) {
                cursor.next();
                List<ActionAst> handler = parseBracketBody(cursor, token);
                if (handler == null) {
                    return null;
                }
                ActionAst.Stage stage = new ActionAst.Stage(id, CoreStageKind.ACTION,
                        arguments, positional, nameToken.column());
                return new ActionAst.OnError(List.of(stage), handler, nameToken.column());
            }
            cursor.next();
            if (token.isKeyValue()) {
                String key = Texts.lower(token.key());
                if (arguments.containsKey(key)) {
                    cursor.fail(CompileDiagnostic.at("action.parse.duplicate_argument", token,
                            Map.of("argument", key)));
                    return null;
                }
                if (RETRY.equals(key) || RETRY_DELAY.equals(key)) {
                    decorator.put(key, token.value());
                    continue;
                }
                arguments.put(key, token.value());
                continue;
            }
            positional.add(token.text());
        }
        int retryTimes = 0;
        long retryDelayTicks = 0L;
        String retryRaw = decorator.get(RETRY);
        if (!Texts.isBlank(retryRaw)) {
            retryTimes = ValueParsers.parseInt(retryRaw, -1);
        }
        String retryDelayRaw = decorator.get(RETRY_DELAY);
        if (!Texts.isBlank(retryDelayRaw)) {
            retryDelayTicks = ValueParsers.parseTicks(retryDelayRaw);
        }
        return new ActionAst.Stage(id, CoreStageKind.ACTION, arguments, positional,
                nameToken.column(), retryTimes, retryDelayTicks);
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

        public boolean successful() {
            return diagnostic == null && !blank;
        }
    }
}
