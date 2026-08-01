package emaki.jiuwu.craft.corelib.api.action;

import java.util.List;
import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Read-only context handed to a pipeline stage.
 *
 * <p>Three roles are always explicit: {@link #caster()} started the pipeline, {@link #targets()} is
 * what it currently flows over, and {@link #origin()} is the spatial reference point. There is no
 * mutable shared channel; CoreLib derives a new context for every change.</p>
 */
public interface CoreStageContext {

    /** {@return the plugin that triggered this pipeline} */
    @Nullable
    Plugin sourcePlugin();

    /** {@return who started this pipeline; {@code Absent} for console triggers} */
    @NotNull
    CoreActionSubject caster();

    /** {@return the current target flow; may be empty} */
    @NotNull
    List<CoreActionSubject> targets();

    /**
     * The target of the current iteration.
     *
     * <p>A stage with a non-{@code NONE} requirement is invoked once per target; this returns the one
     * it should act on.</p>
     *
     * @return the current target, or {@code Absent} when the flow is empty
     */
    @NotNull
    CoreActionSubject currentTarget();

    /** {@return the zero-based index of {@link #currentTarget()} within {@link #targets()}} */
    int currentTargetIndex();

    /** {@return the spatial reference point, defaulting to the caster's location} */
    @NotNull
    Location origin();

    /** {@return the phase name this pipeline belongs to} */
    @NotNull
    String phase();

    /** {@return whether player-facing feedback should be suppressed} */
    boolean silent();

    /**
     * Reads a typed context value.
     *
     * @param key context key
     * @param <T> value type
     * @return the value, empty when absent or mistyped
     */
    @NotNull
    <T> Optional<T> get(@NotNull CoreActionKey<T> key);

    /**
     * Reads a required typed context value.
     *
     * @param key context key
     * @param <T> value type
     * @return the value
     * @throws IllegalStateException when absent, with a diagnostic naming the missing key, the
     *         expected type and the keys this context actually holds
     */
    @NotNull
    <T> T require(@NotNull CoreActionKey<T> key);

    /** {@return the context keys currently present, for diagnostics} */
    @NotNull
    List<CoreActionKey<?>> presentKeys();

    /**
     * Reads a pipeline variable.
     *
     * @param name variable name without the {@code %var.} prefix
     * @return the raw string value, empty when unset
     */
    @NotNull
    Optional<String> variable(@Nullable String name);

    /**
     * Substitutes placeholders in {@code template} using this context.
     *
     * @param template text possibly containing {@code %...%} placeholders
     * @return the rendered text
     */
    @NotNull
    String render(@Nullable String template);
}
