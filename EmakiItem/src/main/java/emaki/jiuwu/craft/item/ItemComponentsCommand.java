package emaki.jiuwu.craft.item;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.text.MiniMessages;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.item.service.ItemComponentInspector;

final class ItemComponentsCommand {

    private final EmakiItemPlugin plugin;

    ItemComponentsCommand(EmakiItemPlugin plugin) {
        this.plugin = plugin;
    }

    boolean handleComponents(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ItemCommandRouter.PERMISSION_INSPECT)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        ComponentTarget target = componentTarget(sender, args);
        if (target.player() == null) {
            plugin.messageService().send(sender, "general.player_not_found");
            return true;
        }
        ItemStack held = target.player().getInventory().getItemInMainHand();
        if (held == null || held.getType().isAir()) {
            plugin.messageService().send(sender, "command.components.no_item", Map.of("player", target.player().getName()));
            return true;
        }
        Map<String, ItemComponentInspector.ComponentEntry> components = plugin.componentInspector().components(held);
        if (components.isEmpty()) {
            plugin.messageService().send(sender, "command.components.empty", Map.of("player", target.player().getName()));
            return true;
        }
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.components.header", Map.of(
                "player", target.player().getName(),
                "count", components.size()
        )));
        if (Texts.isNotBlank(target.componentId())) {
            String componentId = plugin.componentInspector().normalizeComponentId(target.componentId());
            ItemComponentInspector.ComponentEntry entry = components.get(componentId);
            if (entry == null) {
                plugin.messageService().send(sender, "command.components.not_found", Map.of("id", componentId));
                return true;
            }
            String output = target.yaml()
                    ? plugin.componentInspector().prettyYaml(held, entry.id())
                    : plugin.componentInspector().prettyJson(held, entry.id());
            sendComponentEntry(sender, entry.id(), output);
            return true;
        }
        if (target.yaml()) {
            sendComponentOutput(sender, plugin.componentInspector().prettyYaml(held));
            return true;
        }
        for (ItemComponentInspector.ComponentEntry entry : components.values()) {
            sendComponentEntry(sender, entry.id(), plugin.componentInspector().prettyJson(held, entry.id()));
        }
        return true;
    }

    private ComponentTarget componentTarget(CommandSender sender, String[] args) {
        int index = 1;
        boolean yaml = args.length > index && "yaml".equalsIgnoreCase(args[index]);
        if (yaml) {
            index++;
        }
        if (args.length <= index) {
            return new ComponentTarget(sender instanceof Player player ? player : null, "", yaml);
        }
        Player named = Bukkit.getPlayerExact(args[index]);
        if (named != null) {
            return new ComponentTarget(named, args.length > index + 1 ? args[index + 1] : "", yaml);
        }
        if (sender instanceof Player player) {
            return new ComponentTarget(player, args[index], yaml);
        }
        return new ComponentTarget(null, "", yaml);
    }

    private void sendComponentEntry(CommandSender sender, String componentId, String output) {
        plugin.messageService().sendComponent(sender, MiniMessages.parse(componentHeader(componentId)));
        sendComponentOutput(sender, output);
    }

    private void sendComponentOutput(CommandSender sender, String output) {
        for (String line : Texts.toStringSafe(output).split("\\R", -1)) {
            plugin.messageService().sendComponent(sender, MiniMessages.parse("<dark_gray>|</dark_gray> <white>" + MiniMessages.escape(line) + "</white>"));
        }
    }

    private String componentHeader(String componentId) {
        String escapedId = MiniMessages.escape(componentId);
        return "<gray>- <hover:show_text:'<gray>组件 ID: <white>" + escapedId
                + "</white></gray><newline><dark_gray>点击复制组件 ID</dark_gray>'>"
                + "<click:copy_to_clipboard:'" + tagArgument(componentId) + "'><aqua>" + escapedId + "</aqua></click></hover></gray>";
    }

    private String tagArgument(String value) {
        return Texts.toStringSafe(value).replace("\\", "\\\\").replace("'", "\\'");
    }

    void completeComponentIds(List<String> result, Player player, String prefix) {
        String normalizedPrefix = Texts.toStringSafe(prefix).toLowerCase(Locale.ROOT);
        for (String id : plugin.componentInspector().ids(player.getInventory().getItemInMainHand())) {
            if (id.toLowerCase(Locale.ROOT).startsWith(normalizedPrefix) && !result.contains(id)) {
                result.add(id);
            }
        }
    }

    private record ComponentTarget(Player player, String componentId, boolean yaml) {
    }
}
