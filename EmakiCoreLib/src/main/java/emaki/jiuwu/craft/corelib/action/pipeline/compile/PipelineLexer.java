package emaki.jiuwu.craft.corelib.action.v2.compile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Splits one pipeline line into tokens.
 *
 * <p>Quote and escape handling matches the v1 tokenizer so identical text lexes identically. What is
 * new: {@code |}, {@code [} and {@code ]} become their own tokens, and every token records its start
 * column.</p>
 *
 * <p>Outside quotes the three symbols separate tokens even without surrounding whitespace, so both
 * {@code a|b} and {@code a | b} lex the same way. Inside quotes they are ordinary characters, which is
 * what lets {@code text="a | b"} keep its pipe.</p>
 */
public final class PipelineLexer {

    /**
     * Lexes one line.
     *
     * @param line raw pipeline text
     * @return the lex result, holding either tokens or a diagnostic
     */
    public @NotNull Result lex(@Nullable String line) {
        String raw = line == null ? "" : line;
        List<PipelineToken> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int tokenStart = -1;
        int keySplit = -1;
        char quote = 0;
        boolean quotedToken = false;
        boolean escaping = false;
        int quoteOpenColumn = 0;

        for (int index = 0; index < raw.length(); index++) {
            char ch = raw.charAt(index);
            if (escaping) {
                current.append(unescape(ch));
                escaping = false;
                continue;
            }
            if (ch == '\\' && quote != 0) {
                escaping = true;
                continue;
            }
            if (quote != 0) {
                if (ch == quote) {
                    quote = 0;
                } else {
                    current.append(ch);
                }
                continue;
            }
            if (ch == '"' || ch == '\'') {
                quote = ch;
                quotedToken = true;
                quoteOpenColumn = index + 1;
                if (tokenStart < 0) {
                    tokenStart = index + 1;
                }
                continue;
            }
            if (ch == '|' && index + 1 < raw.length() && raw.charAt(index + 1) == '|') {
                // '||' is the logical-or operator inside a condition, never a stage separator.
                if (tokenStart < 0) {
                    tokenStart = index + 1;
                }
                current.append("||");
                index++;
                continue;
            }
            if (ch == '|' || ch == '[' || ch == ']') {
                tokenStart = flush(tokens, current, tokenStart, quotedToken, keySplit);
                quotedToken = false;
                keySplit = -1;
                tokens.add(new PipelineToken(symbolKind(ch), String.valueOf(ch), index + 1, false));
                continue;
            }
            if (Character.isWhitespace(ch)) {
                tokenStart = flush(tokens, current, tokenStart, quotedToken, keySplit);
                quotedToken = false;
                keySplit = -1;
                continue;
            }
            if (tokenStart < 0) {
                tokenStart = index + 1;
            }
            if (ch == '=' && keySplit < 0 && !current.isEmpty() && !isComparisonOperator(raw, index, current)) {
                keySplit = current.length();
            }
            current.append(ch);
        }

        if (quote != 0) {
            return Result.failed(new CompileDiagnostic("action.v2.lex.unclosed_quote", "", "", 0,
                    quoteOpenColumn, String.valueOf(quote),
                    Map.of("quote", String.valueOf(quote)), List.of()));
        }
        if (escaping) {
            current.append('\\');
        }
        flush(tokens, current, tokenStart, quotedToken, keySplit);
        return Result.ok(List.copyOf(tokens));
    }

    /**
     * Tells a {@code key=value} separator apart from a comparison operator.
     *
     * <p>A condition like {@code %c%==3} or {@code %a%>=5} must stay one bare value, while
     * {@code amount=20} must split. The distinguishing marks are a doubled {@code ==} and the
     * {@code !}, {@code >}, {@code <} that precede {@code =} in the other three operators.</p>
     *
     * @param raw the whole line
     * @param index index of the {@code =} being examined
     * @param current text accumulated so far in this token
     * @return whether this {@code =} belongs to a comparison operator
     */
    private static boolean isComparisonOperator(String raw, int index, StringBuilder current) {
        if (index + 1 < raw.length() && raw.charAt(index + 1) == '=') {
            return true;
        }
        char previous = current.charAt(current.length() - 1);
        return previous == '!' || previous == '>' || previous == '<' || previous == '=';
    }

    private static PipelineToken.Kind symbolKind(char ch) {
        return switch (ch) {
            case '|' -> PipelineToken.Kind.PIPE;
            case '[' -> PipelineToken.Kind.BRACKET_OPEN;
            default -> PipelineToken.Kind.BRACKET_CLOSE;
        };
    }

    private static int flush(List<PipelineToken> tokens,
            StringBuilder current,
            int tokenStart,
            boolean quoted,
            int keySplit) {
        if (current.isEmpty() && !quoted) {
            return -1;
        }
        tokens.add(new PipelineToken(PipelineToken.Kind.WORD, current.toString(),
                tokenStart < 0 ? 1 : tokenStart, quoted, keySplit));
        current.setLength(0);
        return -1;
    }

    private static char unescape(char value) {
        return switch (value) {
            case 'n' -> '\n';
            case 't' -> '\t';
            case '\\' -> '\\';
            case '"' -> '"';
            case '\'' -> '\'';
            default -> value;
        };
    }

    /**
     * Lex outcome.
     *
     * @param tokens the tokens, empty when lexing failed
     * @param diagnostic the problem, or {@code null} on success
     */
    public record Result(@NotNull List<PipelineToken> tokens, @Nullable CompileDiagnostic diagnostic) {

        public Result {
            tokens = tokens == null ? List.of() : List.copyOf(tokens);
        }

        static Result ok(List<PipelineToken> tokens) {
            return new Result(tokens, null);
        }

        static Result failed(CompileDiagnostic diagnostic) {
            return new Result(List.of(), diagnostic);
        }

        /** {@return whether lexing succeeded} */
        public boolean successful() {
            return diagnostic == null;
        }
    }
}
