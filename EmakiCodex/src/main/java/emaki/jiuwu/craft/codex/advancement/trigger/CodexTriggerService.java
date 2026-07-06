package emaki.jiuwu.craft.codex.advancement.trigger;

import java.util.Locale;
import java.util.Map;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.codex.EmakiCodexPlugin;
import emaki.jiuwu.craft.codex.advancement.AdvancementService;
import emaki.jiuwu.craft.codex.advancement.loader.AdvancementPageLoader;
import emaki.jiuwu.craft.codex.advancement.model.AdvancementDefinition;
import emaki.jiuwu.craft.codex.advancement.model.AdvancementPage;
import emaki.jiuwu.craft.codex.advancement.model.AdvancementTrigger;
import emaki.jiuwu.craft.corelib.condition.ConditionEvaluator;
import emaki.jiuwu.craft.corelib.text.Texts;

/**
 * Turns gameplay events into automatic advancement grants.
 *
 * <p>Given a normalized trigger key and the domain variables produced by a listener, it scans
 * every loaded advancement node for a matching {@link AdvancementTrigger}. When a trigger's
 * condition passes (a blank condition always passes), the owning node's manual {@code codex}
 * criterion is awarded through {@link AdvancementService}, which flows into the existing
 * completion pipeline (toast, announce, {@code on_complete} actions).
 *
 * <p>Definitions are read live from {@link AdvancementPageLoader} on every fire, so a
 * {@code /codex reload} that reloads the pages takes effect immediately without re-registering
 * listeners. Grants are idempotent: awarding an already-completed criterion is a no-op and does
 * not re-run {@code on_complete}.
 */
public final class CodexTriggerService {

    private final EmakiCodexPlugin plugin;
    private final AdvancementPageLoader pageLoader;
    private final AdvancementService advancementService;

    public CodexTriggerService(EmakiCodexPlugin plugin,
            AdvancementPageLoader pageLoader,
            AdvancementService advancementService) {
        this.plugin = plugin;
        this.pageLoader = pageLoader;
        this.advancementService = advancementService;
    }

    /**
     * Evaluates all advancement triggers for a gameplay event and grants every node whose
     * trigger matches and whose condition passes.
     *
     * @param player     the acting player (ignored when {@code null})
     * @param triggerKey the normalized trigger key, e.g. {@code entity_kill}
     * @param variables  domain variables exposed to the condition as {@code %name%} placeholders
     */
    public void fire(Player player, String triggerKey, Map<String, ?> variables) {
        if (player == null) {
            return;
        }
        String normalizedTrigger = Texts.normalizeId(triggerKey);
        if (Texts.isBlank(normalizedTrigger)) {
            return;
        }
        Map<String, ?> safeVariables = variables == null ? Map.of() : variables;
        for (AdvancementPage page : pageLoader.all().values()) {
            for (AdvancementDefinition definition : page.definitions()) {
                for (AdvancementTrigger trigger : definition.triggers()) {
                    if (!normalizedTrigger.equals(trigger.event())) {
                        continue;
                    }
                    if (conditionPasses(player, trigger, safeVariables)) {
                        advancementService.grant(player, grantId(page, definition));
                    }
                }
            }
        }
    }

    /**
     * Evaluates a trigger's condition. A blank condition always passes; an expression that
     * cannot be evaluated (null result) is treated as a failure so a malformed condition never
     * grants by accident.
     */
    private boolean conditionPasses(Player player, AdvancementTrigger trigger, Map<String, ?> variables) {
        if (!trigger.hasCondition()) {
            return true;
        }
        Boolean result = ConditionEvaluator.evaluateSingle(
                trigger.condition(),
                line -> resolvePlaceholders(player, variables, line));
        return Boolean.TRUE.equals(result);
    }

    /**
     * Resolves the condition line: domain variables ({@code %entity_type%} etc.) are substituted
     * first, then any remaining placeholders are handed to PlaceholderAPI when installed.
     */
    private String resolvePlaceholders(Player player, Map<String, ?> variables, String line) {
        String replaced = Texts.formatTemplate(line, variables);
        if (player == null || Texts.isBlank(replaced)
                || !plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return replaced;
        }
        try {
            return Texts.toStringSafe(me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, replaced));
        } catch (Exception | NoClassDefFoundError _) {
            return replaced;
        }
    }

    /**
     * Builds the bare {@code page/node} grant id, normalized the same way the registrar builds
     * advancement keys so {@link AdvancementService#grant} resolves it to a registered node.
     */
    private String grantId(AdvancementPage page, AdvancementDefinition definition) {
        return (page.pageId() + "/" + definition.id())
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_./-]", "_");
    }
}
