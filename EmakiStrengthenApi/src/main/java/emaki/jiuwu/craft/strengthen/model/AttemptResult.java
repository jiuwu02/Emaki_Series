package emaki.jiuwu.craft.strengthen.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.bukkit.inventory.ItemStack;

/**
 * Outcome of a committed strengthen attempt.
 *
 * <p>On success carries the produced {@code resultItem} and the resulting star;
 * on failure carries an {@code errorKey} plus message {@code replacements} and
 * the post-failure star/crack levels. {@code newlyReachedStars} lists milestone
 * stars first reached by this attempt.
 *
 * @param success           whether the attempt succeeded
 * @param errorKey          failure message key, may be {@code null} on success
 * @param replacements      message placeholder replacements; never {@code null}
 * @param preview           the preview the attempt was based on
 * @param resultItem        the produced item, or {@code null} on failure
 * @param resultingStar     the star level after the attempt
 * @param resultingCrack    the crack level after the attempt
 * @param newlyReachedStars milestone stars first reached; never {@code null}
 */
public record AttemptResult(boolean success,
        String errorKey,
        Map<String, Object> replacements,
        AttemptPreview preview,
        ItemStack resultItem,
        int resultingStar,
        int resultingCrack,
        Set<Integer> newlyReachedStars) {

    /** Canonical constructor; defensively copies the collection fields. */
    public AttemptResult {
        replacements = replacements == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(replacements));
        newlyReachedStars = newlyReachedStars == null ? Set.of() : Set.copyOf(newlyReachedStars);
    }

    /**
     * Builds a failure result, deriving star/crack levels from the preview.
     *
     * @param errorKey     the failure message key
     * @param preview      the preview the attempt was based on, may be
     *                     {@code null}
     * @param replacements message placeholder replacements
     * @return a failed {@link AttemptResult}
     */
    public static AttemptResult failure(String errorKey, AttemptPreview preview, Map<String, Object> replacements) {
        int star = preview == null ? 0 : preview.currentStar();
        int crack = preview == null || preview.state() == null ? 0 : preview.state().crackLevel();
        return new AttemptResult(false, errorKey, replacements, preview, null, star, crack, Set.of());
    }
}
