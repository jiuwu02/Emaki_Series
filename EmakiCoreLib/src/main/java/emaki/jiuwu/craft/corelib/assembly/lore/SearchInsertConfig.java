package emaki.jiuwu.craft.corelib.assembly.lore;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import emaki.jiuwu.craft.corelib.text.Texts;

public record SearchInsertConfig(String action,
                                 SearchMode mode,
                                 InsertPosition position,
                                 int matchIndex,
                                 String targetPattern,
                                 boolean ignoreCase,
                                 List<String> content,
                                 boolean inheritStyle,
                                 OnNotFoundPolicy onNotFound) {

    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .create();

    public SearchInsertConfig {
        action = Texts.trim(Texts.lower(action));
        mode = mode == null ? SearchMode.CONTAINS : mode;
        position = position == null ? InsertPosition.BELOW : position;
        targetPattern = Texts.toStringSafe(targetPattern);
        inheritStyle = inheritStyle;
        onNotFound = onNotFound == null ? OnNotFoundPolicy.SKIP : onNotFound;
        List<String> normalizedContent = new ArrayList<>();
        if (content != null) {
            for (String line : content) {
                normalizedContent.add(Texts.toStringSafe(line));
            }
        }
        content = normalizedContent.isEmpty() ? List.of() : List.copyOf(normalizedContent);
    }

    public static SearchInsertConfig fromAction(String action,
            String targetPattern,
            boolean ignoreCase,
            List<String> content,
            boolean inheritStyle,
            String onNotFound) {
        ParsedActionName parsed = ActionNameParser.parse(action);
        return new SearchInsertConfig(
                Texts.lower(action),
                parsed.mode(),
                parsed.position(),
                parsed.matchIndex(),
                targetPattern,
                ignoreCase,
                content,
                inheritStyle,
                OnNotFoundPolicy.fromKey(onNotFound)
        ).validate();
    }

    public SearchInsertConfig validate() {
        if (!ActionNameParser.isSearchInsertAction(action)) {
            throw new SearchInsertValidationException(
                    SearchInsertValidationException.Reason.INVALID_ACTION_NAME,
                    "Invalid lore search insert action name: " + action
            );
        }
        if (Texts.isBlank(targetPattern)) {
            throw new SearchInsertValidationException(
                    SearchInsertValidationException.Reason.INVALID_TARGET_PATTERN,
                    "Lore search insert target_pattern cannot be blank."
            );
        }
        if (content == null || content.isEmpty()) {
            throw new SearchInsertValidationException(
                    SearchInsertValidationException.Reason.INVALID_CONTENT,
                    "Lore search insert content cannot be empty."
            );
        }
        if (matchIndex <= 0) {
            throw new SearchInsertValidationException(
                    SearchInsertValidationException.Reason.INVALID_MATCH_INDEX,
                    "Lore search insert match index must be >= 1."
            );
        }
        if (onNotFound == null) {
            throw new SearchInsertValidationException(
                    SearchInsertValidationException.Reason.INVALID_ON_NOT_FOUND,
                    "Lore search insert on_not_found policy is required."
            );
        }
        if (mode == SearchMode.REGEX) {
            String normalizedPattern = LoreTextUtil.stripColorCodes(targetPattern);
            try {
                Pattern.compile(normalizedPattern);
            } catch (PatternSyntaxException exception) {
                throw new SearchInsertValidationException(
                        SearchInsertValidationException.Reason.INVALID_REGEX,
                        "Invalid lore search regex: " + exception.getMessage()
                );
            }
        }
        return this;
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public static SearchInsertConfig fromJson(String json) {
        if (Texts.isBlank(json)) {
            throw new SearchInsertValidationException(
                    SearchInsertValidationException.Reason.INVALID_SERIALIZED_CONFIG,
                    "Serialized lore search insert config is blank."
            );
        }
        try {
            SearchInsertConfig config = GSON.fromJson(json, SearchInsertConfig.class);
            if (config == null) {
                throw new SearchInsertValidationException(
                        SearchInsertValidationException.Reason.INVALID_SERIALIZED_CONFIG,
                        "Serialized lore search insert config is missing."
                );
            }
            return config.validate();
        } catch (JsonParseException exception) {
            throw new SearchInsertValidationException(
                    SearchInsertValidationException.Reason.INVALID_SERIALIZED_CONFIG,
                    "Failed to parse lore search insert config JSON: " + exception.getMessage()
            );
        }
    }
}
