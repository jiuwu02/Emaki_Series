package emaki.jiuwu.craft.codex.apiimpl;

import java.util.UUID;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.codex.EmakiCodexPlugin;
import emaki.jiuwu.craft.codex.advancement.AdvancementService;
import emaki.jiuwu.craft.codex.api.CodexOperations;
import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.Unit;

public final class DefaultCodexOperations implements CodexOperations {

    private final EmakiCodexPlugin plugin;

    public DefaultCodexOperations(EmakiCodexPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull EmakiResult<Unit> grant(@Nullable UUID playerId, @Nullable String advancementId) {
        AdvancementService service = service();
        return service == null ? EmakiResult.unavailable() : service.grant(playerId, advancementId);
    }

    @Override
    public @NotNull EmakiResult<Unit> revoke(@Nullable UUID playerId, @Nullable String advancementId) {
        AdvancementService service = service();
        return service == null ? EmakiResult.unavailable() : service.revoke(playerId, advancementId);
    }

    private AdvancementService service() {
        return plugin.isEnabled() && plugin.contentReady() ? plugin.advancementService() : null;
    }
}
