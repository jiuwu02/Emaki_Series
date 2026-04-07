package emaki.jiuwu.craft.corelib.assembly.lore;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.regex.PatternSyntaxException;

import emaki.jiuwu.craft.corelib.text.Texts;

public final class LoreSearchInsertProcessor {

    private final Map<SearchMode, LoreSearchMatcher> matchers = new LinkedHashMap<>();
    private final LoreInsertOperator insertOperator = new LoreInsertOperator();

    public LoreSearchInsertProcessor() {
        matchers.put(SearchMode.CONTAINS, new ContainsLoreSearchMatcher());
        matchers.put(SearchMode.EXACT, new ExactLoreSearchMatcher());
        matchers.put(SearchMode.REGEX, new RegexLoreSearchMatcher());
    }

    public LoreSearchInsertResult apply(List<String> loreLines,
            SearchInsertConfig config,
            UnaryOperator<String> templateRenderer) {
        List<String> currentLore = loreLines == null ? List.of() : List.copyOf(loreLines);
        if (config == null) {
            return new LoreSearchInsertResult(currentLore, LoreSearchInsertStatus.INVALID_CONFIG, "Missing config.");
        }
        try {
            config = config.validate();
        } catch (SearchInsertValidationException exception) {
            LoreSearchInsertStatus status = exception.reason() == SearchInsertValidationException.Reason.INVALID_REGEX
                    ? LoreSearchInsertStatus.INVALID_REGEX
                    : LoreSearchInsertStatus.INVALID_CONFIG;
            return new LoreSearchInsertResult(currentLore, status, exception.getMessage());
        }

        UnaryOperator<String> renderer = templateRenderer == null ? Texts::toStringSafe : templateRenderer;
        List<String> renderedContent = config.contentLines().stream()
                .map(Texts::toStringSafe)
                .map(renderer)
                .toList();

        LoreSearchMatcher matcher = matchers.getOrDefault(config.searchMode(), matchers.get(SearchMode.CONTAINS));
        List<Integer> matches;
        try {
            matches = matcher.matchAll(currentLore, config.searchPattern(), config.caseInsensitive());
        } catch (PatternSyntaxException exception) {
            return new LoreSearchInsertResult(currentLore, LoreSearchInsertStatus.INVALID_REGEX, exception.getMessage());
        }

        if (matches.isEmpty()) {
            return switch (config.onNotFoundPolicy()) {
                case SKIP ->
                    new LoreSearchInsertResult(currentLore, LoreSearchInsertStatus.SKIPPED_NOT_FOUND, "");
                case ERROR ->
                    new LoreSearchInsertResult(currentLore, LoreSearchInsertStatus.ERROR_NOT_FOUND, "");
                case APPEND_TO_END ->
                    new LoreSearchInsertResult(
                            insertOperator.appendToEnd(currentLore, renderedContent, config.styleInheritance()),
                            LoreSearchInsertStatus.APPENDED_TO_END,
                            ""
                    );
            };
        }

        int targetIndex = insertOperator.resolveTargetIndex(matches, config.matchIndex());
        List<String> updatedLore = switch (config.insertPosition()) {
            case ABOVE -> insertOperator.insertAbove(currentLore, targetIndex, renderedContent, config.styleInheritance());
            case BELOW -> insertOperator.insertBelow(currentLore, targetIndex, renderedContent, config.styleInheritance());
        };
        return new LoreSearchInsertResult(updatedLore, LoreSearchInsertStatus.APPLIED, "");
    }
}
