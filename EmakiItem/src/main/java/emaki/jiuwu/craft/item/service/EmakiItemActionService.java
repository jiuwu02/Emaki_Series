package emaki.jiuwu.craft.item.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionExecutor;
import emaki.jiuwu.craft.corelib.item.ItemTextBridge;
import emaki.jiuwu.craft.item.EmakiItemPlugin;
import emaki.jiuwu.craft.item.model.EmakiItemDefinition;

public final class EmakiItemActionService {

    private final EmakiItemPlugin plugin;
    private final ActionExecutor actionExecutor;

    public EmakiItemActionService(EmakiItemPlugin plugin, ActionExecutor actionExecutor) {
        this.plugin = plugin;
        this.actionExecutor = actionExecutor;
    }

    public void execute(Player player, EmakiItemDefinition definition, String trigger, Map<String, ?> extraPlaceholders) {
        execute(player, definition, trigger, extraPlaceholders, null);
    }

    public void execute(Player player,
            EmakiItemDefinition definition,
            String trigger,
            Map<String, ?> extraPlaceholders,
            ItemStack itemStack) {
        if (definition == null) {
            return;
        }
        executeLines(player, definition, trigger, definition.actions(trigger), extraPlaceholders, itemStack);
    }

    public void executeLines(Player player,
            EmakiItemDefinition definition,
            String trigger,
            List<String> lines,
            Map<String, ?> extraPlaceholders) {
        executeLines(player, definition, trigger, lines, extraPlaceholders, null);
    }

    public void executeLines(Player player,
            EmakiItemDefinition definition,
            String trigger,
            List<String> lines,
            Map<String, ?> extraPlaceholders,
            ItemStack itemStack) {
        if (player == null || definition == null || lines == null || lines.isEmpty()) {
            return;
        }
        actionExecutor.executeAll(context(player, definition, trigger, extraPlaceholders, itemStack), lines, true);
    }

    ActionContext context(Player player,
            EmakiItemDefinition definition,
            String trigger,
            Map<String, ?> extraPlaceholders) {
        return context(player, definition, trigger, extraPlaceholders, null);
    }

    ActionContext context(Player player,
            EmakiItemDefinition definition,
            String trigger,
            Map<String, ?> extraPlaceholders,
            ItemStack itemStack) {
        Map<String, Object> placeholders = new LinkedHashMap<>();
        placeholders.put("player", player == null ? "" : player.getName());
        placeholders.put("item_id", definition.id());
        placeholders.put("item_trigger", trigger == null ? "" : trigger);
        placeholders.put("item_name", ItemTextBridge.effectiveNamePlain(plugin.itemFactory().create(definition.id(), 1)));
        if (extraPlaceholders != null) {
            placeholders.putAll(extraPlaceholders);
        }
        ActionContext context = ActionContext.create(plugin, player, trigger, false)
                .withPlaceholders(placeholders)
                .withAttribute("item_definition", definition)
                .withAttribute("item_id", definition.id())
                .withAttribute("trigger", trigger == null ? "" : trigger);
        return itemStack == null ? context : context.withAttribute("item_stack", itemStack);
    }
}
