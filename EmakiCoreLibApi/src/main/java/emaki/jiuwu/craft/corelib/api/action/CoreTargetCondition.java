package emaki.jiuwu.craft.corelib.api.action;

import org.jetbrains.annotations.NotNull;

/**
 * A named condition type that a target selector can evaluate.
 *
 * <p>Conditions are the typed counterpart of expression nodes. A selector's {@code condition} block
 * lists expressions, nested groups, or nodes carrying {@code type: <id>}; the last form is resolved
 * against the registered conditions, CoreLib's built-ins first and third-party registrations after
 * them.</p>
 *
 * <p>Implementations are registered through
 * {@link emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi#registerTargetCondition} and run on the
 * subject's owner thread. A condition reads the subject it is handed; it must not schedule work and
 * must not touch another entity's state.</p>
 */
public interface CoreTargetCondition {

    /** {@return the configuration value used as the node's type, matched case-insensitively} */
    @NotNull
    String id();

    /** {@return a short human-readable description, used by diagnostics} */
    @NotNull
    default String description() {
        return "";
    }

    /**
     * Tests one subject.
     *
     * @param subject   the candidate target, never {@code null}
     * @param context   read-only pipeline context, useful for the origin location
     * @param arguments the node's own fields, placeholders already substituted
     * @return the verdict, never {@code null}
     */
    @NotNull
    CoreTargetOutcome test(@NotNull CoreActionSubject subject,
            @NotNull CoreStageContext context,
            @NotNull CoreTargetConditionArguments arguments);
}