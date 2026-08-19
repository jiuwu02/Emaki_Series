package emaki.jiuwu.craft.gem.api;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.Unit;
import emaki.jiuwu.craft.gem.api.model.GemDefinitionView;
import emaki.jiuwu.craft.gem.api.model.GemExtractOutcome;
import emaki.jiuwu.craft.gem.api.model.GemInlayOutcome;
import emaki.jiuwu.craft.gem.api.model.GemRelationshipCheck;
import emaki.jiuwu.craft.gem.api.model.GemRerollSessionView;
import emaki.jiuwu.craft.gem.api.model.GemResonanceView;
import emaki.jiuwu.craft.gem.api.model.GemStateView;

/** No-op layers used while EmakiGem has no installed bridge. */
final class UnavailableGem implements GemCatalog, GemOperations {

    private static final UnavailableGem INSTANCE = new UnavailableGem();

    static final GemCatalog CATALOG = INSTANCE;
    static final GemOperations OPERATIONS = INSTANCE;

    private UnavailableGem() {
    }

    @Override
    public Optional<GemStateView> readState(ItemStack equipment) {
        return Optional.empty();
    }

    @Override
    public boolean isGemItem(ItemStack itemStack) {
        return false;
    }

    @Override
    public boolean isOpenerItem(ItemStack itemStack) {
        return false;
    }

    @Override
    public Optional<GemDefinitionView> definition(String gemId) {
        return Optional.empty();
    }

    @Override
    public List<GemDefinitionView> definitions() {
        return List.of();
    }

    @Override
    public EmakiResult<GemRelationshipCheck> canInlay(ItemStack equipment, ItemStack gemItem) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<GemRelationshipCheck> canExtract(ItemStack equipment, int slotIndex) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<GemResonanceView> resonance(ItemStack equipment) {
        return EmakiResult.unavailable();
    }

    @Override
    public Map<String, Double> aggregatedAttributes(ItemStack equipment) {
        return Map.of();
    }

    @Override
    public Set<String> aggregatedSkillIds(ItemStack equipment) {
        return Set.of();
    }

    @Override
    public Optional<GemRerollSessionView> rerollSession(java.util.UUID operatorId) {
        return Optional.empty();
    }

    @Override
    public EmakiResult<GemInlayOutcome> inlay(Player actor, ItemStack equipment, ItemStack gemItem, int slotIndex) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<GemExtractOutcome> extract(Player actor, ItemStack equipment, int slotIndex, boolean bypassCost) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<ItemStack> openSocket(Player actor, ItemStack equipment, ItemStack openerItem) {
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
