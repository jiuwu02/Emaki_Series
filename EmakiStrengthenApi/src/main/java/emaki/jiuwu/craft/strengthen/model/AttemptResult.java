package emaki.jiuwu.craft.strengthen.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.bukkit.inventory.ItemStack;

public record AttemptResult(boolean success,
        String errorKey,
        Map<String, Object> replacements,
        AttemptPreview preview,
        ItemStack resultItem,
        int resultingStar,
        int resultingCrack,
        Set<Integer> newlyReachedStars) {

    public AttemptResult {
        replacements = replacements == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(replacements));
        newlyReachedStars = newlyReachedStars == null ? Set.of() : Set.copyOf(newlyReachedStars);
    }

    public static AttemptResult failure(String errorKey, AttemptPreview preview, Map<String, Object> replacements) {
        int star = preview == null ? 0 : preview.currentStar();
        int crack = preview == null || preview.state() == null ? 0 : preview.state().crackLevel();
        return new AttemptResult(false, errorKey, replacements, preview, null, star, crack, Set.of());
    }
}
