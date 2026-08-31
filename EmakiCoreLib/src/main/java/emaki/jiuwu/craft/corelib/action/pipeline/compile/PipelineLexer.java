package emaki.jiuwu.craft.corelib.action.pipeline.compile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PipelineLexer {

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
            return Result.failed(new CompileDiagnostic("action.lex.unclosed_quote", "", "", 0,
                    quoteOpenColumn, String.valueOf(quote),
                    Map.of("quote", String.valueOf(quote)), List.of()));
        }
        if (escaping) {
            current.append('\\');
        }
        flush(tokens, current, tokenStart, quotedToken, keySplit);
        return Result.ok(List.copyOf(tokens));
    }

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

        public boolean successful() {
            return diagnostic == null;
        }
    }
}
