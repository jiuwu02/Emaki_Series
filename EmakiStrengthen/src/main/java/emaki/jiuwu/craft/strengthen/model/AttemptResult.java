package emaki.jiuwu.craft.strengthen.model;

import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.inventory.ItemStack;

public record AttemptResult(boolean success,
        String errorKey,
        Map<String, Object> replacements,
        AttemptPreview preview,
        ItemStack resultItem,
        int resultingStar,
        int resultingCrack) {

    public AttemptResult {
        replacements = replacements == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(replacements));
    }

    public static AttemptResult failure(String errorKey, AttemptPreview preview, Map<String, Object> replacements) {
        int star = preview == null ? 0 : preview.currentStar();
        int crack = preview == null || preview.state() == null ? 0 : preview.state().crackLevel();
        return new AttemptResult(false, errorKey, replacements, preview, null, star, crack);
    }
}
