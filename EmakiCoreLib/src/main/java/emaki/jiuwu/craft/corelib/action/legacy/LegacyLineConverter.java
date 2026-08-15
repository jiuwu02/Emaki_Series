package emaki.jiuwu.craft.corelib.action.legacy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.text.Texts;

final class LegacyLineConverter {

    private final LegacyLineParser parser = new LegacyLineParser();

    @NotNull Result convert(@Nullable String line) {
        if (Texts.isBlank(line)) {
            return Result.notAnAction();
        }
        LegacyParsedActionLine parsed;
        try {
            parsed = parser.parse(1, line);
        } catch (LegacySyntaxException exception) {
            return Result.notAnAction();
        }
        if (parsed == null) {
            return Result.notAnAction();
        }

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

    private String render(String stageId, LegacyParsedActionLine parsed) {
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

    private List<String> orderedKeys(LegacyParsedActionLine parsed) {
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

    private boolean hasControl(LegacyParsedActionLine parsed) {
        return parsed.control() != null
                && (Texts.isNotBlank(parsed.control().condition())
                        || Texts.isNotBlank(parsed.control().chance())
                        || Texts.isNotBlank(parsed.control().delay())
                        || parsed.control().ignoreFailure());
    }

    private void appendControlGates(StringBuilder pipeline, LegacyParsedActionLine parsed) {
        if (parsed.control() == null) {
            return;
        }
        String chance = parsed.control().chance();
        if (Texts.isNotBlank(chance)) {

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

        if (value.indexOf('"') < 0) {
            return '"' + value + '"';
        }
        if (value.indexOf('\'') < 0) {
            return '\'' + value + '\'';
        }
        return '"' + value.replace("\"", "\\\"") + '"';
    }

    record Result(@NotNull Status status,
            @Nullable String oldId,
            @Nullable String pipeline,
            @Nullable String reason) {

        enum Status {

            CONVERTED,

            UNMAPPABLE,

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

    static @NotNull List<String> describeUnmappable() {
        return new ArrayList<>(List.of("loopsync", "loopasync", "cancelloop", "usetemplate"));
    }
}
