package emaki.jiuwu.craft.corelib.action.builtin;

import org.bukkit.entity.Player;

final class ExperienceSupport {

    private ExperienceSupport() {
    }

    static void setTotalExperience(Player player, int value) {
        if (player == null) {
            return;
        }
        int target = Math.max(0, value);
        player.setExp(0F);
        player.setLevel(0);
        player.setTotalExperience(0);
        player.giveExp(target);
    }
}
