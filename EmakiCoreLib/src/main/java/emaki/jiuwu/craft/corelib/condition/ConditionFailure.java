package emaki.jiuwu.craft.corelib.condition;

import java.util.List;

import emaki.jiuwu.craft.corelib.api.text.Texts;

public record ConditionFailure(String message, List<String> actions, boolean blockOutput) {

    public ConditionFailure {
        message = Texts.toStringSafe(message);
        actions = actions == null ? List.of() : List.copyOf(actions);
    }

    public static ConditionFailure of(String message, List<String> actions, boolean blockOutput) {
        return new ConditionFailure(message, actions, blockOutput);
    }

    public boolean silent() {
        return Texts.isBlank(message) && actions.isEmpty() && !blockOutput;
    }
}
