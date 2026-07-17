package emaki.jiuwu.craft.corelib.api.action;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Public SPI for actions registered into EmakiCoreLib by other plugins.
 */
public interface CoreAction {

    long DEFAULT_TIMEOUT_MILLIS = 30_000L;

    /** {@return the unique action id used in config action lines} */
    @NotNull
    String id();

    /** {@return a short human-readable description} */
    @NotNull
    String description();

    /** {@return a category used for listing and documentation} */
    @NotNull
    String category();

    /** {@return the action implementation version} */
    @NotNull
    default String version() {
        return "1.0.0";
    }

    /** {@return declared arguments accepted by this action} */
    @NotNull
    default List<CoreActionParameter> parameters() {
        return List.of();
    }

    /**
     * Allows dynamic arguments that are not declared in {@link #parameters()}.
     *
     * @param name argument name
     * @return true when the argument should be accepted
     */
    default boolean acceptsDynamicParameter(@Nullable String name) {
        return false;
    }

    /** {@return where this action may execute} */
    @NotNull
    default CoreActionExecutionMode executionMode() {
        return CoreActionExecutionMode.SYNC;
    }

    /**
     * Selects the scheduler ownership domain for one invocation.
     *
     * <p>The default is intentionally undeclared. CoreLib keeps legacy synchronous
     * actions compatible on ordinary Paper, but rejects them on Folia until they
     * explicitly choose a safe target.</p>
     */
    @NotNull
    default CoreActionExecutionTarget executionTarget(@NotNull CoreActionPlanningContext context) {
        return executionMode() == CoreActionExecutionMode.ASYNC_IO
                ? CoreActionExecutionTarget.asyncCompute()
                : CoreActionExecutionTarget.undeclared();
    }

    /** {@return timeout in milliseconds for this action} */
    default long timeoutMillis() {
        return DEFAULT_TIMEOUT_MILLIS;
    }

    /**
     * Optional custom validation after CoreLib has applied its base parameter checks.
     *
     * @param arguments resolved string arguments
     * @return validation result
     */
    @NotNull
    default CoreActionResult validate(@NotNull Map<String, String> arguments) {
        return CoreActionResult.ok();
    }

    /**
     * Stage-based validation hook invoked during CoreLib's owned planning dispatch.
     *
     * <p>The method name and return type do not imply automatic offloading. This
     * default calls {@link #validate(Map)} inline on the caller's current scheduler
     * thread and returns an already-completed stage. Overrides may return an
     * unfinished stage, but must not assume that completion or dependent callbacks
     * are moved back to a Bukkit/Paper/Folia ownership domain.</p>
     *
     * @param arguments resolved string arguments
     * @return a non-null stage; the default is already complete when this method returns
     */
    @NotNull
    default CompletionStage<CoreActionResult> validateAsync(@NotNull Map<String, String> arguments) {
        return CompletableFuture.completedFuture(validate(arguments));
    }

    /**
     * Executes the action.
     *
     * @param context execution context supplied by CoreLib
     * @param arguments resolved string arguments
     * @return action result
     */
    @NotNull
    CoreActionResult execute(@NotNull CoreActionContext context, @NotNull Map<String, String> arguments);

    /**
     * Stage-based execution hook invoked after CoreLib dispatches the invocation to
     * the domain selected by {@link #executionTarget(CoreActionPlanningContext)}.
     *
     * <p>This API does not offload work merely because it returns a stage. The
     * default calls {@link #execute(CoreActionContext, Map)} inline on that owned
     * scheduler thread and returns an already-completed stage. Overrides may return
     * an unfinished stage; any Bukkit/Paper/Folia access performed by later
     * continuations remains the implementation's responsibility and must use the
     * correct owner scheduler. CoreLib applies the declared delay before invocation
     * and observes {@link #timeoutMillis()} while awaiting completion.</p>
     *
     * @param context execution context supplied by CoreLib
     * @param arguments resolved string arguments
     * @return a non-null stage; the default is already complete when this method returns
     */
    @NotNull
    default CompletionStage<CoreActionResult> executeAsync(@NotNull CoreActionContext context,
            @NotNull Map<String, String> arguments) {
        return CompletableFuture.completedFuture(execute(context, arguments));
    }
}
