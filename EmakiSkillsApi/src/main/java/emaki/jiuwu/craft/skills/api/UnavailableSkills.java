package emaki.jiuwu.craft.skills.api;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.Unit;
import emaki.jiuwu.craft.skills.api.model.PlayerSkillView;
import emaki.jiuwu.craft.skills.api.model.SkillCastOutcome;
import emaki.jiuwu.craft.skills.api.model.SkillDefinitionView;
import emaki.jiuwu.craft.skills.api.model.SkillUpgradeOutcome;
import emaki.jiuwu.craft.skills.api.model.SkillUpgradePreview;

/** No-op layers returned while EmakiSkills is absent. */
final class UnavailableSkills implements SkillCatalog, SkillOperations, SkillExtensions {

    private static final UnavailableSkills INSTANCE = new UnavailableSkills();

    static final SkillCatalog CATALOG = INSTANCE;
    static final SkillOperations OPERATIONS = INSTANCE;
    static final SkillExtensions EXTENSIONS = INSTANCE;

    private UnavailableSkills() {
    }

    @Override public List<SkillDefinitionView> skills() { return List.of(); }
    @Override public Optional<SkillDefinitionView> skill(String skillId) { return Optional.empty(); }
    @Override public EmakiResult<PlayerSkillView> playerSkills(Player player) { return EmakiResult.unavailable(); }
    @Override public EmakiResult<Integer> level(Player player, String skillId) { return EmakiResult.unavailable(); }
    @Override public EmakiResult<SkillUpgradePreview> upgradePreview(Player player, String skillId) { return EmakiResult.unavailable(); }
    @Override public EmakiResult<Boolean> hasLearned(Player player, String skillId) { return EmakiResult.unavailable(); }
    @Override public EmakiResult<Boolean> castModeEnabled(Player player) { return EmakiResult.unavailable(); }
    @Override public EmakiResult<Boolean> atMaxLevel(Player player, String skillId) { return EmakiResult.unavailable(); }

    @Override public CompletableFuture<EmakiResult<SkillCastOutcome>> cast(Player player, String skillId) {
        return CompletableFuture.completedFuture(EmakiResult.unavailable());
    }
    @Override public CompletableFuture<EmakiResult<SkillCastOutcome>> castByTrigger(Player player, String triggerId) {
        return CompletableFuture.completedFuture(EmakiResult.unavailable());
    }
    @Override public EmakiResult<Unit> learn(Player player, String skillId) { return EmakiResult.unavailable(); }
    @Override public EmakiResult<Unit> forget(Player player, String skillId) { return EmakiResult.unavailable(); }
    @Override public EmakiResult<Integer> forgetAll(Player player) { return EmakiResult.unavailable(); }
    @Override public EmakiResult<Unit> equip(Player player, int slotIndex, String skillId) { return EmakiResult.unavailable(); }
    @Override public EmakiResult<Unit> unequip(Player player, int slotIndex) { return EmakiResult.unavailable(); }
    @Override public EmakiResult<Unit> bindTrigger(Player player, int slotIndex, String triggerId) { return EmakiResult.unavailable(); }
    @Override public EmakiResult<SkillUpgradeOutcome> upgrade(Player player, String skillId) { return EmakiResult.unavailable(); }
    @Override public EmakiResult<Integer> setLevel(Player player, String skillId, int level) { return EmakiResult.unavailable(); }
    @Override public EmakiResult<Integer> addLevel(Player player, String skillId, int delta) { return EmakiResult.unavailable(); }
    @Override public EmakiResult<Unit> setCastMode(Player player, boolean enabled) { return EmakiResult.unavailable(); }

    @Override public SkillSourceRegistration registerSkillSource(Plugin owner, SkillSourceProvider provider) {
        return SkillSourceRegistration.noop();
    }
}
