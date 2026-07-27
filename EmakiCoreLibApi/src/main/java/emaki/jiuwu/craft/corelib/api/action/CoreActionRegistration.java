package emaki.jiuwu.craft.corelib.api.action;

import java.util.function.BooleanSupplier;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Handle returned by CoreLib when an action registration is attempted.
 */
public final class CoreActionRegistration {

    private final String actionId;
    private final String ownerKey;
    private final String source;
    private final boolean registered;
    private final CoreActionResult result;
    private final BooleanSupplier unregisterer;

    public CoreActionRegistration(@Nullable String actionId,
            @Nullable String ownerKey,
            @Nullable String source,
            boolean registered,
            @Nullable CoreActionResult result,
            @Nullable BooleanSupplier unregisterer) {
        this.actionId = actionId == null ? "" : actionId;
        this.ownerKey = ownerKey == null ? "" : ownerKey;
        this.source = source == null ? "" : source;
        this.registered = registered;
        this.result = result == null ? CoreActionResult.ok() : result;
        this.unregisterer = unregisterer;
    }

    public static @NotNull CoreActionRegistration unavailable(@Nullable CoreActionResult result) {
        return new CoreActionRegistration("", "", "", false,
                result == null ? CoreActionResult.failure(CoreActionErrorType.INVALID_STATE, "EmakiCoreLib is unavailable.") : result,
                null);
    }

    public @NotNull String actionId() {
        return actionId;
    }

    public @NotNull String ownerKey() {
        return ownerKey;
    }

    public @NotNull String source() {
        return source;
    }

    public boolean registered() {
        return registered;
    }

    public @NotNull CoreActionResult result() {
        return result;
    }

    public boolean unregister() {
        return registered && unregisterer != null && unregisterer.getAsBoolean();
    }
}
