package emaki.jiuwu.craft.corelib.script.host;

import java.util.List;

import org.jetbrains.annotations.NotNull;

public record ScriptLoadReport(int filesLoaded, int filesSkipped, @NotNull List<ScriptLoadError> errors) {

    public record ScriptLoadError(@NotNull String file, @NotNull String message) {
    }

    public ScriptLoadReport {
        errors = List.copyOf(errors);
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public static @NotNull ScriptLoadReport empty() {
        return new ScriptLoadReport(0, 0, List.of());
    }
}
