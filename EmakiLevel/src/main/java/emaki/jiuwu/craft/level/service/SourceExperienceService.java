package emaki.jiuwu.craft.level.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.level.EmakiLevelPlugin;
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
        if (amount <= 0D) {
            return;
        }
        plugin.levelService().addExp(player.getUniqueId(), source.type(), amount, reason);
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

    public SourceRuleConfig.Rule matchItem(SourceRuleConfig source, ItemStack itemStack) {
        ItemSourceService itemSourceService = plugin.coreLib().itemSourceService();
        ItemSource actual = itemSourceService.identifyItem(itemStack);
        for (SourceRuleConfig.Rule rule : source.rules()) {
            if (rule.resultItemSources().isEmpty()) {
                return rule;
            }
            for (String expectedText : rule.resultItemSources()) {
                ItemSource expected = ItemSourceUtil.parse(expectedText);
                if (ItemSourceUtil.matches(expected, actual)) {
                    return rule;
                }
            }
        }
        return null;
    }
}
