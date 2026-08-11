package emaki.jiuwu.craft.corelib.api.itemsource;

import java.util.Locale;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A reference to one item from one item source, replacing the old {@code ItemSource} value object.
 *
 * <p>Equality is {@code kind} plus normalised {@code identifier}, so two references written
 * differently in config &mdash; {@code minecraft-IRON_INGOT} and {@code mc-iron_ingot} &mdash; compare
 * equal once parsed.
 *
 * <p>Identifier normalisation here is deliberately shallow: trim, lower-case with {@link Locale#ROOT},
 * and turn spaces into underscores. Anything kind-specific (rejecting namespaced vanilla ids, stripping
 * a provider's own prefix) belongs to the provider that owns the kind, not to this type.
 *
 * @param kind which item source this reference belongs to
 * @param identifier the item id within that source, normalised
 */
public record ItemSourceRef(@NotNull ItemSourceKind kind, @NotNull String identifier) {

    /**
     * Normalises the identifier.
     *
     * @throws IllegalArgumentException when {@code kind} is {@code null} or {@code identifier} is blank
     */
    public ItemSourceRef {
        if (kind == null) {
            throw new IllegalArgumentException("item source kind must not be null");
        }
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("item source identifier must not be blank");
        }
        identifier = identifier.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    /**
     * Creates a reference, returning {@code null} instead of throwing when the input is unusable.
     *
     * <p>Parsing config is the main caller here, and a malformed entry is an expected outcome there
     * rather than a programming error.
     *
     * @param kind the item source kind
     * @param identifier the raw identifier
     * @return the reference, or {@code null} when either part is missing
     */
    public static @Nullable ItemSourceRef orNull(@Nullable ItemSourceKind kind, @Nullable String identifier) {
        if (kind == null || identifier == null || identifier.isBlank()) {
            return null;
        }
        return new ItemSourceRef(kind, identifier);
    }

    /** {@return whether this reference points at a vanilla material} */
    public boolean vanilla() {
        return ItemSourceKind.VANILLA.equals(kind);
    }

    @Override
    public String toString() {
        return kind.key() + '/' + identifier;
    }
}
