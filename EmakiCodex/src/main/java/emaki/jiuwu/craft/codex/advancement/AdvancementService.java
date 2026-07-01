package emaki.jiuwu.craft.codex.advancement;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.codex.advancement.model.AdvancementDefinition;

/**
 * Grants and revokes EmakiCodex advancements for online players by awarding or
 * revoking the single manual {@code codex} criterion. Awarding it completes the
 * advancement, which fires {@code PlayerAdvancementDoneEvent} and runs the node's
 * {@code on_complete} actions.
 */
public final class AdvancementService {

    private final AdvancementRegistrar registrar;

    public AdvancementService(AdvancementRegistrar registrar) {
        this.registrar = registrar;
    }

    /**
     * Grants an advancement to a player.
     *
     * @param player        the target player
     * @param advancementId the advancement id (full key or bare page/node path)
     * @return {@code true} when the criterion was awarded
     */
    public boolean grant(Player player, String advancementId) {
        if (player == null) {
            return false;
        }
        NamespacedKey key = registrar.resolveKey(advancementId);
        if (key == null) {
            return false;
        }
        Advancement advancement = Bukkit.getAdvancement(key);
        if (advancement == null) {
            return false;
        }
        AdvancementProgress progress = player.getAdvancementProgress(advancement);
        return progress.awardCriteria(AdvancementDefinition.CRITERION);
    }

    /**
     * Revokes an advancement from a player.
     *
     * @param player        the target player
     * @param advancementId the advancement id (full key or bare page/node path)
     * @return {@code true} when the criterion was revoked
     */
    public boolean revoke(Player player, String advancementId) {
        if (player == null) {
            return false;
        }
        NamespacedKey key = registrar.resolveKey(advancementId);
        if (key == null) {
            return false;
        }
        Advancement advancement = Bukkit.getAdvancement(key);
        if (advancement == null) {
            return false;
        }
        AdvancementProgress progress = player.getAdvancementProgress(advancement);
        return progress.revokeCriteria(AdvancementDefinition.CRITERION);
    }
}
