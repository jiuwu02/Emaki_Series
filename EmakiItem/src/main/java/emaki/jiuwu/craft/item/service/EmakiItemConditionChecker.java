package emaki.jiuwu.craft.item.service;

import java.util.Map;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.condition.ConditionEvaluator;
import emaki.jiuwu.craft.corelib.placeholder.PlaceholderRegistry;
import emaki.jiuwu.craft.corelib.text.AdventureSupport;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.item.EmakiItemPlugin;
import emaki.jiuwu.craft.item.model.EmakiItemDefinition;
import emaki.jiuwu.craft.item.model.ItemConditions;

public final class EmakiItemConditionChecker {

    private final EmakiItemPlugin plugin;
    private final PlaceholderRegistry placeholderRegistry;
    private final EmakiItemActionService actionService;

    public EmakiItemConditionChecker(EmakiItemPlugin plugin,
            PlaceholderRegistry placeholderRegistry,
            EmakiItemActionService actionService) {
        this.plugin = plugin;
        this.placeholderRegistry = placeholderRegistry;
        this.actionService = actionService;
    }

    public boolean passes(Player player, EmakiItemDefinition definition, String trigger) {
        if (player == null || definition == null) {
            return false;
        }
        ItemConditions conditions = definition.conditions();
        if (conditions == null || !conditions.configured()) {
            return true;
        }
        ActionContext context = actionService.context(player, definition, trigger, Map.of());
        boolean passes = ConditionEvaluator.evaluate(
                conditions.entries(),
                conditions.type(),
                conditions.requiredCount(),
                text -> placeholderRegistry.resolve(context, text),
                conditions.invalidAsFailure()
        );
        if (!passes) {
            if (Texts.isNotBlank(conditions.denyMessage())) {
                AdventureSupport.sendMessage(plugin, player, MiniMessages.parse(conditions.denyMessage()));
            }
            actionService.executeLines(player, definition, "condition_deny", conditions.denyActions(), Map.of());
        }
        return passes;
    }
}
