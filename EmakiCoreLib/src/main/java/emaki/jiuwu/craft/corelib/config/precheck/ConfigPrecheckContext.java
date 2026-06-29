package emaki.jiuwu.craft.corelib.config.precheck;

import emaki.jiuwu.craft.corelib.action.ActionLineParser;
import emaki.jiuwu.craft.corelib.action.ActionRegistry;
import emaki.jiuwu.craft.corelib.action.ActionTemplateRegistry;

public record ConfigPrecheckContext(
        ActionLineParser lineParser,
        ActionRegistry actionRegistry,
        ActionTemplateRegistry templateRegistry
) {
}
