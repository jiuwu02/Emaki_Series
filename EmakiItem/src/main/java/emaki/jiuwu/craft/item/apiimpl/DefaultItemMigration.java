package emaki.jiuwu.craft.item.apiimpl;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.item.EmakiItemPlugin;
import emaki.jiuwu.craft.item.api.ItemMigration;
import emaki.jiuwu.craft.item.api.model.MigrationFileView;
import emaki.jiuwu.craft.item.api.model.MigrationOutcome;
import emaki.jiuwu.craft.item.api.model.MigrationPreview;
import emaki.jiuwu.craft.item.service.EmakiItemMigrationService;

/** Runtime-backed {@link ItemMigration}. */
public final class DefaultItemMigration implements ItemMigration {

    private final EmakiItemPlugin plugin;

    public DefaultItemMigration(EmakiItemPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull EmakiResult<MigrationPreview> preview(@Nullable String oldId,
            @Nullable String newId) {
        EmakiResult<NormalizedIds> ids = ids(oldId, newId, false);
        if (ids.isFailure()) {
            return ids.retypeFailure();
        }
        if (plugin.migrationService() == null) {
            return EmakiResult.unavailable();
        }
        try {
            return EmakiResult.success(toPreview(plugin.migrationService().preview(oldId, newId)));
        } catch (IOException exception) {
            return EmakiResult.internalError("item.migration.preview_io_error");
        } catch (RuntimeException exception) {
            return EmakiResult.internalError("item.migration.preview_internal_error");
        }
    }

    @Override
    public @NotNull EmakiResult<MigrationOutcome> apply(@Nullable String oldId,
            @Nullable String newId,
            boolean replaceReferences,
            boolean keepAlias) {
        EmakiResult<NormalizedIds> ids = ids(oldId, newId, true);
        if (ids.isFailure()) {
            return ids.retypeFailure();
        }
        if (plugin.migrationService() == null) {
            return EmakiResult.unavailable();
        }
        try {
            return EmakiResult.success(toOutcome(plugin.migrationService()
                    .apply(oldId, newId, replaceReferences, keepAlias)));
        } catch (EmakiItemMigrationService.PartialMigrationException exception) {
            return EmakiResult.partial(toOutcome(exception.outcome()), "item.migration.partial");
        } catch (IOException exception) {
            return EmakiResult.internalError("item.migration.apply_io_error");
        } catch (RuntimeException exception) {
            return EmakiResult.internalError("item.migration.apply_internal_error");
        }
    }

    @Override
    public @NotNull EmakiResult<Integer> migrateInventory(@Nullable Player player) {
        if (player == null) {
            return EmakiResult.invalidInput("item.player.required");
        }
        if (!player.isOnline()) {
            return EmakiResult.targetOffline();
        }
        if (plugin.migrationService() == null || plugin.threadOwnership() == null) {
            return EmakiResult.unavailable();
        }
        if (!plugin.threadOwnership().isEntityOwned(player)) {
            return EmakiResult.wrongThread();
        }
        try {
            return EmakiResult.success(plugin.migrationService().migrateInventory(player));
        } catch (RuntimeException exception) {
            return EmakiResult.internalError("item.migration.inventory_internal_error");
        }
    }

    @Override
    public @NotNull EmakiResult<Integer> migrateAllOnline() {
        if (plugin.migrationService() == null || plugin.threadOwnership() == null) {
            return EmakiResult.unavailable();
        }
        try {
            EmakiItemMigrationService.OnlineMigrationResult result =
                    plugin.migrationService().migrateOwnedOnlineInventories();
            return result.skippedPlayers() > 0
                    ? EmakiResult.partial(result.changedItems(), "item.migration.players_wrong_thread")
                    : EmakiResult.success(result.changedItems());
        } catch (RuntimeException exception) {
            return EmakiResult.internalError("item.migration.online_internal_error");
        }
    }

    private EmakiResult<NormalizedIds> ids(String oldId, String newId, boolean requireTarget) {
        String oldNormalized = Texts.normalizeId(oldId);
        String newNormalized = Texts.normalizeId(newId);
        if (Texts.isBlank(oldNormalized) || Texts.isBlank(newNormalized) || oldNormalized.equals(newNormalized)) {
            return EmakiResult.invalidInput("item.migration.ids_invalid");
        }
        if (plugin.itemLoader() == null || plugin.aliasLoader() == null) {
            return EmakiResult.unavailable();
        }
        if (requireTarget && plugin.itemLoader().get(newNormalized) == null) {
            return EmakiResult.notFound("item.migration.target_not_found");
        }
        return EmakiResult.success(new NormalizedIds(oldNormalized, newNormalized));
    }

    private MigrationPreview toPreview(Map<String, Object> map) {
        return new MigrationPreview(
                string(map, "oldId"),
                string(map, "newId"),
                bool(map, "oldExists"),
                bool(map, "newExists"),
                bool(map, "aliasExists"),
                number(map, "aliasRevision").longValue(),
                files(map.get("files")),
                number(map, "replacementCount").intValue());
    }

    private MigrationOutcome toOutcome(Map<String, Object> map) {
        return new MigrationOutcome(
                string(map, "oldId"),
                string(map, "newId"),
                files(map.get("changedFiles")),
                number(map, "replacementCount").intValue(),
                bool(map, "aliasKept"),
                number(map, "aliasRevision").longValue());
    }

    private List<MigrationFileView> files(Object raw) {
        if (!(raw instanceof List<?> values)) {
            return List.of();
        }
        return values.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(value -> new MigrationFileView(
                        string(value, "moduleId"),
                        string(value, "path"),
                        string(value, "kind"),
                        number(value, "replacements").intValue(),
                        number(value, "revision").longValue()))
                .toList();
    }

    private String string(Map<?, ?> map, String key) {
        Object value = map == null ? null : map.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private boolean bool(Map<?, ?> map, String key) {
        Object value = map == null ? null : map.get(key);
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
    }

    private Number number(Map<?, ?> map, String key) {
        Object value = map == null ? null : map.get(key);
        return value instanceof Number number ? number : 0L;
    }

    private record NormalizedIds(String oldId, String newId) {
    }
}
