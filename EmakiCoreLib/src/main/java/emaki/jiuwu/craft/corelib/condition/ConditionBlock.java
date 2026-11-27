package emaki.jiuwu.craft.corelib.condition;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import emaki.jiuwu.craft.corelib.api.condition.ConditionContext;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;

public record ConditionBlock(ConditionGroup group,
        boolean invalidAsFailure,
        List<String> passActions,
        List<String> failActions,
        boolean blockOutput,
        String failMessage) {

    public ConditionBlock {
        group = group == null ? ConditionGroup.empty() : group;
        passActions = normalizeActions(passActions);
        failActions = normalizeActions(failActions);
        failMessage = Texts.toStringSafe(failMessage);
    }

    public static ConditionBlock empty() {
        return new ConditionBlock(ConditionGroup.empty(), true, List.of(), List.of(), false, "");
    }

    public static ConditionBlock fromConfig(Object raw) {
        return fromConfig(raw, true, false);
    }

    public static ConditionBlock fromConfig(Object raw, boolean defaultInvalidAsFailure, boolean defaultBlockOutput) {
        if (raw == null) {
            return new ConditionBlock(ConditionGroup.empty(), defaultInvalidAsFailure, List.of(), List.of(), defaultBlockOutput, "");
        }
        if (raw instanceof ConditionBlock block) {
            return block;
        }
        YamlSection section = ConditionGroup.asSection(raw);
        if (section == null) {
            return new ConditionBlock(ConditionGroup.fromConfig(raw), defaultInvalidAsFailure, List.of(), List.of(), defaultBlockOutput, "");
        }
        return fromSection(section, defaultInvalidAsFailure, defaultBlockOutput);
    }

    public static ConditionBlock fromRoot(YamlSection root) {
        return fromRoot(root, true, false);
    }

    public static ConditionBlock fromRoot(YamlSection root, boolean defaultInvalidAsFailure, boolean defaultBlockOutput) {
        if (root == null) {
            return new ConditionBlock(ConditionGroup.empty(), defaultInvalidAsFailure, List.of(), List.of(), defaultBlockOutput, "");
        }
        YamlSection section = root.getSection("condition");
        if (section != null && !section.isEmpty()) {
            return fromSection(section, defaultInvalidAsFailure, defaultBlockOutput);
        }
        return new ConditionBlock(ConditionGroup.empty(), defaultInvalidAsFailure, List.of(), List.of(), defaultBlockOutput, "");
    }

    public boolean configured() {
        return group != null && !group.emptyGroup();
    }

    public String conditionType() {
        return group == null ? "all_of" : group.conditionType();
    }

    public int requiredCount() {
        return group == null ? 0 : group.requiredCount();
    }

    private static ConditionBlock fromSection(YamlSection section, boolean defaultInvalidAsFailure, boolean defaultBlockOutput) {
        ConditionGroup group = ConditionGroup.fromConfig(section);
        return new ConditionBlock(
                group,
                section.getBoolean("invalid_as_failure", defaultInvalidAsFailure),
                section.getStringList("on_pass.actions"),
                section.getStringList("on_fail.actions"),
                section.getBoolean("on_fail.block_output", defaultBlockOutput),
                section.getString("on_fail.message", "")
        );
    }

    public ConditionResolution resolve(Function<String, String> placeholderReplacer) {
        return resolve(placeholderReplacer, ConditionContext.EMPTY);
    }

    public ConditionResolution resolve(Function<String, String> placeholderReplacer, ConditionContext context) {
        if (!configured()) {
            return ConditionResolution.pass();
        }
        boolean passed = ConditionEvaluator.evaluate(this, placeholderReplacer, context);
        if (passed) {
            return ConditionResolution.pass();
        }
        List<ConditionFailure> failures = ConditionEvaluator.failures(group, placeholderReplacer, invalidAsFailure, context);
        List<String> messages = new ArrayList<>();
        List<String> actions = new ArrayList<>();
        boolean blocked = blockOutput;
        for (ConditionFailure failure : failures) {
            if (Texts.isNotBlank(failure.message())) {
                messages.add(failure.message());
            }
            actions.addAll(failure.actions());
            blocked = blocked || failure.blockOutput();
        }
        if (messages.isEmpty() && Texts.isNotBlank(failMessage)) {
            messages.add(failMessage);
        }
        if (actions.isEmpty()) {
            actions.addAll(failActions);
        }
        return ConditionResolution.fail(messages, actions, blocked);
    }

    private static List<String> normalizeActions(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .map(Texts::toStringSafe)
                .filter(Texts::isNotBlank)
                .toList();
    }
}
