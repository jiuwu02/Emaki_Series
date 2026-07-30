package emaki.jiuwu.craft.level.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.Unit;

/** Shared non-null unavailable implementation used while no runtime bridge is installed. */
enum UnavailableLevel implements LevelCatalog, LevelOperations, LevelExtensions {

    INSTANCE;

    @Override
    public List<LevelTypeView> types() {
        return List.of();
    }

    @Override
    public Optional<LevelTypeView> type(String typeId) {
        return Optional.empty();
    }

    @Override
    public EmakiResult<Integer> level(UUID uuid, String typeId) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<Double> exp(UUID uuid, String typeId) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<Double> totalExp(UUID uuid, String typeId) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<Double> requiredExp(UUID uuid, String typeId, int targetLevel) {
        return EmakiResult.unavailable();
    }

    @Override
    public CompletableFuture<EmakiResult<PlayerLevelView>> loadPlayerDataAsync(UUID uuid) {
        return CompletableFuture.completedFuture(EmakiResult.unavailable());
    }

    @Override
    public EmakiResult<List<LevelTopEntry>> top(String typeId, int limit) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<Integer> topCount(String typeId) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<LevelExpAdjustmentView> previewAdjustment(UUID uuid,
            String typeId,
            double amount,
            String reason) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<LevelOperationResult> addExp(UUID uuid, String typeId, double amount, String reason) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<LevelOperationResult> addExp(UUID uuid,
            String typeId,
            double amount,
            String reason,
            boolean silent) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<LevelOperationResult> removeExp(UUID uuid, String typeId, double amount, String reason) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<LevelOperationResult> setExp(UUID uuid, String typeId, double amount, String reason) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<LevelOperationResult> addLevel(UUID uuid, String typeId, int amount, String reason) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<LevelOperationResult> removeLevel(UUID uuid, String typeId, int amount, String reason) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<LevelOperationResult> setLevel(UUID uuid, String typeId, int level, String reason) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<LevelOperationResult> levelUp(UUID uuid, String typeId, LevelUpCause cause) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<LevelOperationResult> reset(UUID uuid, String typeId) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<Unit> syncPlayer(Player player) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<Unit> openGui(Player player, String typeId) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<Unit> openTopGui(Player player, String typeId) {
        return EmakiResult.unavailable();
    }

    @Override
    public ExpSourceRegistration registerExpSource(Plugin owner, ExpSourceProvider provider) {
        return ExpSourceRegistration.noop();
    }
}
