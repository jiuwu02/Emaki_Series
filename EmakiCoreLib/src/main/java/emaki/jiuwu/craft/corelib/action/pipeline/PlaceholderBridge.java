package emaki.jiuwu.craft.corelib.action.pipeline;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface PlaceholderBridge {

    @NotNull
    String render(@NotNull PipelineContext context, @Nullable String template);

    static @NotNull PlaceholderBridge noop() {
        return (context, template) -> template == null ? "" : template;
    }
}
