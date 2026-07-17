package emaki.jiuwu.craft.level.apiimpl;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import emaki.jiuwu.craft.level.EmakiLevelPlugin;
import emaki.jiuwu.craft.level.api.EmakiLevelApi;
import emaki.jiuwu.craft.level.api.LevelOperationResult;
import emaki.jiuwu.craft.level.api.LevelTypeView;
import emaki.jiuwu.craft.level.api.LevelUpCause;
import emaki.jiuwu.craft.level.api.PlayerLevelEntryView;
import emaki.jiuwu.craft.level.api.PlayerLevelView;
import emaki.jiuwu.craft.level.config.LevelTypeConfig;
import emaki.jiuwu.craft.level.model.PlayerLevelData;
import emaki.jiuwu.craft.level.model.PlayerLevelEntry;

public final class DefaultEmakiLevelApi implements EmakiLevelApi.Bridge {

    private final EmakiLevelPlugin plugin;

    public DefaultEmakiLevelApi(EmakiLevelPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public Optional<LevelTypeView> type(String typeId) {
        return plugin.typeRegistry().type(typeId).map(this::view);
    }

    @Override
    public Collection<LevelTypeView> types() {
        return plugin.typeRegistry().all().stream().map(this::view).toList();
    }

    @Override
    public CompletableFuture<PlayerLevelView> getPlayerData(UUID uuid) {
        return plugin.dataStore().getOrLoadAsync(uuid, plugin.typeRegistry().asMap())
                .thenApply(data -> data == null
                        ? new PlayerLevelView(uuid, "", Map.of())
                        : playerView(data));
    }

    @Override
    public int getLevel(UUID uuid, String typeId) {
        PlayerLevelEntry entry = entry(uuid, typeId);
        return entry == null ? 0 : entry.level();
    }

    @Override
    public double getExp(UUID uuid, String typeId) {
        PlayerLevelEntry entry = entry(uuid, typeId);
        return entry == null ? 0D : entry.exp();
    }

    @Override
    public double getTotalExp(UUID uuid, String typeId) {
        PlayerLevelEntry entry = entry(uuid, typeId);
        return entry == null ? 0D : entry.totalExp();
    }

    @Override
    public double getRequiredExp(UUID uuid, String typeId, int targetLevel) {
        LevelTypeConfig type = plugin.typeRegistry().type(typeId).orElse(null);
        if (type == null) {
            return 0D;
        }
        return plugin.requirementService().requiredExp(type, entry(uuid, typeId), targetLevel);
    }

    @Override
    public LevelOperationResult addExp(UUID uuid, String typeId, double amount, String reason) {
        return plugin.levelService().addExp(uuid, typeId, amount, reason);
    }

    @Override
    public LevelOperationResult removeExp(UUID uuid, String typeId, double amount, String reason) {
        return plugin.levelService().removeExp(uuid, typeId, amount, reason);
    }

    @Override
    public LevelOperationResult setExp(UUID uuid, String typeId, double amount, String reason) {
        return plugin.levelService().setExp(uuid, typeId, amount, reason);
    }

    @Override
    public LevelOperationResult addLevel(UUID uuid, String typeId, int amount, String reason) {
        return plugin.levelService().addLevel(uuid, typeId, amount, reason);
    }

    @Override
    public LevelOperationResult removeLevel(UUID uuid, String typeId, int amount, String reason) {
        return plugin.levelService().removeLevel(uuid, typeId, amount, reason);
    }

    @Override
    public LevelOperationResult setLevel(UUID uuid, String typeId, int level, String reason) {
        return plugin.levelService().setLevel(uuid, typeId, level, reason);
    }

    @Override
    public LevelOperationResult levelUp(UUID uuid, String typeId, LevelUpCause cause) {
        return plugin.levelService().levelUp(uuid, typeId, cause);
    }

    private PlayerLevelEntry entry(UUID uuid, String typeId) {
        if (uuid == null) {
            return null;
        }
        PlayerLevelData data = plugin.dataStore().cached(uuid);
        return data == null ? null : data.entry(emaki.jiuwu.craft.corelib.text.Texts.normalizeId(typeId));
    }

    private LevelTypeView view(LevelTypeConfig type) {
        return new LevelTypeView(type.id(), type.displayName(), type.description(), type.primary(), type.enabled(), type.startLevel(), type.maxLevel(), type.upgrade().autoUpgrade(), type.upgrade().manualUpgrade(), type.attributes());
    }

    private PlayerLevelView playerView(PlayerLevelData data) {
        Map<String, PlayerLevelEntryView> entries = new LinkedHashMap<>();
        for (LevelTypeConfig type : plugin.typeRegistry().all()) {
            PlayerLevelEntry entry = data.entry(type.id());
            if (entry == null) {
                continue;
            }
            double required = plugin.requirementService().requiredExp(type, entry, Math.min(type.maxLevel(), entry.level() + 1));
            double progress = required <= 0D ? 1D : Math.min(1D, entry.exp() / required);
            entries.put(type.id(), new PlayerLevelEntryView(type.id(), entry.level(), entry.exp(), entry.totalExp(), required, progress));
        }
        return new PlayerLevelView(data.uuid(), data.name(), entries);
    }
}
