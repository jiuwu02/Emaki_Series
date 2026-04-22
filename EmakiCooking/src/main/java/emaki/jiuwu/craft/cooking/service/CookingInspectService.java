package emaki.jiuwu.craft.cooking.service;

import java.util.Map;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.service.MessageService;

public final class CookingInspectService {

    private final MessageService messageService;
    private final ItemSourceService itemSourceService;

    public CookingInspectService(MessageService messageService, ItemSourceService itemSourceService) {
        this.messageService = messageService;
        this.itemSourceService = itemSourceService;
    }

    public boolean inspectHand(CommandSender sender, Player player) {
        if (player == null) {
            return false;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            messageService.send(sender, "general.no_item_in_hand");
            return true;
        }
        ItemSource source = itemSourceService.identifyItem(hand);
        String shorthand = source == null ? "-" : String.valueOf(ItemSourceUtil.toShorthand(source));
        String displayName = source == null ? hand.getType().name() : itemSourceService.displayName(source);
        messageService.sendRaw(sender, messageService.message("command.inspect.header"));
        messageService.sendRaw(sender, messageService.message("command.inspect.line", Map.of("key", "player", "value", player.getName())));
        messageService.sendRaw(sender, messageService.message("command.inspect.line", Map.of("key", "source", "value", shorthand)));
        messageService.sendRaw(sender, messageService.message("command.inspect.line", Map.of("key", "display", "value", displayName)));
        messageService.sendRaw(sender, messageService.message("command.inspect.line", Map.of("key", "amount", "value", hand.getAmount())));
        return true;
    }
}
