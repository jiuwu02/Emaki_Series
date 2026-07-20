package emaki.jiuwu.craft.codex.advancement;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.codex.advancement.model.AdvancementDefinition;







public final class AdvancementService {

    private final AdvancementRegistrar registrar;

    public AdvancementService(AdvancementRegistrar registrar) {
        this.registrar = registrar;
    }








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
