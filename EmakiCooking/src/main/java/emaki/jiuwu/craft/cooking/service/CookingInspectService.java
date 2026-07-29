package emaki.jiuwu.craft.cooking.service;

import java.util.Map;

import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.cooking.model.StationType;
import emaki.jiuwu.craft.cooking.service.StationStateStore.StorageInspection;

public final class CookingInspectService {

    private final MessageService messageService;
    private final ItemSourceService itemSourceService;
    private final StationStateStore stationStateStore;
    private final CookingBlockMatcher blockMatcher;
    private final CookingSettingsService settingsService;

    public CookingInspectService(MessageService messageService,
            ItemSourceService itemSourceService,
            StationStateStore stationStateStore,
            CookingBlockMatcher blockMatcher,
            CookingSettingsService settingsService) {
        this.messageService = messageService;
        this.itemSourceService = itemSourceService;
        this.stationStateStore = stationStateStore;
        this.blockMatcher = blockMatcher;
        this.settingsService = settingsService;
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
        String displayName = EmakiCoreLibApi.itemDisplayName(hand).orElse("");
        messageService.sendRaw(sender, messageService.message("command.inspect.header"));
        messageService.sendRaw(sender, messageService.message("command.inspect.line", Map.of("key", "player", "value", player.getName())));
        messageService.sendRaw(sender, messageService.message("command.inspect.line", Map.of("key", "source", "value", shorthand)));
        messageService.sendRaw(sender, messageService.message("command.inspect.line", Map.of("key", "display", "value", displayName)));
        messageService.sendRaw(sender, messageService.message("command.inspect.line", Map.of("key", "amount", "value", hand.getAmount())));
        return true;
    }

    public boolean inspectBlock(CommandSender sender, Player player) {
        if (player == null) {
            return false;
        }
        Block block = player.getTargetBlockExact(6);
        if (block == null || block.getType().isAir()) {
            messageService.sendRaw(sender, "<gray>No target block within 6 blocks.</gray>");
            return true;
        }
        StorageInspection inspection = stationStateStore == null ? null : stationStateStore.inspect(block);
        StationType stationType = inspection == null ? null : inspection.stationType();
        if (stationType == null) {
            stationType = resolveConfiguredStation(block);
        }
        boolean disabled = settingsService != null
                && stationType != null
                && block.getWorld() != null
                && settingsService.isInteractionDisabled(stationType, block.getWorld().getName());
        messageService.sendRaw(sender, "<gold>Station block inspect</gold>");
        messageService.sendRaw(sender, line("station", stationType == null ? "-" : stationType.folderName()));
        messageService.sendRaw(sender, line("source", inspection == null || inspection.stationSource().isBlank() ? "-" : inspection.stationSource()));
        messageService.sendRaw(sender, line("block", block.getType().getKey().toString()));
        messageService.sendRaw(sender, line("block_state", inspection == null ? block.getState().getClass().getSimpleName() : inspection.blockStateClass()));
        messageService.sendRaw(sender, line("tile_state", inspection != null && inspection.tileState() ? "yes" : "no"));
        messageService.sendRaw(sender, line("storage_backend", inspection == null ? "-" : inspection.currentBackend().name()));
        messageService.sendRaw(sender, line("pdc_state", inspection != null && inspection.pdcPresent() ? "present" : "absent"));
        messageService.sendRaw(sender, line("indexed", inspection != null && inspection.indexed() ? "yes" : "no"));
        messageService.sendRaw(sender, line("index_backend", inspection == null || inspection.indexedBackend() == null ? "-" : inspection.indexedBackend().name()));
        messageService.sendRaw(sender, line("legacy_yaml", inspection != null && inspection.legacyYamlPresent() ? "present" : "absent"));
        messageService.sendRaw(sender, line("interaction", disabled ? "disabled (world blacklist)" : "enabled"));
        return true;
    }

    private StationType resolveConfiguredStation(Block block) {
        if (blockMatcher == null || block == null) {
            return null;
        }
        for (StationType type : StationType.values()) {
            if (blockMatcher.matches(block, type)) {
                return type;
            }
        }
        return null;
    }

    private String line(String key, Object value) {
        return "<gray>" + key + ":</gray> <white>" + String.valueOf(value) + "</white>";
    }
}