package emaki.jiuwu.craft.codex.action;

import emaki.jiuwu.craft.codex.EmakiCodexPlugin;
import emaki.jiuwu.craft.corelib.action.ActionRegistry;

/** Registers EmakiCodex actions into CoreLib's ActionRegistry. */
public final class CodexActionRegistrar {

    private static final String SOURCE = "emakicodex";

    private final EmakiCodexPlugin plugin;

    public CodexActionRegistrar(EmakiCodexPlugin plugin) {
        this.plugin = plugin;
    }

    public void register(ActionRegistry registry) {
        if (registry == null) {
            return;
        }
        registry.register(plugin, SOURCE, new GrantAdvancementAction(plugin));
        registry.register(plugin, SOURCE, new RevokeAdvancementAction(plugin));
        registry.register(plugin, SOURCE, new ResyncAdvancementAction(plugin));
        registry.register(plugin, SOURCE, new ResetAdvancementAction(plugin, ResetAdvancementAction.Mode.PAGE));
        registry.register(plugin, SOURCE, new ResetAdvancementAction(plugin, ResetAdvancementAction.Mode.ALL));
    }
}
