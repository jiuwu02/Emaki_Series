package emaki.jiuwu.craft.accessory.service;

import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.accessory.model.PlayerAccessories;
import emaki.jiuwu.craft.corelib.api.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.log.LogTags;
import emaki.jiuwu.craft.corelib.log.LogTag;
import java.util.logging.Level;

public final class AccessoryAdminService {

    private final Logger logger;
    private final PlayerAccessoryStore store;

    public AccessoryAdminService(Logger logger, PlayerAccessoryStore store) {
        this.logger = logger;
        this.store = store;
    }

    public int clear(CommandSender operator, UUID targetId) {
        if (targetId == null) {
            return -1;
        }
        Integer removed = store.mutate(targetId, 0L, accessories -> {
            Map<String, Map<String, ItemStack>> cleared = accessories.clearAll();
            audit(operator, targetId, accessories, cleared);
            int total = 0;
            for (Map<String, ItemStack> page : cleared.values()) {
                total += page.size();
            }
            return total;
        });
        return removed == null ? -1 : removed;
    }

    private void audit(CommandSender operator,
            UUID targetId,
            PlayerAccessories accessories,
            Map<String, Map<String, ItemStack>> cleared) {
        if (logger == null) {
            return;
        }
        String operatorName = operator == null ? "控制台" : operator.getName();
        if (cleared.isEmpty()) {
            LogTags.send(logger, LogTag.COMMAND, Level.INFO, "command.accessory_clear_empty", Map.of("operator", operatorName, "target", targetId, "player", accessories.playerName()));
            return;
        }
        cleared.forEach((pageId, items) -> items.forEach((slotInstanceId, item) ->
                LogTags.send(logger, LogTag.COMMAND, Level.INFO, "command.accessory_clear_item", Map.of("operator", operatorName, "target", targetId, "player", accessories.playerName(), "page", pageId, "slot", slotInstanceId, "type", item.getType().name(), "amount", item.getAmount(), "item_name", ItemTextBridge.effectiveNamePlain(item)))));
    }
}
