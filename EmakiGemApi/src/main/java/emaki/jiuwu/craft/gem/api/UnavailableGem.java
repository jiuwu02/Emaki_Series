package emaki.jiuwu.craft.gem.api;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.Unit;
import emaki.jiuwu.craft.gem.api.model.GemDefinitionView;
import emaki.jiuwu.craft.gem.api.model.GemExtractOutcome;
import emaki.jiuwu.craft.gem.api.model.GemInlayOutcome;
import emaki.jiuwu.craft.gem.api.model.GemRelationshipCheck;
import emaki.jiuwu.craft.gem.api.model.GemResonanceView;
import emaki.jiuwu.craft.gem.api.model.GemSocketOpenOutcome;
import emaki.jiuwu.craft.gem.api.model.GemStateView;

/**
 * Layers returned when EmakiGem is not installed.
 *
 * <p>Queries answer empty and operations report
 * {@link emaki.jiuwu.craft.corelib.api.contract.FailureKind#UNAVAILABLE}, so callers never need a null
 * check on {@code EmakiGemApi.catalog()} or {@code operations()}.
 */
final class UnavailableGem implements GemCatalog, GemOperations {

    static final GemCatalog CATALOG = new UnavailableGem();
    static final GemOperations OPERATIONS = (GemOperations) CATALOG;

    private UnavailableGem() {
    }

    @Override
    public List<String> gemIds() {
        return List.of();
    }

    @Override
    public Optional<GemDefinitionView> gem(String gemId, int level) {
        return Optional.empty();
    }

    @Override
    public Optional<GemDefinitionView> identifyGem(ItemStack itemStack) {
        return Optional.empty();
    }

    @Override
    public boolean isOpenerItem(ItemStack itemStack) {
        return false;
    }

    @Override
    public EmakiResult<GemStateView> state(ItemStack equipment) {
        return EmakiResult.unavailable();
    }

    @Override
    public Map<String, Double> aggregatedAttributes(ItemStack equipment) {
        return Map.of();
    }

    @Override
    public List<String> aggregatedSkillIds(ItemStack equipment) {
        return List.of();
    }

    @Override
    public List<GemResonanceView> resonances(ItemStack equipment) {
        return List.of();
    }

    @Override
    public EmakiResult<GemRelationshipCheck> canInlay(ItemStack equipment, String gemId, int slotIndex) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<GemRelationshipCheck> canExtract(ItemStack equipment, int slotIndex) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<GemInlayOutcome> inlay(Player actor,
            ItemStack equipment,
            ItemStack gemItem,
            int slotIndex,
            boolean bypassCost) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<GemExtractOutcome> extract(Player actor, ItemStack equipment, int slotIndex, boolean bypassCost) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<GemSocketOpenOutcome> openSocket(Player actor,
            ItemStack equipment,
            ItemStack openerItem,
            int slotIndex,
            boolean bypassRequirement) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<ItemStack> createGemItem(String gemId, int level, int amount) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<ItemStack> clearGems(ItemStack equipment) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<Unit> openGui(Player player) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<Unit> openSocketGui(Player player, ItemStack target) {
        return EmakiResult.unavailable();
    }
}
