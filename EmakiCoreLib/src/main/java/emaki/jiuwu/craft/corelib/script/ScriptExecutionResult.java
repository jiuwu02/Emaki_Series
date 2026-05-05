package emaki.jiuwu.craft.corelib.script;

import java.util.Map;

public record ScriptExecutionResult(boolean success,
        boolean skipped,
        String message,
        Object returnValue,
        Map<String, Object> output,
        Throwable throwable) {

    public ScriptExecutionResult {
        output = output == null ? Map.of() : Map.copyOf(output);
    }

    public static ScriptExecutionResult success(Object returnValue, String message) {
        return new ScriptExecutionResult(true, false, message, returnValue, Map.of(), null);
    }

    public static ScriptExecutionResult success(Object returnValue, String message, Map<String, Object> output) {
        return new ScriptExecutionResult(true, false, message, returnValue, output, null);
    }

    public static ScriptExecutionResult skipped(String message) {
        return new ScriptExecutionResult(true, true, message, null, Map.of(), null);
    }

    public static ScriptExecutionResult failure(String message) {
        return new ScriptExecutionResult(false, false, message, null, Map.of(), null);
    }

    public static ScriptExecutionResult failure(String message, Throwable throwable) {
        return new ScriptExecutionResult(false, false, message, null, Map.of(), throwable);
    }
}
