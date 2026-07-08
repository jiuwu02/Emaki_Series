package emaki.jiuwu.craft.codex.action;

import java.util.List;

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
        registerGrant(registry, List.of("codex-grant-advancement", "codex_grant_advancement", "codexgrantadvancement"));
        registerRevoke(registry, List.of("codex-revoke-advancement", "codex_revoke_advancement", "codexrevokeadvancement"));
        registerResync(registry, List.of("codex-resync-advancement", "codex_resync_advancement", "codexresyncadvancement"));
        registerReset(registry, ResetAdvancementAction.Mode.PAGE, List.of("codex-reset-page", "codex_reset_page", "codexresetpage"));
        registerReset(registry, ResetAdvancementAction.Mode.ALL, List.of("codex-reset-all", "codex_reset_all", "codexresetall"));
    }

    private void registerGrant(ActionRegistry registry, List<String> ids) {
        for (String id : ids) {
            registry.register(plugin, SOURCE, new GrantAdvancementAction(plugin, id));
        }
    }

    private void registerRevoke(ActionRegistry registry, List<String> ids) {
        for (String id : ids) {
            registry.register(plugin, SOURCE, new RevokeAdvancementAction(plugin, id));
        }
    }

    private void registerResync(ActionRegistry registry, List<String> ids) {
        for (String id : ids) {
            registry.register(plugin, SOURCE, new ResyncAdvancementAction(plugin, id));
        }
    }

    private void registerReset(ActionRegistry registry, ResetAdvancementAction.Mode mode, List<String> ids) {
        for (String id : ids) {
            registry.register(plugin, SOURCE, new ResetAdvancementAction(plugin, mode, id));
        }
    }
}
