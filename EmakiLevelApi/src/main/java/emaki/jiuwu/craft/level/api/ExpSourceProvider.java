package emaki.jiuwu.craft.level.api;

import java.util.Collection;

import org.jetbrains.annotations.NotNull;

/** Supplies zero or more level experience grants for one gameplay trigger. */
public interface ExpSourceProvider {

    /** {@return a stable provider id, unique within the owning plugin} */
    @NotNull
    String id();

    /**
     * Evaluates one gameplay trigger.
     *
     * <p>The runtime invokes each registered provider exactly once per trigger, synchronously on the
     * context player's owner thread. Implementations should be fast and must not return {@code null}.
     */
    @NotNull
    Collection<ExpSourceGrant> provide(@NotNull ExpSourceContext context);
}
