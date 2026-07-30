package emaki.jiuwu.craft.codex.advancement.trigger;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.codex.EmakiCodexPlugin;
import emaki.jiuwu.craft.codex.advancement.AdvancementService;
import emaki.jiuwu.craft.codex.advancement.loader.AdvancementPageLoader;
import emaki.jiuwu.craft.codex.advancement.model.AdvancementDefinition;
import emaki.jiuwu.craft.codex.advancement.model.AdvancementPage;
import emaki.jiuwu.craft.codex.advancement.model.AdvancementTrigger;
import emaki.jiuwu.craft.codex.api.AdvancementTriggerContext;
import emaki.jiuwu.craft.corelib.condition.ConditionContext;
import emaki.jiuwu.craft.corelib.condition.ConditionEvaluator;
import emaki.jiuwu.craft.corelib.text.Texts;

/** Resolves configured and external trigger providers into central advancement mutations. */
public final class CodexTriggerService {

    private final EmakiCodexPlugin plugin;
    private final AdvancementPageLoader pageLoader;
    private final AdvancementService advancementService;
    private final AdvancementTriggerRegistry triggerRegistry;

    public CodexTriggerService(EmakiCodexPlugin plugin,
            AdvancementPageLoader pageLoader,
            AdvancementService advancementService,
            AdvancementTriggerRegistry triggerRegistry) {
        this.plugin = plugin;
        this.pageLoader = pageLoader;
        this.advancementService = advancementService;
        this.triggerRegistry = triggerRegistry;
    }

    public void fire(Player player, String triggerKey, Map<String, ?> variables) {
        if (player == null || !plugin.appConfig().advancementEnabled()
                || !plugin.appConfig().advancementTriggersEnabled()) {
            return;
        }
        String normalizedTrigger = Texts.normalizeId(triggerKey);
        if (Texts.isBlank(normalizedTrigger)) {
            return;
        }
        Map<String, Object> safeVariables = conditionVariables(variables);
        Set<String> grants = new LinkedHashSet<>();
        for (AdvancementPage page : pageLoader.all().values()) {
            for (AdvancementDefinition definition : page.definitions()) {
                for (AdvancementTrigger trigger : definition.triggers()) {
                    if (normalizedTrigger.equals(trigger.event())
                            && conditionPasses(player, trigger, safeVariables)) {
                        grants.add(grantId(page, definition));
                    }
                }
            }
        }
        if (triggerRegistry != null) {
            grants.addAll(triggerRegistry.dispatch(
                    new AdvancementTriggerContext(player, normalizedTrigger, safeVariables)));
        }
        for (String advancementId : grants) {
            advancementService.grant(player, advancementId);
        }
    }

    private boolean conditionPasses(Player player, AdvancementTrigger trigger, Map<String, ?> variables) {
        if (!trigger.hasCondition()) {
            return true;
        }
        return ConditionEvaluator.evaluate(
                trigger.condition(),
                line -> resolvePlaceholders(player, variables, line),
                true,
                ConditionContext.of(player, null, conditionVariables(variables)));
    }

    private Map<String, Object> conditionVariables(Map<String, ?> variables) {
        if (variables == null || variables.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : variables.entrySet()) {
            if (!Texts.isBlank(entry.getKey())) {
                result.put(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
            }
        }
        return Map.copyOf(result);
    }

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

    private String grantId(AdvancementPage page, AdvancementDefinition definition) {
        return (page.pageId() + "/" + definition.id())
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_./-]", "_");
    }
}
