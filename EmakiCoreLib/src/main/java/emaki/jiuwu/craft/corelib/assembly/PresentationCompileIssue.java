package emaki.jiuwu.craft.corelib.assembly;

import emaki.jiuwu.craft.corelib.assembly.lore.SearchInsertValidationException;
import emaki.jiuwu.craft.corelib.text.Texts;

public record PresentationCompileIssue(String sourceId,
                                       String action,
                                       String targetPattern,
                                       SearchInsertValidationException.Reason reason,
                                       String detail) {

    public PresentationCompileIssue {
        sourceId = Texts.toStringSafe(sourceId);
        action = Texts.toStringSafe(action);
        targetPattern = Texts.toStringSafe(targetPattern);
        reason = reason == null ? SearchInsertValidationException.Reason.INVALID_SERIALIZED_CONFIG : reason;
        detail = Texts.toStringSafe(detail);
    }

    public boolean invalidRegex() {
        return reason == SearchInsertValidationException.Reason.INVALID_REGEX;
    }
}
