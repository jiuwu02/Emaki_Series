package emaki.jiuwu.craft.corelib.action.builtin.stage;

import java.util.Locale;
import java.util.Map;

import org.bukkit.GameRule;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.BaseStage;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRequirement;
import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class SetGameRuleStage extends BaseStage {

    public SetGameRuleStage() {
        super("set_game_rule", "world", "设置世界的原版游戏规则。",
                CoreTargetRequirement.NONE, CoreActionExecutionDomain.SERVER_GLOBAL,
                CoreStageParameter.required("rule", CoreStageParameterType.STRING, "游戏规则名"),
                CoreStageParameter.required("value", CoreStageParameterType.STRING, "规则值"));
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        World world;
        try {
            world = context.origin().getWorld();
        } catch (IllegalStateException exception) {
            return CoreActionOutcome.skipped("action.stage.set_game_rule.no_world");
        }
        if (world == null) {
            return CoreActionOutcome.skipped("action.stage.set_game_rule.no_world");
        }
        String ruleName = Texts.lower(Texts.trim(arguments.getString("rule")));
        GameRule<?> rule = lookupRule(ruleName);
        if (rule == null) {
            return CoreActionOutcome.failure(
                    emaki.jiuwu.craft.corelib.api.action.CoreActionFailureKind.INVALID_CONFIG,
                    "action.stage.set_game_rule.unknown_rule",
                    Map.of("rule", ruleName));
        }
        String rawValue = arguments.getString("value");
        Object parsed = parseValue(rule, rawValue);
        if (parsed == null) {
            return CoreActionOutcome.failure(
                    emaki.jiuwu.craft.corelib.api.action.CoreActionFailureKind.INVALID_CONFIG,
                    "action.stage.set_game_rule.invalid_value",
                    Map.of("rule", ruleName, "value", rawValue));
        }
        applyRule(world, rule, parsed);
        return CoreActionOutcome.success();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void applyRule(World world, GameRule<?> rule, Object value) {
        world.setGameRule((GameRule) rule, value);
    }

    private static Object parseValue(GameRule<?> rule, String raw) {
        String normalized = Texts.trim(raw);
        if (rule.getType() == Boolean.class) {
            return "true".equalsIgnoreCase(normalized) || "false".equalsIgnoreCase(normalized)
                    ? Boolean.valueOf(normalized)
                    : null;
        }
        if (rule.getType() == Integer.class) {
            try {
                return Integer.valueOf(normalized);
            } catch (NumberFormatException exception) {
                return null;
            }
        }
        return null;
    }

    private static GameRule<?> lookupRule(String name) {
        for (GameRule<?> rule : GameRule.values()) {
            if (rule.getName().equalsIgnoreCase(name)) {
                return rule;
            }
        }
        return null;
    }
}
