package emaki.jiuwu.craft.codex.action;

import emaki.jiuwu.craft.codex.EmakiCodexPlugin;
import emaki.jiuwu.craft.corelib.action.ActionRegistry;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;


public final class CodexActionRegistrar {

    private static final String SOURCE = "emakicodex";

    private final EmakiCodexPlugin plugin;

    public CodexActionRegistrar(EmakiCodexPlugin plugin) {
        this.plugin = plugin;
    }

    public void register(ActionRegistry registry, ItemSourceService itemSourceService) {
        if (registry == null) {
            return;
        }
        registry.register(plugin, SOURCE, new GrantAdvancementAction(plugin, "codex_grant_advancement"));
        registry.register(plugin, SOURCE, new RevokeAdvancementAction(plugin, "codex_revoke_advancement"));
        registry.register(plugin, SOURCE, new ResyncAdvancementAction(plugin, "codex_resync_advancement"));
        registry.register(plugin, SOURCE, new ResetAdvancementAction(plugin, ResetAdvancementAction.Mode.PAGE, "codex_reset_page"));
        registry.register(plugin, SOURCE, new ResetAdvancementAction(plugin, ResetAdvancementAction.Mode.ALL, "codex_reset_all"));
        registry.register(plugin, SOURCE, new ShowAchievementToastAction(plugin, itemSourceService));
    }
}
