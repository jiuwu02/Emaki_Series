package emaki.jiuwu.craft.skills.service;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.skills.model.PlayerSkillProfile;

public final class CastModeService {

    private final JavaPlugin plugin;
    private final PlayerSkillDataStore dataStore;

    public CastModeService(JavaPlugin plugin, PlayerSkillDataStore dataStore) {
        this.plugin = plugin;
        this.dataStore = dataStore;
    }

    public boolean isCastModeEnabled(Player player) {
        if (player == null) {
            return false;
        }
        PlayerSkillProfile profile = dataStore.get(player);
        return profile != null && profile.castModeEnabled();
    }

    public void setCastMode(Player player, boolean enabled) {
        if (player == null) {
            return;
        }
        PlayerSkillProfile profile = dataStore.get(player);
        if (profile == null) {
            return;
        }
        profile.setCastModeEnabled(enabled);
        profile.markDirty();
    }

    public void toggleCastMode(Player player) {
        if (player == null) {
            return;
        }
        PlayerSkillProfile profile = dataStore.get(player);
        if (profile == null) {
            return;
        }
        profile.setCastModeEnabled(!profile.castModeEnabled());
        profile.markDirty();
    }

    public String getEntryKeyType() {
        // Reads from config via plugin; default to "auto"
        return "auto";
    }
}
