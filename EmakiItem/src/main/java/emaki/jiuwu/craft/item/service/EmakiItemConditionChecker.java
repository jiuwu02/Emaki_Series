package emaki.jiuwu.craft.item.service;

import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.api.condition.ConditionContext;
import emaki.jiuwu.craft.corelib.condition.ConditionEvaluator;
import emaki.jiuwu.craft.corelib.placeholder.PlaceholderRegistry;
import emaki.jiuwu.craft.corelib.api.text.MiniMessages;
import emaki.jiuwu.craft.corelib.api.text.Texts;
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
        return passes(player, definition, trigger, null);
    }

    public boolean passes(Player player, EmakiItemDefinition definition, String trigger, ItemStack itemStack) {
        if (player == null || definition == null) {
            return false;
        }
        ItemConditions conditions = definition.conditions();
        if (conditions == null || !conditions.configured()) {
            return true;
        }
        boolean passes = evaluateSilently(player, definition, trigger, itemStack);
        if (passes) {
            if (!conditions.passActions().isEmpty()) {
                actionService.executeLines(player, definition, "condition_pass", conditions.passActions(), Map.of(), itemStack);
            }
        } else {
            if (Texts.isNotBlank(conditions.denyMessage())) {
                player.sendMessage(MiniMessages.parse(conditions.denyMessage()));
            }
            actionService.executeLines(player, definition, "condition_fail", conditions.failActions(), Map.of(), itemStack);
        }
        return passes;
    }

    /**
     * Evaluates the item conditions without sending the deny message or running
     * the pass/fail actions.
     *
     * <p>Required by hot paths such as attribute and skill collection, which run
     * per equipment slot on every resync and must stay side-effect free.
     *
     * @param player the owning player
     * @param definition the item definition
     * @param trigger the trigger name exposed to the condition context
     * @param itemStack the evaluated item; may be {@code null}
     * @return whether the conditions are satisfied
     */
    public boolean evaluateSilently(Player player,
            EmakiItemDefinition definition,
            String trigger,
            ItemStack itemStack) {
        if (player == null || definition == null) {
            return false;
        }
        ItemConditions conditions = definition.conditions();
        if (conditions == null || !conditions.configured()) {
            return true;
        }
        ActionContext context = actionService.context(player, definition, trigger, Map.of(), itemStack);
        return ConditionEvaluator.evaluate(
                conditions.block(),
                text -> placeholderRegistry.resolve(context, text),
                ConditionContext.of(player, itemStack, Map.of("trigger", Texts.toStringSafe(trigger)))
        );
    }
}
