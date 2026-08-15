package emaki.jiuwu.craft.corelib.script.exports;

import org.graalvm.polyglot.HostAccess;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;

public final class EmakiContextExport {

    private final CoreStageContext context;

    public EmakiContextExport(@NotNull CoreStageContext context) {
        if (context == null) {
            throw new IllegalArgumentException("context cannot be null");
        }
        this.context = context;
    }

    @HostAccess.Export
    public @Nullable Object getVariable(@NotNull String name) {
        if (name == null) {
            return null;
        }
        return context.variable(name).orElse(null);
    }

    @HostAccess.Export
    public boolean hasVariable(@NotNull String name) {
        if (name == null) {
            return false;
        }
        return context.variable(name).isPresent();
    }

    @Override
    public String toString() {
        return "EmakiContextExport{context=" + context.phase() + "}";
    }
}
