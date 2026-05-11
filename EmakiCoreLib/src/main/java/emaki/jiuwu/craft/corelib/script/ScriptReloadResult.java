package emaki.jiuwu.craft.corelib.script;

import java.util.List;

public record ScriptReloadResult(boolean success, int scriptCount, List<String> scripts, String message) {

    public ScriptReloadResult {
        scripts = scripts == null ? List.of() : List.copyOf(scripts);
        message = message == null ? "" : message;
    }

    public static ScriptReloadResult success(List<String> scripts) {
        return new ScriptReloadResult(true, scripts == null ? 0 : scripts.size(), scripts, "");
    }

    public static ScriptReloadResult failure(String message) {
        return new ScriptReloadResult(false, 0, List.of(), message);
    }
}
