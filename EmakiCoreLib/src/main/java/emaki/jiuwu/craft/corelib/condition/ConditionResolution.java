package emaki.jiuwu.craft.corelib.condition;

import java.util.ArrayList;
import java.util.List;

import emaki.jiuwu.craft.corelib.api.text.Texts;

public record ConditionResolution(boolean passed,
        List<String> messages,
        List<String> actions,
        boolean blockOutput) {

    public ConditionResolution {
        messages = messages == null ? List.of() : List.copyOf(messages);
        actions = actions == null ? List.of() : List.copyOf(actions);
    }

    public static ConditionResolution pass() {
        return new ConditionResolution(true, List.of(), List.of(), false);
    }

    public static ConditionResolution fail(List<String> messages, List<String> actions, boolean blockOutput) {
        return new ConditionResolution(false, messages, actions, blockOutput);
    }

    public boolean silent() {
        return !passed && messages.isEmpty() && actions.isEmpty();
    }

    public static List<String> mergeDistinct(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> merged = new ArrayList<>();
        for (String value : values) {
            String line = Texts.toStringSafe(value);
            if (Texts.isNotBlank(line) && !merged.contains(line)) {
                merged.add(line);
            }
        }
        return List.copyOf(merged);
    }
}
