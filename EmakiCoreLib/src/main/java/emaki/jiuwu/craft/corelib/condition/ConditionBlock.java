package emaki.jiuwu.craft.corelib.condition;

import java.util.List;

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
                firstBoolean(section, defaultBlockOutput, "on_fail.block_output", "on_fail.block"),
                section.getString("on_fail.message", "")
        );
    }

    private static boolean firstBoolean(YamlSection section, boolean defaultValue, String... paths) {
        if (section == null || paths == null) {
            return defaultValue;
        }
        for (String path : paths) {
            if (Texts.isNotBlank(path) && section.contains(path)) {
                return section.getBoolean(path, defaultValue);
            }
        }
        return defaultValue;
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
