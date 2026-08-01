package emaki.jiuwu.craft.corelib.action.legacy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.action.ActionLineParser;
import emaki.jiuwu.craft.corelib.action.ActionSyntaxException;
import emaki.jiuwu.craft.corelib.action.ParsedActionLine;
import emaki.jiuwu.craft.corelib.text.Texts;

/**
 * Converts one old action line into one v2 pipeline line.
 *
 * <p>Reuses the old {@link ActionLineParser} rather than re-tokenising: the old tokeniser owns quoting
 * and escaping rules that a second implementation would drift from, and a line the old parser cannot
 * read is by definition not an old action line.</p>
 *
 * <p>One line in, one line out. Nothing here rewrites YAML structure, because the files being migrated
 * are heavily commented examples and a DOM round-trip would discard every comment.</p>
 */
final class LegacyLineConverter {

    private final ActionLineParser parser = new ActionLineParser();

    /**
     * Converts one line.
     *
     * @param line the raw old action line, without the YAML list dash
     * @return the outcome, never {@code null}
     */
    @NotNull Result convert(@Nullable String line) {
        if (Texts.isBlank(line)) {
            return Result.notAnAction();
        }
        ParsedActionLine parsed;
        try {
            parsed = parser.parse(1, line);
        } catch (ActionSyntaxException exception) {
            return Result.notAnAction();
        }
        if (parsed == null) {
            return Result.notAnAction();
        }
        // A bare token with no arguments and no control prefix is not an action line, whatever it is
        // named. Enum values such as `PROJECTILE` in an event list parse as an id because the old parser
        // lowercases it, and every action this migration handles carries at least one argument.
        if (parsed.arguments().isEmpty() && !hasControl(parsed)) {
            return Result.notAnAction();
        }
        String oldId = parsed.actionId();
        String unmappable = LegacyMappings.unmappableReason(oldId);
        if (unmappable != null) {
            return Result.unmappable(oldId, unmappable);
        }
        String stageId = LegacyMappings.stageId(oldId);
        if (stageId == null) {
            return Result.notAnAction();
        }
        return Result.converted(oldId, render(stageId, parsed));
    }

    /**
     * Builds the pipeline line.
     *
     * <p>No source segment is emitted. Decision Q4 made an omitted source mean {@code self}, which is
     * exactly what the old lines meant, so adding {@code self |} everywhere would be noise that also
     * enlarges the diff a server owner has to review.</p>
     */
    private String render(String stageId, ParsedActionLine parsed) {
        StringBuilder pipeline = new StringBuilder();
        appendControlGates(pipeline, parsed);
        pipeline.append(stageId);
        for (String key : orderedKeys(parsed)) {
            String value = parsed.arguments().get(key);
            if (value == null) {
                continue;
            }
            pipeline.append(' ').append(key).append('=')
                    .append(quote(LegacyMappings.rewritePlaceholders(value),
                            wasQuoted(parsed.rawLine(), key)));
        }
        return pipeline.toString();
    }

    /**
     * Recovers the argument order the author wrote.
     *
     * <p>{@link ParsedActionLine} carries an unordered {@code Map.copyOf} map, so iterating it directly
     * reorders arguments arbitrarily. That would turn every converted line into a reordered diff and
     * defeat the line-by-line review this migration depends on, so the order is read back off the raw
     * line. Any key not found there is appended afterwards so nothing is silently dropped.</p>
     */
    private List<String> orderedKeys(ParsedActionLine parsed) {
        List<String> ordered = new ArrayList<>(parsed.arguments().size());
        String raw = parsed.rawLine() == null ? "" : parsed.rawLine();
        char quote = 0;
        int tokenStart = 0;
        for (int index = 0; index <= raw.length(); index++) {
            char ch = index < raw.length() ? raw.charAt(index) : ' ';
            if (quote != 0) {
                if (ch == quote) {
                    quote = 0;
                }
                continue;
            }
            if (ch == '"' || ch == '\'') {
                quote = ch;
                continue;
            }
            if (!Character.isWhitespace(ch)) {
                continue;
            }
            String token = raw.substring(tokenStart, index);
            tokenStart = index + 1;
            int equals = token.indexOf('=');
            if (equals <= 0) {
                continue;
            }
            String key = token.substring(0, equals);
            if (parsed.arguments().containsKey(key) && !ordered.contains(key)) {
                ordered.add(key);
            }
        }
        for (String key : parsed.arguments().keySet()) {
            if (!ordered.contains(key)) {
                ordered.add(key);
            }
        }
        return ordered;
    }

    /** {@return whether the line carried any {@code @} control prefix} */
    private boolean hasControl(ParsedActionLine parsed) {
        return parsed.control() != null
                && (Texts.isNotBlank(parsed.control().condition())
                        || Texts.isNotBlank(parsed.control().chance())
                        || Texts.isNotBlank(parsed.control().delay())
                        || parsed.control().ignoreFailure());
    }

    /**
     * Emits the gate segments that the old {@code @} control prefixes become.
     *
     * <p>{@code @if} becomes {@code where}, never an {@code if [ ] else [ ]} branch: the old syntax has
     * no else arm, so generating a branch would invent structure the source never expressed. This also
     * means the converter never emits a bracket, so it cannot produce an unbalanced one.</p>
     *
     * <p>{@code @ignore_failure} has no counterpart and needs none: continuing past a failed stage is
     * already the default, and the old flag only suppressed an abort.</p>
     */
    private void appendControlGates(StringBuilder pipeline, ParsedActionLine parsed) {
        if (parsed.control() == null) {
            return;
        }
        String chance = parsed.control().chance();
        if (Texts.isNotBlank(chance)) {
            // Passed through verbatim, including values the v2 `chance` gate will reject. Both engines
            // read a bare number as a 0..1 fraction, so `@chance=5` was already an invalid 500% in v1 and
            // its action never ran. Rewriting it to `5%` would be the converter inventing a behaviour the
            // config never had; leaving it lets the compile check report it as the config bug it is.
            pipeline.append("chance ").append(chance.trim()).append(" | ");
        }
        String delay = parsed.control().delay();
        if (Texts.isNotBlank(delay)) {
            pipeline.append("after ").append(delay.trim()).append(" | ");
        }
        String condition = parsed.control().condition();
        if (Texts.isNotBlank(condition)) {
            pipeline.append("where ").append(LegacyMappings.rewritePlaceholders(condition.trim()))
                    .append(" | ");
        }
    }

    /**
     * Reports whether the author had quoted this argument's value.
     *
     * <p>The old tokeniser discards quotes, so the parsed value cannot tell. Recovering the original
     * choice keeps the rewritten line as close to the source as possible, which matters because this
     * migration is reviewed as a diff.</p>
     */
    private boolean wasQuoted(@Nullable String rawLine, String key) {
        if (rawLine == null) {
            return false;
        }
        int at = rawLine.indexOf(key + "=");
        while (at > 0 && !Character.isWhitespace(rawLine.charAt(at - 1))) {
            at = rawLine.indexOf(key + "=", at + 1);
        }
        if (at < 0) {
            return false;
        }
        int valueStart = at + key.length() + 1;
        return valueStart < rawLine.length()
                && (rawLine.charAt(valueStart) == '"' || rawLine.charAt(valueStart) == '\'');
    }

    /**
     * Quotes a value when the pipeline lexer needs it, or when the author had quoted it.
     *
     * <p>The lexer treats whitespace, {@code |}, {@code [} and {@code ]} as token boundaries outside
     * quotes, so a value containing any of them must be quoted or it would be silently truncated.
     * Author-quoted values stay quoted even when not strictly required, to keep the diff minimal.</p>
     */
    private String quote(String value, boolean wasQuoted) {
        if (value.isEmpty()) {
            return "\"\"";
        }
        boolean needsQuote = wasQuoted;
        for (int index = 0; index < value.length() && !needsQuote; index++) {
            char ch = value.charAt(index);
            if (Character.isWhitespace(ch) || ch == '|' || ch == '[' || ch == ']') {
                needsQuote = true;
            }
        }
        if (!needsQuote) {
            return value;
        }
        // Prefer double quotes; fall back to single when the value itself contains a double quote, so
        // that no escaping is needed and the result stays readable in YAML.
        if (value.indexOf('"') < 0) {
            return '"' + value + '"';
        }
        if (value.indexOf('\'') < 0) {
            return '\'' + value + '\'';
        }
        return '"' + value.replace("\"", "\\\"") + '"';
    }

    /**
     * What converting one line produced.
     *
     * @param status how the line was classified
     * @param oldId the old action id, {@code null} when the line was not an action
     * @param pipeline the converted pipeline line, only set when {@code status} is {@code CONVERTED}
     * @param reason why the line could not be converted, only set when {@code status} is
     *     {@code UNMAPPABLE}
     */
    record Result(@NotNull Status status,
            @Nullable String oldId,
            @Nullable String pipeline,
            @Nullable String reason) {

        /** How one line was classified. */
        enum Status {
            /** Rewritten to a pipeline line. */
            CONVERTED,
            /** A recognised old action whose v2 target stage does not exist. */
            UNMAPPABLE,
            /** Not an old action line at all; left untouched. */
            NOT_AN_ACTION
        }

        static Result converted(String oldId, String pipeline) {
            return new Result(Status.CONVERTED, oldId, pipeline, null);
        }

        static Result unmappable(String oldId, String reason) {
            return new Result(Status.UNMAPPABLE, oldId, null, reason);
        }

        static Result notAnAction() {
            return new Result(Status.NOT_AN_ACTION, null, null, null);
        }
    }

    /** {@return the old action ids this converter recognises, for diagnostics} */
    static @NotNull List<String> describeUnmappable() {
        return new ArrayList<>(List.of("loopsync", "loopasync", "cancelloop", "usetemplate"));
    }
}
