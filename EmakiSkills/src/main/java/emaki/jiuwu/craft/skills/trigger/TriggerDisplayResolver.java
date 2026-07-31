package emaki.jiuwu.craft.skills.trigger;

import java.util.Map;

public final class TriggerDisplayResolver {

    private TriggerDisplayResolver() {
    }

    /**
     * 解析触发器显示名。
     *
     * <p>显示名的唯一来源是 {@code config.yml} 的 {@code triggers.*.display_name}
     * （由 {@code SkillsLifecycleCoordinator} 装入注册表），此处不再维护第二份内置中文映射。
     * 未注册的触发器回落为 {@code [id]}。
     */
    public static String resolve(String triggerId, Map<String, SkillTriggerDefinition> definitions) {
        SkillTriggerDefinition def = definitions.get(triggerId);
        if (def != null) {
            return def.displayName();
        }

        return "[" + triggerId + "]";
    }
}
