package emaki.jiuwu.craft.corelib.action.pipeline.compile;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import emaki.jiuwu.craft.corelib.api.action.CoreActionKey;
import emaki.jiuwu.craft.corelib.api.action.CoreStageKind;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRequirement;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.runtime.ExecutionDomain;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.action.pipeline.compile.PhaseContract;

final class StaticValidatorSupport {

    static boolean containsOnError(List<ActionAst> nodes) {
        for (ActionAst node : nodes) {
            if (node instanceof ActionAst.OnError) {
                return true;
            }
            if (node instanceof ActionAst.Branch branch) {
                if (containsOnError(branch.thenBranch()) || containsOnError(branch.elseBranch())) {
                    return true;
                }
            }
            if (node instanceof ActionAst.Weighted weighted) {
                for (ActionAst.Weighted.Option option : weighted.options()) {
                    if (containsOnError(option.body())) {
                        return true;
                    }
                }
            }
        }
        return false;
}

    static ActionAst firstMeaningful(List<ActionAst> nodes) {
        for (ActionAst node : nodes) {
            if (node instanceof ActionAst.Inline inline) {
                ActionAst inner = firstMeaningful(inline.nodes());
                if (inner != null) {
                    return inner;
                }
                continue;
            }
            return node;
        }
        return null;
}

    static boolean validLiteral(String value, CoreStageParameterType type) {
        CoreStageParameterType expected = type == null ? CoreStageParameterType.STRING : type;
        return switch (expected) {
            case STRING, SOUND -> true;
            case INTEGER -> ValueParsers.parseIntNullable(value) != null;
            case DOUBLE -> ValueParsers.parseDoubleNullable(value) != null;
            case BOOLEAN -> "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value);
            case DURATION -> ValueParsers.parseTicks(value) >= 0L;
            case ENTITY_TYPE -> enumExists(EntityType.class, value);
            case MATERIAL -> Material.matchMaterial(value) != null;
            case PERCENTAGE -> validChance(value);
            case EXPRESSION -> ExpressionEngine.evaluateNumericDetailed(value).success();
        };
}

    static boolean validChance(String value) {
        int slash = value.indexOf('/');
        if (slash > 0 && slash < value.length() - 1) {
            Double numerator = ValueParsers.parseDoubleNullable(value.substring(0, slash).trim());
            Double denominator = ValueParsers.parseDoubleNullable(value.substring(slash + 1).trim());
            return numerator != null && denominator != null && denominator != 0D
                    && numerator / denominator >= 0D && numerator / denominator <= 1D;
        }
        double chance = ValueParsers.parseChance(value);
        return chance >= 0D && chance <= 1D;
}

    static <E extends Enum<E>> boolean enumExists(Class<E> type, String value) {
        String normalized = Texts.trim(value).replace("minecraft:", "")
                .replace('.', '_').toUpperCase(Locale.ROOT);
        try {
            Enum.valueOf(type, normalized);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
}

    static boolean containsPlaceholder(String value) {
        int first = value.indexOf('%');
        return first >= 0 && value.indexOf('%', first + 1) > first;
}

    static PipelineToken token(String text, int column) {
        return new PipelineToken(PipelineToken.Kind.WORD, text, Math.max(1, column), false);
}
}
