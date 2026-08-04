package emaki.jiuwu.craft.item.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.pipeline.PipelineContext;
import emaki.jiuwu.craft.corelib.api.action.CoreActionKeys;
import emaki.jiuwu.craft.corelib.api.item.ItemTextBridge;
import emaki.jiuwu.craft.item.EmakiItemPlugin;
import emaki.jiuwu.craft.item.model.EmakiItemDefinition;

public final class EmakiItemActionService {

    private final EmakiItemPlugin plugin;

    public EmakiItemActionService(EmakiItemPlugin plugin) {
        this.plugin = plugin;
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
        // The item travels as the typed ITEM key, which is what stages read in place of v1's
        // weakly-typed "item_stack" attribute. The trigger stays the phase, as it was in v1.
        PipelineContext context = plugin.actionLines()
                .context(player, trigger, false, placeholders(player, definition, trigger, extraPlaceholders));
        plugin.actionLines().run(lines, itemStack == null
                ? context
                : context.with(CoreActionKeys.ITEM, itemStack), true);
    }

    ActionContext context(Player player,
            EmakiItemDefinition definition,
            String trigger,
            Map<String, ?> extraPlaceholders) {
        return context(player, definition, trigger, extraPlaceholders, null);
    }

    /**
     * Builds the context CoreLib's placeholder registry consumes.
     *
     * <p>Retained after the pipeline migration because {@code PlaceholderResolver} and
     * {@code PlaceholderRegistry} are declared in terms of {@code ActionContext}; that subsystem keeps it
     * as its own context type, so this is not a leftover action-executor dependency. The condition path
     * only reaches it indirectly: {@code ConditionEvaluator} takes a text resolver, and the resolver it is
     * handed renders through this context.</p>
     */
    ActionContext context(Player player,
            EmakiItemDefinition definition,
            String trigger,
            Map<String, ?> extraPlaceholders,
            ItemStack itemStack) {
        ActionContext context = ActionContext.create(player, trigger, false)
                .withPlaceholders(placeholders(player, definition, trigger, extraPlaceholders))
                .withAttribute("item_definition", definition)
                .withAttribute("item_id", definition.id())
                .withAttribute("trigger", trigger == null ? "" : trigger);
        return itemStack == null ? context : context.withAttribute("item_stack", itemStack);
    }

    private Map<String, Object> placeholders(Player player,
            EmakiItemDefinition definition,
            String trigger,
            Map<String, ?> extraPlaceholders) {
        Map<String, Object> placeholders = new LinkedHashMap<>();
        placeholders.put("player", player == null ? "" : player.getName());
        placeholders.put("item_id", definition.id());
        placeholders.put("item_trigger", trigger == null ? "" : trigger);
        placeholders.put("item_name", ItemTextBridge.effectiveNamePlain(plugin.itemFactory().create(definition.id(), 1)));
        if (extraPlaceholders != null) {
            placeholders.putAll(extraPlaceholders);
        }
        return placeholders;
    }
}
