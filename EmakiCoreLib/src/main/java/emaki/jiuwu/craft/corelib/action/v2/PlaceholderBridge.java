package emaki.jiuwu.craft.corelib.action.v2;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Renders placeholders for a pipeline context.
 *
 * <p>Kept as a seam so the interpreter does not depend on the placeholder subsystem directly, which
 * also lets the compiler and its tests run without a live server.</p>
 */
public interface PlaceholderBridge {

    /**
     * Substitutes placeholders in {@code template}.
     *
     * @param context the context supplying values
     * @param template text possibly containing {@code %...%} placeholders
     * @return the rendered text, never {@code null}
     */
    @NotNull
    String render(@NotNull PipelineContext context, @Nullable String template);

    /** {@return a bridge that returns the template unchanged} */
    static @NotNull PlaceholderBridge noop() {
        return (context, template) -> template == null ? "" : template;
    }
}
