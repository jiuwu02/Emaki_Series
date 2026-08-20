package emaki.jiuwu.craft.strengthen.enhancement.affix;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import emaki.jiuwu.craft.strengthen.api.target.EnhancementTargetProvider;
import emaki.jiuwu.craft.strengthen.enhancement.mastery.MasteryLayer;
import emaki.jiuwu.craft.strengthen.enhancement.mastery.MasteryLayerCodec;
import emaki.jiuwu.craft.strengthen.integration.StrengthenAttributeBridge;

/**
 * 把「装备上的某一条词条」暴露为强化框架的目标类型。
 *
 * <p>按 ES-01 与整件星级强化完全分离：本 Provider 维护词条层（{@link AffixLayer}）及其
 * 独立结构化属性来源，<strong>不触碰整件装备的 {@code currentStar}</strong>。
 *
 * <p>字段映射：
 * <ul>
 *   <li>{@code level} → 当前选中词条的强化等级；</li>
 *   <li>{@code temper} → 词条层剩余容量，让配方公式能据此写条件；</li>
 *   <li>{@code recipeId} → 当前选中的词条 key。</li>
 * </ul>
 *
 * <p>「当前选中词条」由 {@link AffixSelectionService} 按玩家维护。强化框架优先调用带
 * {@link Player} 的 context-aware 重载；{@link #bindOperator} 仅保留给仍调用旧 item-only 方法的兼容路径。
 */
public final class AffixTargetProvider implements EnhancementTargetProvider {

    /** 与配方 {@code target.provider} 对应的 Provider ID。 */
    public static final String PROVIDER_ID = "affix";

    /** 词条强化独立属性来源，不能复用整件星级强化的来源。 */
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

    /**
     * 绑定当前线程上的操作者，使 {@code readLevel} 等方法能解析出「选中的词条」。
     *
     * <p>用 ThreadLocal 而非字段：强化在玩家所属实体线程上执行，Folia 下不同玩家可能并行，
     * 单个字段会串味。
     */
    public void bindOperator(@Nullable UUID playerId) {
        if (playerId == null) {
            operator.remove();
        } else {
            operator.set(playerId);
        }
    }

    /** 解绑当前线程的操作者。必须在一次交互结束时调用，否则会泄漏到后续任务。 */
    public void unbindOperator() {
        operator.remove();
    }

    @Override
    public @NotNull String id() {
        return PROVIDER_ID;
    }

    @Override
    public boolean canHandle(@Nullable ItemStack itemStack) {
        // 只认领「确实有可强化词条」的物品，避免遮蔽 equipment / gem 等其他目标类型。
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
            // 容量不足时不写入。执行服务会通过预写回 + read-back 在扣费前拦截，
            // 这里是 Provider 自身的第二道防线。
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

        // 先改独立属性来源，再写词条账本。通用执行服务在克隆件上调用本方法；若第二步失败，
        // read-back 仍会看到旧等级并拒绝提交，因此玩家物品不会出现半写状态。
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
        // temper 映射为「剩余容量」，是派生只读量，不接受直接写入。
    }

    @Override
    public void writeRecipeId(@Nullable ItemStack itemStack, @Nullable String recipeId) {
        // 选中词条由玩家在 GUI 中决定，不由强化流程改写。
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
