package emaki.jiuwu.craft.item.api;

import java.util.Map;
import java.util.Set;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.Unit;
import emaki.jiuwu.craft.corelib.api.item.ConfiguredItemDefinition;
import emaki.jiuwu.craft.item.api.model.ItemRefreshSummary;
import emaki.jiuwu.craft.item.api.model.MigrationOutcome;
import emaki.jiuwu.craft.item.api.model.MigrationPreview;
import emaki.jiuwu.craft.item.api.model.RepairOutcome;
import emaki.jiuwu.craft.item.api.model.RepairQuoteView;
import emaki.jiuwu.craft.item.api.preview.ItemLayerPreviewProvider;
import emaki.jiuwu.craft.item.api.preview.ItemLayerPreviewRegistration;

/** Shared no-op layers used while no EmakiItem runtime bridge is installed. */
final class UnavailableItem implements ItemCatalog, ItemOperations, ItemRepair, ItemMigration, ItemExtensions, ItemState {

    private static final UnavailableItem INSTANCE = new UnavailableItem();

    static final ItemCatalog CATALOG = INSTANCE;
    static final ItemOperations OPERATIONS = INSTANCE;
    static final ItemRepair REPAIR = INSTANCE;
    static final ItemMigration MIGRATION = INSTANCE;
    static final ItemExtensions EXTENSIONS = INSTANCE;
    static final ItemState STATE = INSTANCE;

    private UnavailableItem() {
    }

    @Override
    public Set<String> definitionIds() {
        return Set.of();
    }

    @Override
    public EmakiResult<ConfiguredItemDefinition> definition(String id) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<String> identify(ItemStack itemStack) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<String> displayName(String id) {
        return EmakiResult.unavailable();
    }

    @Override
    public boolean exists(String id) {
        return false;
    }

    @Override
    public EmakiResult<Boolean> conditionPasses(Player player, String itemId, String trigger, ItemStack itemStack) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<ItemStack> create(String id, int amount) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<ItemStack> refresh(ItemStack itemStack) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<ItemStack> forceRefresh(ItemStack itemStack) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<ItemRefreshSummary> refreshPlayer(Player player, String trigger) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<ItemRefreshSummary> refreshEquippedSets(Player player, String trigger) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<Unit> openRepairGui(Player player) {
        return EmakiResult.unavailable();
    }

    @Override
    public boolean isDisabled(ItemStack itemStack) {
        return false;
    }

    @Override
    public EmakiResult<Unit> markDisabled(ItemStack itemStack) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<Unit> clearDisabled(ItemStack itemStack) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<RepairQuoteView> quote(Player player, ItemStack itemStack) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<RepairOutcome> repair(Player player, ItemStack itemStack) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<MigrationPreview> preview(String oldId, String newId) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<MigrationOutcome> apply(String oldId, String newId,
            boolean replaceReferences, boolean keepAlias) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<Integer> migrateInventory(Player player) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<Integer> migrateAllOnline() {
        return EmakiResult.unavailable();
    }

    @Override
    public ItemLayerPreviewRegistration registerLayerPreview(Plugin owner, ItemLayerPreviewProvider provider) {
        return ItemLayerPreviewRegistration.noop();
    }

    @Override
    public void unregisterLayerPreviews(Plugin owner) {
        // No registrations exist in the unavailable implementation.
    }

    @Override
    public ItemStateSnapshot snapshot(ItemStack item) {
        return new ItemStateSnapshot(item, Map.of(), ItemStateMetadata.empty());
    }

    @Override
    public ItemStateSnapshot repair(ItemStack item) {
        return new ItemStateSnapshot(item, Map.of(), ItemStateMetadata.empty());
    }

    @Override
    public <T> java.util.Optional<T> get(ItemStack item, ItemStateKey<T> key) {
        return java.util.Optional.empty();
    }

    @Override
    public <T> ItemStateMutation<T> set(ItemStack item, ItemStateKey<T> key, T value) {
        return ItemStateMutation.rejected(key, "unavailable", null);
    }

    @Override
    public <T> ItemStateMutation<T> add(ItemStack item, ItemStateKey<T> key, Number amount) {
        return ItemStateMutation.rejected(key, "unavailable", null);
    }

    @Override
    public <T> ItemStateMutation<T> remove(ItemStack item, ItemStateKey<T> key) {
        return ItemStateMutation.rejected(key, "unavailable", null);
    }
}
