package emaki.jiuwu.craft.corelib.editor;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

public final class EditorFeedback {

    private EditorFeedback() {
    }

    public static void play(Player player, boolean success) {
        player.playSound(player.getLocation(),
                success ? Sound.ENTITY_EXPERIENCE_ORB_PICKUP : Sound.ENTITY_VILLAGER_NO,
                1.0F, success ? 1.2F : 0.6F);
    }
}
