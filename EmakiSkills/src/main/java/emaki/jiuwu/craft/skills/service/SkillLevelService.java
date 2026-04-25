package emaki.jiuwu.craft.skills.service;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.skills.model.PlayerSkillLevelState;
import emaki.jiuwu.craft.skills.model.PlayerSkillProfile;
import emaki.jiuwu.craft.skills.model.SkillDefinition;

public final class SkillLevelService {

    private final PlayerSkillDataStore dataStore;

    public SkillLevelService(PlayerSkillDataStore dataStore) {
        this.dataStore = dataStore;
    }

    public int currentLevel(Player player, SkillDefinition definition) {
        if (player == null || definition == null) {
            return 1;
        }
        PlayerSkillProfile profile = dataStore.get(player);
        if (profile == null) {
            return 1;
        }
        PlayerSkillLevelState state = profile.skillLevels().get(definition.id());
        int configured = state == null ? 1 : state.level();
        return clampLevel(definition, configured);
    }

    public int maxLevel(SkillDefinition definition) {
        if (definition == null || definition.upgrade() == null || !definition.upgrade().enabled()) {
            return 1;
        }
        return Math.max(1, definition.upgrade().maxLevel());
    }

    public boolean isMaxLevel(Player player, SkillDefinition definition) {
        return currentLevel(player, definition) >= maxLevel(definition);
    }

    public int setLevel(Player player, SkillDefinition definition, int level) {
        if (player == null || definition == null || Texts.isBlank(definition.id())) {
            return 1;
        }
        PlayerSkillProfile profile = dataStore.get(player);
        if (profile == null) {
            return 1;
        }
        int clamped = clampLevel(definition, level);
        profile.skillLevels().put(definition.id(), new PlayerSkillLevelState(definition.id(), clamped));
        profile.markDirty();
        return clamped;
    }

    public int addLevel(Player player, SkillDefinition definition, int delta) {
        int current = currentLevel(player, definition);
        return setLevel(player, definition, current + delta);
    }

    private int clampLevel(SkillDefinition definition, int level) {
        return Numbers.clamp(Math.max(1, level), 1, maxLevel(definition));
    }
}
