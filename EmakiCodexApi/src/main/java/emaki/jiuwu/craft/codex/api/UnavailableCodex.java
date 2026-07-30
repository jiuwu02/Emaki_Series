package emaki.jiuwu.craft.codex.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.codex.api.model.AdvancementView;
import emaki.jiuwu.craft.codex.api.model.CodexPageView;
import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.Unit;

/** No-op layers returned while EmakiCodex is absent. */
final class UnavailableCodex implements CodexCatalog, CodexOperations, CodexExtensions {

    private static final UnavailableCodex INSTANCE = new UnavailableCodex();
    static final CodexCatalog CATALOG = INSTANCE;
    static final CodexOperations OPERATIONS = INSTANCE;
    static final CodexExtensions EXTENSIONS = INSTANCE;

    private UnavailableCodex() { }

    @Override public List<AdvancementView> advancements() { return List.of(); }
    @Override public Optional<AdvancementView> advancement(String advancementId) { return Optional.empty(); }
    @Override public List<String> pageIds() { return List.of(); }
    @Override public Optional<CodexPageView> page(String pageId) { return Optional.empty(); }
    @Override public int count() { return 0; }
    @Override public EmakiResult<Boolean> completed(UUID playerId, String advancementId) { return EmakiResult.unavailable(); }
    @Override public EmakiResult<Unit> grant(UUID playerId, String advancementId) { return EmakiResult.unavailable(); }
    @Override public EmakiResult<Unit> revoke(UUID playerId, String advancementId) { return EmakiResult.unavailable(); }
    @Override public AdvancementRegistration registerAdvancement(Plugin owner, AdvancementSpec spec) {
        return AdvancementRegistration.noop();
    }
    @Override public AdvancementTriggerRegistration registerTrigger(Plugin owner, AdvancementTrigger trigger) {
        return AdvancementTriggerRegistration.noop();
    }
}
