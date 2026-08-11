package emaki.jiuwu.craft.level.service;

import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import emaki.jiuwu.craft.corelib.pdc.PdcPartition;
import emaki.jiuwu.craft.corelib.pdc.PdcService;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.level.config.LevelTypeConfig;
import emaki.jiuwu.craft.level.model.PlayerLevelEntry;

public final class LevelPdcService {

    private final PdcService pdcService;
    private final PdcPartition partition;
    private boolean enabled;

    public LevelPdcService(String namespace, boolean enabled) {
        this.pdcService = new PdcService(Texts.isBlank(namespace) ? "emakilevel" : namespace);
        this.partition = pdcService.partition("player");
        this.enabled = enabled;
    }

    public void enabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void sync(Player player, LevelTypeConfig type, PlayerLevelEntry entry, double requiredExp) {
        if (!enabled || player == null || type == null || entry == null || !type.pdcEnabled()) {
            return;
        }
        String prefix = type.id() + "_";
        pdcService.set(player, partition, prefix + "level", PersistentDataType.INTEGER, entry.level());
        pdcService.set(player, partition, prefix + "exp", PersistentDataType.DOUBLE, entry.exp());
        pdcService.set(player, partition, prefix + "total_exp", PersistentDataType.DOUBLE, entry.totalExp());
        pdcService.set(player, partition, prefix + "required_exp", PersistentDataType.DOUBLE, requiredExp);
        double progress = requiredExp <= 0D ? 1D : Math.min(1D, entry.exp() / requiredExp);
        pdcService.set(player, partition, prefix + "progress", PersistentDataType.DOUBLE, progress);
    }
}
