package emaki.jiuwu.craft.accessory.service;

import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.accessory.model.PlayerAccessories;
import emaki.jiuwu.craft.corelib.api.item.ItemTextBridge;

/**
 * Administrative accessory data operations.
 *
 * <p>Clearing exists because support cannot be done without it: hand-editing a player's YAML while they
 * are online is pointless, since the session cache writes its own state back over the edit. The command
 * routes through the cache so the in-memory copy is the thing that changes.
 *
 * <p>Every cleared item is logged with slot, type, amount and operator before removal. The operation is
 * irreversible and easy to aim at the wrong name, so without a log a mistake would be untraceable.
 */
public final class AccessoryAdminService {

    private final Logger logger;
    private final PlayerAccessoryStore store;

    /**
     * Creates the service.
     *
     * @param logger receives the audit lines
     * @param store  the session store whose cached payload is mutated
     */
    public AccessoryAdminService(Logger logger, PlayerAccessoryStore store) {
        this.logger = logger;
        this.store = store;
    }

    /**
     * Clears one player's accessory contents.
     *
     * <p>Only touches a loaded, writable session; the caller is responsible for having loaded the target
     * and for closing any open window first, so no stale window can write the old contents back.
     *
     * @param operator the sender performing the operation, for the audit line
     * @param targetId the player whose accessories are cleared
     * @return how many items were removed, or {@code -1} when no writable session was available
     */
    public int clear(CommandSender operator, UUID targetId) {
        if (targetId == null) {
            return -1;
        }
        Integer removed = store.mutate(targetId, 0L, accessories -> {
            Map<String, ItemStack> cleared = accessories.clearAll();
            audit(operator, targetId, accessories, cleared);
            return cleared.size();
        });
        return removed == null ? -1 : removed;
    }

    private void audit(CommandSender operator,
            UUID targetId,
            PlayerAccessories accessories,
            Map<String, ItemStack> cleared) {
        if (logger == null) {
            return;
        }
        String operatorName = operator == null ? "console" : operator.getName();
        if (cleared.isEmpty()) {
            logger.info("Accessory clear by " + operatorName + " on " + targetId
                    + " (" + accessories.playerName() + "): no items to remove");
            return;
        }
        cleared.forEach((slotInstanceId, item) -> logger.info("Accessory clear by " + operatorName
                + " on " + targetId + " (" + accessories.playerName() + "): slot=" + slotInstanceId
                + " type=" + item.getType().name()
                + " amount=" + item.getAmount()
                + " name=" + ItemTextBridge.effectiveNamePlain(item)));
    }
}
