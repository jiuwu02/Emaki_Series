package emaki.jiuwu.craft.corelib.api.action;

import java.util.Map;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Typed view over one stage's arguments, after placeholders have been substituted.
 *
 * <p>Replaces the v1 {@code Map<String, String>} plus per-action {@code ActionParsers.parseDouble(...)}
 * boilerplate. Every accessor returns the declared default when the argument is absent, so stages do
 * not repeat null handling.</p>
 *
 * <p>Implemented by EmakiCoreLib; third-party code consumes it and does not implement it.</p>
 */
public interface CoreResolvedArguments {

    /** {@return the raw resolved values, keyed by lowercase argument name} */
    @NotNull
    Map<String, String> raw();

    /** {@return whether {@code name} was supplied with a non-blank value} */
    boolean has(@Nullable String name);

    /** {@return the raw string, or the declared default, or an empty string} */
    @NotNull
    String getString(@Nullable String name);

    /** {@return the raw string, or {@code fallback} when absent} */
    @NotNull
    String getString(@Nullable String name, @NotNull String fallback);

    /** {@return the value as an int, or {@code fallback} when absent or unparseable} */
    int getInt(@Nullable String name, int fallback);

    /** {@return the value as a double, or {@code fallback} when absent or unparseable} */
    double getDouble(@Nullable String name, double fallback);

    /** {@return the value as a boolean, or {@code fallback} when absent or unparseable} */
    boolean getBoolean(@Nullable String name, boolean fallback);

    /**
     * Reads a duration argument in ticks.
     *
     * @param name argument name
     * @param fallbackTicks value when absent or unparseable
     * @return duration in ticks, never negative
     */
    long getDurationTicks(@Nullable String name, long fallbackTicks);

    /**
     * Reads a probability argument as a fraction in {@code [0, 1]}.
     *
     * @param name argument name
     * @param fallback value when absent or unparseable
     * @return the chance as a fraction
     */
    double getChance(@Nullable String name, double fallback);

    /** {@return the value as an {@link EntityType}, empty when absent or unknown} */
    @NotNull
    Optional<EntityType> getEntityType(@Nullable String name);

    /** {@return the value as a {@link Material}, empty when absent or unknown} */
    @NotNull
    Optional<Material> getMaterial(@Nullable String name);

    /**
     * Evaluates an arithmetic expression argument.
     *
     * @param name argument name
     * @param fallback value when absent or not evaluable
     * @return the numeric result
     */
    double getExpression(@Nullable String name, double fallback);
}
