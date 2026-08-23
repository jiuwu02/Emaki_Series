package emaki.jiuwu.craft.strengthen.enhancement.affix;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.FailureKind;
import emaki.jiuwu.craft.corelib.api.contract.Unit;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.item.api.EmakiItemApi;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;
import emaki.jiuwu.craft.strengthen.api.model.ItemMasteryView;
import emaki.jiuwu.craft.strengthen.api.model.TargetSnapshotCategory;
import emaki.jiuwu.craft.strengthen.api.target.EnhancementTargetProvider;
import emaki.jiuwu.craft.strengthen.enhancement.mastery.MasteryLayer;
import emaki.jiuwu.craft.strengthen.enhancement.mastery.MasteryLayerCodec;
import emaki.jiuwu.craft.strengthen.integration.StrengthenAttributeBridge;

public final class AffixTargetProvider implements EnhancementTargetProvider {

    public static final String PROVIDER_ID = "affix";

    public static final String ATTRIBUTE_SOURCE_ID = "strengthen_affix";

    public static final String ERROR_BRIDGE_UNAVAILABLE = "strengthen.enhancement.attribute_bridge_unavailable";

    public static final String ERROR_REFRESH_FAILED = "strengthen.enhancement.refresh_failed";

    private static final String DEBUG_MODULE = "attempt";

    private final EmakiStrengthenPlugin plugin;
    private final AffixLayerCodec layerCodec;
    private final AffixSelectionService selectionService;
    private final StrengthenAttributeBridge attributeGateway;
    private final MasteryLayerCodec masteryCodec;
    private final ThreadLocal<UUID> operator = new ThreadLocal<>();

    public AffixTargetProvider(EmakiStrengthenPlugin plugin,
            AffixLayerCodec layerCodec,
            AffixSelectionService selectionService,
            StrengthenAttributeBridge attributeGateway) {
        this(plugin, layerCodec, selectionService, attributeGateway, null);
    }

    public AffixTargetProvider(EmakiStrengthenPlugin plugin,
            AffixLayerCodec layerCodec,
            AffixSelectionService selectionService,
            StrengthenAttributeBridge attributeGateway,
            @Nullable MasteryLayerCodec masteryCodec) {
        this.plugin = plugin;
        this.layerCodec = layerCodec;
        this.selectionService = selectionService;
        this.attributeGateway = attributeGateway;
        this.masteryCodec = masteryCodec;
    }

    @Override
    public @NotNull EmakiResult<ItemMasteryView> masterySnapshot(@Nullable ItemStack itemStack) {
        if (masteryCodec == null) {
            return EmakiResult.unavailable();
        }
        if (itemStack == null || itemStack.getType().isAir()) {
            return EmakiResult.invalidInput("strengthen.error.no_target");
        }
        MasteryLayer layer = masteryCodec.read(itemStack);
        return layer == null
                ? EmakiResult.notFound("strengthen.mastery.absent")
                : EmakiResult.success(layer.toView());
    }

    public void bindOperator(@Nullable UUID playerId) {
        if (playerId == null) {
            operator.remove();
        } else {
            operator.set(playerId);
        }
    }

    public void unbindOperator() {
        operator.remove();
    }

    @Override
    public @NotNull String id() {
        return PROVIDER_ID;
    }

    @Override
    public @NotNull Map<TargetSnapshotCategory, Set<String>> snapshotPartitions() {

        return Map.of(
                TargetSnapshotCategory.LAYER,
                Set.of(AffixLayerCodec.partitionPath(), AffixLayerCodec.legacyPartitionPath()),
                TargetSnapshotCategory.AUDIT,
                Set.of(MasteryLayerCodec.partitionPath(), MasteryLayerCodec.legacyPartitionPath()));
    }

    @Override
    public boolean canHandle(@Nullable ItemStack itemStack) {
        return !resolveEnhanceableCandidates(itemStack).isEmpty();
    }

    @Override
    public int readLevel(@Nullable ItemStack itemStack) {
        return readLevel(itemStack, operator.get());
    }

    @Override
    public int readLevel(@Nullable Player player, @Nullable ItemStack itemStack) {
        return readLevel(itemStack, playerId(player));
    }

    @Override
    public int readTemper(@Nullable ItemStack itemStack) {
        return layerCodec.readOrEmpty(itemStack, defaultCapacityMax()).capacityRemaining();
    }

    @Override
    public @NotNull String readRecipeId(@Nullable ItemStack itemStack) {
        return resolveSelection(itemStack, operator.get());
    }

    @Override
    public @NotNull String readRecipeId(@Nullable Player player, @Nullable ItemStack itemStack) {
        return resolveSelection(itemStack, playerId(player));
    }

    @Override
    public void writeLevel(@Nullable ItemStack itemStack, int level) {
        writeLevel(itemStack, level, operator.get());
    }

    @Override
    public void writeLevel(@Nullable Player player, @Nullable ItemStack itemStack, int level) {
        writeLevel(itemStack, level, playerId(player));
    }

    @Override
    public @NotNull EmakiResult<Unit> refreshPresentation(@Nullable Player player,
            @Nullable ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return EmakiResult.invalidInput(ERROR_REFRESH_FAILED);
        }
        if (!attributeGateway.available()) {
            logBridgeUnavailable(player, "refresh_presentation");
            return EmakiResult.failure(FailureKind.REJECTED, ERROR_BRIDGE_UNAVAILABLE);
        }
        AffixLayer layer = layerCodec.readOrEmpty(itemStack, defaultCapacityMax());
        if (!syncAttributePayload(itemStack, layer)) {
            logRefreshFailure(player, itemStack, "attribute_payload_mismatch");
            return EmakiResult.failure(FailureKind.INTERNAL_ERROR, ERROR_REFRESH_FAILED);
        }
        return refreshItemPresentation(player, itemStack);
    }

    private @NotNull EmakiResult<Unit> refreshItemPresentation(@Nullable Player player, ItemStack itemStack) {
        try {
            if (!EmakiItemApi.status().usable()) {
                return EmakiResult.ok();
            }
            EmakiResult<ItemStack> refreshed = EmakiItemApi.operations().refresh(itemStack);
            ItemStack rebuilt = refreshed.optionalValue().orElse(null);
            if (rebuilt == null) {
                return EmakiResult.ok();
            }
            ItemMeta rebuiltMeta = rebuilt.getItemMeta();
            if (rebuiltMeta != null && !itemStack.setItemMeta(rebuiltMeta)) {
                logRefreshFailure(player, itemStack, "lore_commit_rejected");
                return EmakiResult.failure(FailureKind.INTERNAL_ERROR, ERROR_REFRESH_FAILED);
            }
            return EmakiResult.ok();
        } catch (RuntimeException | LinkageError exception) {
            logRefreshFailure(player, itemStack, exception.getClass().getSimpleName());
            return EmakiResult.failure(FailureKind.INTERNAL_ERROR, ERROR_REFRESH_FAILED);
        }
    }

    private void writeLevel(@Nullable ItemStack itemStack, int level, @Nullable UUID playerId) {
        String affixKey = resolveSelection(itemStack, playerId);
        if (Texts.isBlank(affixKey) || itemStack == null) {
            return;
        }
        AffixLayer storedLayer = layerCodec.read(itemStack);
        AffixLayer currentLayer = storedLayer == null ? AffixLayer.empty(defaultCapacityMax()) : storedLayer;
        AffixState current = currentLayer.affix(affixKey);
        int targetLevel = normalizeTargetLevel(level);
        if (targetLevel == current.level()) {
            return;
        }

        AffixLayer nextLayer;
        if (targetLevel <= 0) {
            nextLayer = currentLayer.without(affixKey);
        } else if (targetLevel > current.level()) {
            int levelDelta = targetLevel - current.level();
            long capacityDeltaLong = (long) capacityCostPerLevel() * levelDelta;
            double bonusDelta = bonusPerLevel() * levelDelta;
            if (capacityDeltaLong > Integer.MAX_VALUE || !Double.isFinite(bonusDelta)) {
                return;
            }
            int capacityDelta = (int) capacityDeltaLong;
            if (!currentLayer.canAfford(capacityDelta)) {
                return;
            }
            nextLayer = currentLayer.with(new AffixState(
                    affixKey,
                    targetLevel,
                    current.bonus() + bonusDelta,
                    current.capacityCost() + capacityDelta
            ));
        } else {
            int levelDelta = current.level() - targetLevel;
            long capacityDelta = (long) capacityCostPerLevel() * levelDelta;
            double bonusDelta = bonusPerLevel() * levelDelta;
            if (!Double.isFinite(bonusDelta)) {
                return;
            }
            nextLayer = currentLayer.with(new AffixState(
                    affixKey,
                    targetLevel,
                    current.bonus() - bonusDelta,
                    (int) Math.max(0L, current.capacityCost() - capacityDelta)
            ));
        }

        if (!syncAttributePayload(itemStack, nextLayer)) {
            return;
        }
        if (!layerCodec.write(itemStack, nextLayer) || !nextLayer.equals(layerCodec.read(itemStack))) {
            syncAttributePayload(itemStack, currentLayer);
            if (storedLayer == null) {
                layerCodec.clear(itemStack);
            } else {
                layerCodec.write(itemStack, currentLayer);
            }
        }
    }

    @Override
    public void writeTemper(@Nullable ItemStack itemStack, int temper) {
    }

    @Override
    public void writeRecipeId(@Nullable ItemStack itemStack, @Nullable String recipeId) {
    }

    @Override
    public void clearEnhancement(@Nullable ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return;
        }
        if (!attributeGateway.available()) {
            logBridgeUnavailable(null, "clear_enhancement");
            return;
        }
        AffixLayer currentLayer = layerCodec.read(itemStack);
        int capacityMax = currentLayer == null ? defaultCapacityMax() : currentLayer.capacityMax();
        if (!syncAttributePayload(itemStack, AffixLayer.empty(capacityMax))) {
            return;
        }
        layerCodec.clear(itemStack);
        if (layerCodec.read(itemStack) != null && currentLayer != null) {
            syncAttributePayload(itemStack, currentLayer);
        }
    }

    private List<String> resolveEnhanceableCandidates(ItemStack itemStack) {
        return selectionService.enhanceableAffixes(itemStack, maxAffixLevel());
    }

    private int readLevel(@Nullable ItemStack itemStack, @Nullable UUID playerId) {
        String affixKey = resolveSelection(itemStack, playerId);
        if (Texts.isBlank(affixKey)) {
            return 0;
        }
        return layerCodec.readOrEmpty(itemStack, defaultCapacityMax()).affix(affixKey).level();
    }

    private @NotNull String resolveSelection(@Nullable ItemStack itemStack, @Nullable UUID playerId) {
        return selectionService.selected(playerId, selectionService.affixes(itemStack));
    }

    private static @Nullable UUID playerId(@Nullable Player player) {
        return player == null ? null : player.getUniqueId();
    }

    private int normalizeTargetLevel(int level) {
        int normalized = Math.max(0, level);
        int maxLevel = maxAffixLevel();
        return maxLevel <= 0 ? normalized : Math.min(normalized, maxLevel);
    }

    private void logBridgeUnavailable(@Nullable Player player, String stage) {
        if (plugin == null || plugin.debugLogger() == null) {
            return;
        }
        plugin.debugLogger().log(DEBUG_MODULE, player, "debug.affix.bridge_unavailable", Map.of(
                "stage", stage,
                "source", ATTRIBUTE_SOURCE_ID,
                "error_key", ERROR_BRIDGE_UNAVAILABLE));
        plugin.getLogger().warning("词条强化属性桥不可用 | 阶段=" + stage
                + " | 来源=" + ATTRIBUTE_SOURCE_ID + " | 错误键=" + ERROR_BRIDGE_UNAVAILABLE);
    }

    private void logRefreshFailure(@Nullable Player player, ItemStack itemStack, String reason) {
        if (plugin == null || plugin.debugLogger() == null) {
            return;
        }
        plugin.debugLogger().log(DEBUG_MODULE, player, "debug.affix.refresh_failed", Map.of(
                "reason", reason,
                "item", itemStack == null ? "-" : itemStack.getType().name(),
                "error_key", ERROR_REFRESH_FAILED));
        plugin.getLogger().warning("词条强化刷新失败 | 原因=" + reason
                + " | 物品=" + (itemStack == null ? "-" : itemStack.getType().name())
                + " | 错误键=" + ERROR_REFRESH_FAILED);
    }

    private boolean syncAttributePayload(ItemStack itemStack, AffixLayer layer) {
        if (itemStack == null || itemStack.getType().isAir() || layer == null) {
            return false;
        }
        if (!attributeGateway.available()) {
            logBridgeUnavailable(null, "sync_attribute_payload");
            return false;
        }
        Map<String, Double> attributes = new LinkedHashMap<>();
        for (AffixState state : layer.affixes().values()) {
            if (state.enhanced()) {
                attributes.put(state.attributeKey(), state.bonus());
            }
        }
        if (attributes.isEmpty()) {
            boolean hasPayload = attributeGateway.readAllAttributes(itemStack).containsKey(ATTRIBUTE_SOURCE_ID);
            if (hasPayload && !attributeGateway.clear(itemStack, ATTRIBUTE_SOURCE_ID)) {
                return false;
            }
            return !attributeGateway.readAllAttributes(itemStack).containsKey(ATTRIBUTE_SOURCE_ID);
        }
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("layer", PROVIDER_ID);
        meta.put("capacity_max", String.valueOf(layer.capacityMax()));
        meta.put("capacity_used", String.valueOf(layer.capacityUsed()));
        if (!attributeGateway.write(itemStack, ATTRIBUTE_SOURCE_ID, attributes, meta)) {
            return false;
        }
        return attributes.equals(attributeGateway.readAllAttributes(itemStack).get(ATTRIBUTE_SOURCE_ID));
    }

    private int maxAffixLevel() {
        return plugin == null || plugin.appConfig() == null ? 0 : plugin.appConfig().affixMaxLevel();
    }

    private int defaultCapacityMax() {
        return plugin == null || plugin.appConfig() == null ? 0 : plugin.appConfig().affixCapacityMax();
    }

    private int capacityCostPerLevel() {
        return plugin == null || plugin.appConfig() == null ? 1 : plugin.appConfig().affixCapacityCostPerLevel();
    }

    private double bonusPerLevel() {
        return plugin == null || plugin.appConfig() == null ? 0D : plugin.appConfig().affixBonusPerLevel();
    }
}
