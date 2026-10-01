package emaki.jiuwu.craft.corelib.script.bridge;

import java.util.LinkedHashMap;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.action.ActionContext;

public final class ScriptPlaceholderContextExport {

    private final ActionContext context;

    public ScriptPlaceholderContextExport(@Nullable ActionContext context) {
        this.context = context;
    }

    @Nullable
    public ScriptPlayerReadExport getPlayer() {
        return context != null && context.player() != null
                ? new ScriptPlayerReadExport(context.player())
                : null;
    }

    @NotNull
    public String getPhase() {
        return context == null ? "default" : context.phase();
    }

    public boolean isSilent() {
        return context != null && context.silent();
    }

    @Nullable
    public String getPlaceholder(@Nullable String key) {
        return context == null ? null : context.placeholder(key);
    }

    @NotNull
    public Map<String, String> getPlaceholders() {
        return context == null ? Map.of() : new LinkedHashMap<>(context.placeholders());
    }

    @Override
    public String toString() {
        return "placeholderContext[phase=" + getPhase() + "]";
    }
}
