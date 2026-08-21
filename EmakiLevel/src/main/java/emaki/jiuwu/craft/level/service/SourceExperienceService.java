package emaki.jiuwu.craft.level.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemComponentSnapshotScope;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.matcher.MatchContext;
import emaki.jiuwu.craft.corelib.matcher.Matcher;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.level.EmakiLevelPlugin;
import emaki.jiuwu.craft.level.api.ExpSourceContext;
import emaki.jiuwu.craft.level.api.LevelOperationResult;
import emaki.jiuwu.craft.level.config.SourceRuleConfig;

public final class SourceExperienceService {

    private final EmakiLevelPlugin plugin;

    public SourceExperienceService(EmakiLevelPlugin plugin) {
        this.plugin = plugin;
    }

    public void award(Player player, SourceRuleConfig source, SourceRuleConfig.Rule rule, Map<String, ?> variables, String reason) {
        if (player == null || source == null || rule == null) {
            return;
        }
        Map<String, Object> context = new LinkedHashMap<>();
        if (variables != null) {
            context.putAll(variables);
        }
        double amount = Math.max(0D, ExpressionEngine.evaluate(rule.expFormula(), context));
        if (amount <= 0D || plugin.antiAbuseService().isOnCooldown(player, source)) {
            return;
        }
        LevelOperationResult result = plugin.levelService().addExp(player.getUniqueId(), source.type(), amount, reason);
        if (result.success()) {
            plugin.antiAbuseService().markCooldown(player, source);
        }
    }

    public void awardExtensions(Player player, String trigger, Map<String, ?> variables) {
        if (player == null || plugin.expSourceRegistry() == null) {
            return;
        }
        Map<String, Object> contextVariables = new LinkedHashMap<>();
        if (variables != null) {
            contextVariables.putAll(variables);
        }
        ExpSourceContext context = new ExpSourceContext(player, Texts.normalizeId(trigger), contextVariables);
        plugin.expSourceRegistry().dispatch(context, grant -> {
            if (grant == null
                    || Texts.isBlank(grant.typeId())
                    || !Double.isFinite(grant.amount())
                    || grant.amount() <= 0D) {
                return;
            }
            String reason = Texts.isBlank(grant.reason()) ? context.trigger() : grant.reason();
            plugin.levelService().addExp(
                    player.getUniqueId(), grant.typeId(), grant.amount(), reason, null, grant.silent());
        });
    }

    public SourceRuleConfig.Rule matchEntity(SourceRuleConfig source, EntityType type) {
        String name = type == null ? "" : type.name();
        for (SourceRuleConfig.Rule rule : source.rules()) {
            if (rule.entityTypes().isEmpty() || rule.entityTypes().contains(name)) {
                return rule;
            }
        }
        return null;
    }

    public SourceRuleConfig.Rule matchBlock(SourceRuleConfig source, Material material) {
        String name = material == null ? "" : material.name();
        for (SourceRuleConfig.Rule rule : source.rules()) {
            if (rule.blocks().isEmpty() || rule.blocks().contains(name)) {
                return rule;
            }
        }
        return null;
    }

    public SourceRuleConfig.Rule matchState(SourceRuleConfig source, String state) {
        String normalized = Texts.upper(state);
        for (SourceRuleConfig.Rule rule : source.rules()) {
            if (rule.states().isEmpty() || rule.states().contains(normalized)) {
                return rule;
            }
        }
        return null;
    }

    public SourceRuleConfig.Rule matchPotion(SourceRuleConfig source, String potionType) {
        String normalized = Texts.upper(potionType);
        for (SourceRuleConfig.Rule rule : source.rules()) {
            if (rule.potionTypes().isEmpty() || rule.potionTypes().contains(normalized)) {
                return rule;
            }
        }
        return null;
    }

    public SourceRuleConfig.Rule matchMobId(SourceRuleConfig source, String mobId) {
        String normalized = Texts.upper(mobId);
        for (SourceRuleConfig.Rule rule : source.rules()) {
            if (rule.mobIds().isEmpty() || rule.mobIds().contains(normalized)) {
                return rule;
            }
        }
        return null;
    }

    public SourceRuleConfig.Rule matchItem(SourceRuleConfig source, ItemStack itemStack, Player player) {
        ItemSourceService itemSourceService = plugin.coreLib().itemSourceService();
        ItemSourceRef actual = itemSourceService.identifyItem(itemStack);
        for (SourceRuleConfig.Rule rule : source.rules()) {
            if (rule.matcher() != null) {
                if (testMatcher(rule.matcher(), itemStack, actual, player)) {
                    return rule;
                }
                continue;
            }
            if (rule.resultItemSources().isEmpty()) {
                return rule;
            }
            for (String expectedText : rule.resultItemSources()) {
                ItemSourceRef expected = ItemSourceUtil.parse(expectedText);
                if (ItemSourceUtil.matches(expected, actual)) {
                    return rule;
                }
            }
        }
        return null;
    }

    private boolean testMatcher(Matcher matcher, ItemStack itemStack, ItemSourceRef actual, Player player) {
        try (ItemComponentSnapshotScope _ = ItemComponentSnapshotScope.open()) {
            return matcher.test(MatchContext.of(itemStack, actual, player));
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Source rule matcher threw and is treated as no match: "
                    + exception.getClass().getSimpleName());
            return false;
        }
    }
}
