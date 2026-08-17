package emaki.jiuwu.craft.strengthen.enhancement.affix;

import java.util.List;
import java.util.UUID;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;
import emaki.jiuwu.craft.strengthen.api.target.EnhancementTargetProvider;

/**
 * 把「装备上的某一条词条」暴露为强化框架的目标类型。
 *
 * <p>按 ES-01 与整件星级强化完全分离：本 Provider 只读写词条层
 * （{@link AffixLayer}），<strong>不触碰整件装备的 {@code currentStar}</strong>。
 *
 * <p>字段映射：
 * <ul>
 *   <li>{@code level} → 当前选中词条的强化等级；</li>
 *   <li>{@code temper} → 词条层剩余容量，让配方公式能据此写条件；</li>
 *   <li>{@code recipeId} → 当前选中的词条 key。</li>
 * </ul>
 *
 * <p>「当前选中词条」由 {@link AffixSelectionService} 按玩家维护。框架接口不传玩家，因此本 Provider
 * 需要一个「当前操作者」上下文：由 GUI 在开始一次交互时通过 {@link #bindOperator} 绑定，结束时解绑。
 * 这是接口形态与词条强化需求之间的落差，不是设计偏好——框架的读写方法只接受 {@code ItemStack}。
 */
public final class AffixTargetProvider implements EnhancementTargetProvider {

    /** 与配方 {@code target.provider} 对应的 Provider ID。 */
    public static final String PROVIDER_ID = "affix";

    private final EmakiStrengthenPlugin plugin;
    private final AffixLayerCodec layerCodec;
    private final AffixSelectionService selectionService;
    private final ThreadLocal<UUID> operator = new ThreadLocal<>();

    public AffixTargetProvider(EmakiStrengthenPlugin plugin,
            AffixLayerCodec layerCodec,
            AffixSelectionService selectionService) {
        this.plugin = plugin;
        this.layerCodec = layerCodec;
        this.selectionService = selectionService;
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
        return !resolveCandidates(itemStack).isEmpty();
    }

    @Override
    public int readLevel(@Nullable ItemStack itemStack) {
        String affixKey = resolveSelection(itemStack);
        if (Texts.isBlank(affixKey)) {
            return 0;
        }
        return layerCodec.readOrEmpty(itemStack, defaultCapacityMax()).affix(affixKey).level();
    }

    @Override
    public int readTemper(@Nullable ItemStack itemStack) {
        return layerCodec.readOrEmpty(itemStack, defaultCapacityMax()).capacityRemaining();
    }

    @Override
    public @NotNull String readRecipeId(@Nullable ItemStack itemStack) {
        return resolveSelection(itemStack);
    }

    @Override
    public void writeLevel(@Nullable ItemStack itemStack, int level) {
        String affixKey = resolveSelection(itemStack);
        if (Texts.isBlank(affixKey) || itemStack == null) {
            return;
        }
        AffixLayer layer = layerCodec.readOrEmpty(itemStack, defaultCapacityMax());
        AffixState current = layer.affix(affixKey);
        int targetLevel = Math.max(0, level);
        if (targetLevel <= current.level()) {
            return;
        }
        int capacityCostPerLevel = capacityCostPerLevel();
        int levelDelta = targetLevel - current.level();
        int capacityDelta = capacityCostPerLevel * levelDelta;
        // 容量不足时不写入。执行服务已在扣费前用 canAfford 拦过一次，这里是第二道防线，
        // 保证任何绕过预检的调用路径也不会把容量写成负数。
        if (!layer.canAfford(capacityDelta)) {
            return;
        }
        AffixState next = new AffixState(
                affixKey,
                targetLevel,
                current.bonus() + bonusPerLevel() * levelDelta,
                current.capacityCost() + capacityDelta
        );
        layerCodec.write(itemStack, layer.with(next));
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
        layerCodec.clear(itemStack);
    }

    private List<String> resolveCandidates(ItemStack itemStack) {
        return selectionService.enhanceableAffixes(itemStack, maxAffixLevel());
    }

    private String resolveSelection(ItemStack itemStack) {
        List<String> candidates = resolveCandidates(itemStack);
        return selectionService.selected(operator.get(), candidates);
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
