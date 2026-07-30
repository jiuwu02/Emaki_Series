package emaki.jiuwu.craft.level.apiimpl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.FailureKind;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.level.EmakiLevelPlugin;
import emaki.jiuwu.craft.level.api.LevelCatalog;
import emaki.jiuwu.craft.level.api.LevelExpAdjustmentView;
import emaki.jiuwu.craft.level.api.LevelTopEntry;
import emaki.jiuwu.craft.level.api.LevelTypeView;
import emaki.jiuwu.craft.level.api.PlayerLevelEntryView;
import emaki.jiuwu.craft.level.api.PlayerLevelView;
import emaki.jiuwu.craft.level.config.LevelTypeConfig;
import emaki.jiuwu.craft.level.model.PlayerLevelData;
import emaki.jiuwu.craft.level.model.PlayerLevelEntry;
import emaki.jiuwu.craft.level.service.LevelExperienceRuleService;
import emaki.jiuwu.craft.level.service.LevelTopService;

/** Default read-only API adapter. */
public final class DefaultLevelCatalog implements LevelCatalog {

    private final EmakiLevelPlugin plugin;

    public DefaultLevelCatalog(EmakiLevelPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<LevelTypeView> types() {
        if (plugin == null || plugin.typeRegistry() == null) {
            return List.of();
        }
        return plugin.typeRegistry().all().stream().map(DefaultLevelCatalog::view).toList();
    }

    @Override
    public Optional<LevelTypeView> type(String typeId) {
        if (plugin == null || plugin.typeRegistry() == null || Texts.isBlank(typeId)) {
            return Optional.empty();
        }
        return plugin.typeRegistry().type(typeId).map(DefaultLevelCatalog::view);
    }

    @Override
    public EmakiResult<Integer> level(UUID uuid, String typeId) {
        EmakiResult<PlayerLevelEntry> entry = entry(uuid, typeId);
        return entry.hasValue()
                ? EmakiResult.success(entry.orElse(null).level())
                : entry.retypeFailure();
    }

    @Override
    public EmakiResult<Double> exp(UUID uuid, String typeId) {
        EmakiResult<PlayerLevelEntry> entry = entry(uuid, typeId);
        return entry.hasValue()
                ? EmakiResult.success(entry.orElse(null).exp())
                : entry.retypeFailure();
    }

    @Override
    public EmakiResult<Double> totalExp(UUID uuid, String typeId) {
        EmakiResult<PlayerLevelEntry> entry = entry(uuid, typeId);
        return entry.hasValue()
                ? EmakiResult.success(entry.orElse(null).totalExp())
                : entry.retypeFailure();
    }

    @Override
    public EmakiResult<Double> requiredExp(UUID uuid, String typeId, int targetLevel) {
        if (targetLevel < 0) {
            return EmakiResult.invalidInput("level.target_level_invalid");
        }
        EmakiResult<PlayerLevelEntry> entryResult = entry(uuid, typeId);
        if (entryResult.isFailure()) {
            return entryResult.retypeFailure();
        }
        LevelTypeConfig type = plugin.typeRegistry().type(typeId).orElse(null);
        if (type == null) {
            return EmakiResult.notFound("level.type_not_found");
        }
        if (targetLevel < type.startLevel() || targetLevel > type.maxLevel()) {
            return EmakiResult.invalidInput("level.target_level_out_of_range");
        }
        try {
            return EmakiResult.success(plugin.requirementService().requiredExp(type, entryResult.orElse(null), targetLevel));
        } catch (RuntimeException exception) {
            return EmakiResult.internalError("level.required_exp_failed");
        }
    }

    @Override
    public CompletableFuture<EmakiResult<PlayerLevelView>> loadPlayerDataAsync(UUID uuid) {
        if (uuid == null) {
            return CompletableFuture.completedFuture(EmakiResult.invalidInput("level.player_uuid_required"));
        }
        try {
            return plugin.dataStore().getOrLoadAsync(uuid, plugin.typeRegistry().asMap())
                    .thenApply(data -> data == null
                            ? EmakiResult.<PlayerLevelView>internalError("level.player_data_load_failed")
                            : EmakiResult.success(playerView(plugin, data)))
                    .exceptionally(throwable -> EmakiResult.internalError("level.player_data_load_failed"));
        } catch (RuntimeException exception) {
            return CompletableFuture.completedFuture(EmakiResult.internalError("level.player_data_load_failed"));
        }
    }

    @Override
    public EmakiResult<List<LevelTopEntry>> top(String typeId, int limit) {
        EmakiResult<LevelTypeConfig> typeResult = typeConfig(typeId);
        if (typeResult.isFailure()) {
            return typeResult.retypeFailure();
        }
        if (limit <= 0) {
            return EmakiResult.invalidInput("level.top_limit_invalid");
        }
        try {
            List<LevelTopEntry> entries = plugin.topService().top(typeId, limit).stream()
                    .map(DefaultLevelCatalog::topView)
                    .toList();
            return EmakiResult.success(entries);
        } catch (RuntimeException exception) {
            return EmakiResult.internalError("level.top_read_failed");
        }
    }

    @Override
    public EmakiResult<Integer> topCount(String typeId) {
        EmakiResult<LevelTypeConfig> typeResult = typeConfig(typeId);
        if (typeResult.isFailure()) {
            return typeResult.retypeFailure();
        }
        try {
            return EmakiResult.success(plugin.topService().count(typeId));
        } catch (RuntimeException exception) {
            return EmakiResult.internalError("level.top_count_failed");
        }
    }

    @Override
    public EmakiResult<LevelExpAdjustmentView> previewAdjustment(UUID uuid,
            String typeId,
            double amount,
            String reason) {
        if (uuid == null) {
            return EmakiResult.invalidInput("level.player_uuid_required");
        }
        if (!Double.isFinite(amount) || amount <= 0D) {
            return EmakiResult.invalidInput("level.amount_invalid");
        }
        EmakiResult<LevelTypeConfig> typeResult = typeConfig(typeId);
        if (typeResult.isFailure()) {
            return typeResult.retypeFailure();
        }
        try {
            LevelExperienceRuleService.LevelExperienceAdjustment adjustment = plugin.experienceRuleService()
                    .preview(uuid, typeResult.orElse(null).id(), amount, reason);
            return EmakiResult.success(adjustmentView(adjustment));
        } catch (RuntimeException exception) {
            return EmakiResult.internalError("level.adjustment_preview_failed");
        }
    }

    private EmakiResult<PlayerLevelEntry> entry(UUID uuid, String typeId) {
        if (uuid == null) {
            return EmakiResult.invalidInput("level.player_uuid_required");
        }
        EmakiResult<LevelTypeConfig> typeResult = typeConfig(typeId);
        if (typeResult.isFailure()) {
            return typeResult.retypeFailure();
        }
        PlayerLevelData data = plugin.dataStore().cached(uuid);
        if (data == null) {
            return EmakiResult.failure(FailureKind.NOT_FOUND, "level.player_data_not_found");
        }
        PlayerLevelEntry entry = data.entry(typeResult.orElse(null).id());
        return entry == null
                ? EmakiResult.notFound("level.player_level_not_found")
                : EmakiResult.success(entry);
    }

    private EmakiResult<LevelTypeConfig> typeConfig(String typeId) {
        if (plugin == null || plugin.typeRegistry() == null) {
            return EmakiResult.unavailable();
        }
        if (Texts.isBlank(typeId)) {
            return EmakiResult.invalidInput("level.type_id_required");
        }
        LevelTypeConfig type = plugin.typeRegistry().type(typeId).orElse(null);
        return type == null
                ? EmakiResult.notFound("level.type_not_found")
                : EmakiResult.success(type);
    }

    private static LevelTypeView view(LevelTypeConfig type) {
        return new LevelTypeView(type.id(),
                type.displayName(),
                type.description(),
                type.primary(),
                type.enabled(),
                type.startLevel(),
                type.maxLevel(),
                type.upgrade().autoUpgrade(),
                type.upgrade().manualUpgrade(),
                type.attributes());
    }

    private static LevelTopEntry topView(LevelTopService.TopEntry entry) {
        return new LevelTopEntry(entry.uuid(),
                entry.name(),
                entry.typeId(),
                entry.level(),
                entry.exp(),
                entry.totalExp(),
                entry.updatedAt());
    }

    private static LevelExpAdjustmentView adjustmentView(
            LevelExperienceRuleService.LevelExperienceAdjustment adjustment) {
        return new LevelExpAdjustmentView(adjustment.originalAmount(),
                adjustment.multiplier(),
                adjustment.multipliedAmount(),
                adjustment.dailyLimit(),
                adjustment.gainedToday(),
                adjustment.actualAmount(),
                adjustment.reason());
    }

    private static PlayerLevelView playerView(EmakiLevelPlugin plugin, PlayerLevelData data) {
        Map<String, PlayerLevelEntryView> entries = new LinkedHashMap<>();
        for (LevelTypeConfig type : plugin.typeRegistry().all()) {
            PlayerLevelEntry entry = data.entry(type.id());
            if (entry == null) {
                continue;
            }
            double required = plugin.requirementService().requiredExp(
                    type, entry, Math.min(type.maxLevel(), entry.level() + 1));
            double progress = required <= 0D ? 1D : Math.min(1D, entry.exp() / required);
            entries.put(type.id(), new PlayerLevelEntryView(
                    type.id(), entry.level(), entry.exp(), entry.totalExp(), required, progress));
        }
        return new PlayerLevelView(data.uuid(), data.name(), entries);
    }
}
