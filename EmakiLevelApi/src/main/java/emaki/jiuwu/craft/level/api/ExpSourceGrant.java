package emaki.jiuwu.craft.level.api;

import org.jetbrains.annotations.NotNull;

/** One experience grant produced by an {@link ExpSourceProvider}. */
public record ExpSourceGrant(@NotNull String typeId, double amount, @NotNull String reason, boolean silent) {

    public ExpSourceGrant {
        if (typeId == null) {
            throw new NullPointerException("typeId");
        }
        reason = reason == null ? "" : reason;
    }
}
