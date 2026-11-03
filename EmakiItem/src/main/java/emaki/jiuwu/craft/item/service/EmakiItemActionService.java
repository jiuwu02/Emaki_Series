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
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.item.EmakiItemPlugin;
import emaki.jiuwu.craft.item.config.AppConfig;
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
        DebugLogger debugLogger = actionDebug(player);
        boolean runnable = player != null && definition != null && lines != null && !lines.isEmpty();
        logDispatch(debugLogger, player, definition, trigger, lines, runnable);
        if (!runnable) {
            return;
        }

        PipelineContext context = plugin.actionLines()
                .context(player, trigger, false, placeholders(player, definition, trigger, extraPlaceholders));
        plugin.actionLines().run(lines, itemStack == null
                ? context
                : context.with(CoreActionKeys.ITEM, itemStack), stopOnFailure())
                .whenComplete((success, error) -> logResult(debugLogger, player, trigger, success, error));
    }

    private boolean stopOnFailure() {
        AppConfig config = plugin.appConfig();
        return config != null && config.actionStopOnFailure();
    }

    private DebugLogger actionDebug(Player player) {
        DebugLogger debugLogger = plugin.debugLogger();
        return debugLogger != null && debugLogger.shouldLog("action", player) ? debugLogger : null;
    }

    private void logDispatch(DebugLogger debugLogger,
            Player player,
            EmakiItemDefinition definition,
            String trigger,
            List<String> lines,
            boolean runnable) {
        if (debugLogger == null) {
            return;
        }
        debugLogger.log("action", player,
                runnable ? "action.dispatch" : "action.dispatch_skipped",
                Map.of("trigger", Texts.toStringSafe(trigger),
                        "definition", definition == null ? "-" : definition.id(),
                        "lines", lines == null ? "0" : Texts.toStringSafe(lines.size()),
                        "runnable", runnable,
                        "first_line", lines == null || lines.isEmpty() ? "-" : Texts.toStringSafe(lines.get(0))));
    }

    private void logResult(DebugLogger debugLogger, Player player, String trigger, Boolean success, Throwable error) {
        if (debugLogger == null) {
            return;
        }
        debugLogger.log("action", player, "action.result",
                Map.of("trigger", Texts.toStringSafe(trigger),
                        "success", Texts.toStringSafe(success),
                        "error", error == null ? "-" : Texts.toStringSafe(error.getMessage())));
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
