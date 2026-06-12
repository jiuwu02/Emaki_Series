package emaki.jiuwu.craft.corelib.condition;

import java.util.List;

import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;

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
        return fromLegacyRoot(root, defaultInvalidAsFailure, defaultBlockOutput);
    }

    public static ConditionBlock fromLegacyRoot(YamlSection root, boolean defaultInvalidAsFailure, boolean defaultBlockOutput) {
        if (root == null || (!root.contains("conditions") && !root.contains("condition_type"))) {
            return new ConditionBlock(ConditionGroup.empty(), defaultInvalidAsFailure, List.of(), List.of(), defaultBlockOutput, "");
        }
        ConditionGroup group = ConditionGroup.fromConfig(
                root,
                root.getString("condition_type", "all_of"),
                Numbers.tryParseInt(root.get("condition_required_count"), 0)
        );
        return new ConditionBlock(
                group,
                root.getBoolean("invalid_as_failure", defaultInvalidAsFailure),
                root.getStringList("pass_actions"),
                root.getStringList("fail_actions"),
                root.getBoolean("block_output_on_false", defaultBlockOutput),
                root.getString("deny_message", "")
        );
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
                firstStringList(section, "on_pass.actions", "pass_actions"),
                firstStringList(section, "on_fail.actions", "fail_actions"),
                firstBoolean(section, defaultBlockOutput, "on_fail.block_output", "on_fail.block", "block_output_on_false"),
                firstString(section, "on_fail.message", "deny_message")
        );
    }

    private static List<String> firstStringList(YamlSection section, String... paths) {
        if (section == null || paths == null) {
            return List.of();
        }
        for (String path : paths) {
            if (Texts.isBlank(path) || !section.contains(path)) {
                continue;
            }
            List<String> values = section.getStringList(path);
            if (!values.isEmpty()) {
                return values;
            }
        }
        return List.of();
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

    private static String firstString(YamlSection section, String... paths) {
        if (section == null || paths == null) {
            return "";
        }
        for (String path : paths) {
            if (Texts.isBlank(path) || !section.contains(path)) {
                continue;
            }
            String value = section.getString(path, "");
            if (Texts.isNotBlank(value)) {
                return value;
            }
        }
        return "";
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
