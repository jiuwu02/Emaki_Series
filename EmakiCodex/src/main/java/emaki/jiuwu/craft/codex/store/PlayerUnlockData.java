package emaki.jiuwu.craft.codex.store;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;

/**
 * Per-player persistent state for EmakiCodex: the set of recipe ids the player has
 * explicitly unlocked. Advancement completion state is not stored here because the
 * vanilla save already persists it.
 */
public final class PlayerUnlockData {

    private final String uuid;
    private final Set<String> unlockedRecipes = new LinkedHashSet<>();

    public PlayerUnlockData(String uuid) {
        this.uuid = uuid == null ? "" : uuid;
    }

    public PlayerUnlockData copy() {
        PlayerUnlockData copy = new PlayerUnlockData(uuid);
        copy.unlockedRecipes.addAll(unlockedRecipes);
        return copy;
    }

    public static PlayerUnlockData fromConfig(String uuid, YamlSection section) {
        PlayerUnlockData data = new PlayerUnlockData(uuid);
        if (section == null) {
            return data;
        }
        List<String> stored = section.getStringList("unlocked_recipes");
        if (stored != null) {
            for (String recipeId : stored) {
                if (Texts.isNotBlank(recipeId)) {
                    data.unlockedRecipes.add(recipeId.trim());
                }
            }
        }
        return data;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("uuid", uuid);
        result.put("unlocked_recipes", List.copyOf(unlockedRecipes));
        return result;
    }

    /** {@return true when the recipe was newly added} */
    public boolean unlock(String recipeId) {
        return Texts.isNotBlank(recipeId) && unlockedRecipes.add(recipeId.trim());
    }

    /** {@return true when the recipe was present and removed} */
    public boolean lock(String recipeId) {
        return Texts.isNotBlank(recipeId) && unlockedRecipes.remove(recipeId.trim());
    }

    public boolean isUnlocked(String recipeId) {
        return recipeId != null && unlockedRecipes.contains(recipeId.trim());
    }

    /** {@return an immutable snapshot of unlocked recipe ids} */
    public Set<String> unlockedRecipes() {
        return Set.copyOf(unlockedRecipes);
    }
}
