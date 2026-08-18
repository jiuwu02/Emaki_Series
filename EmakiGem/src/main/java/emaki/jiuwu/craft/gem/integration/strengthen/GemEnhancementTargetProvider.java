package emaki.jiuwu.craft.gem.integration.strengthen;

import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.gem.EmakiGemPlugin;
import emaki.jiuwu.craft.gem.model.GemDefinition;
import emaki.jiuwu.craft.gem.model.GemItemInstance;
import emaki.jiuwu.craft.strengthen.api.target.EnhancementTargetProvider;

/**
 * 把「一枚宝石物品」暴露为强化框架的目标类型。
 *
 * <p>职责边界按 ES-11 划分：Strengthen 负责材料、费用、概率、保底、事务与 Action；本 Provider
 * 只负责识别宝石、读取阶段/等级、以及把结果写回宝石实例。Strengthen 侧因此不需要引用
 * {@link GemItemInstance} 或任何 Gem 内部 Service。
 *
 * <p>字段映射：
 * <ul>
 *   <li>{@code level} → 宝石等级 {@link GemItemInstance#level()}；</li>
 *   <li>{@code temper} → 宝石阶段 {@link GemItemInstance#stage()}，宝石没有「淬炼值」概念，
 *       阶段是与之语义最接近的递进维度；</li>
 *   <li>{@code recipeId} → 宝石定义 ID，即 {@link GemItemInstance#gemId()}。</li>
 * </ul>
 *
 * <p>写回时其余实例字段（{@code instanceId} / {@code affixes} / {@code matrices} /
 * {@code extensions} / {@code dataVersion}）原样保留，避免强化流程顺带清空 T-4-03 引入的实例数据。
 */
public final class GemEnhancementTargetProvider implements EnhancementTargetProvider {

    /** 与配方 {@code target.provider} 对应的 Provider ID。 */
    public static final String PROVIDER_ID = "gem";

    private final EmakiGemPlugin plugin;

    public GemEnhancementTargetProvider(EmakiGemPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String id() {
        return PROVIDER_ID;
    }

    @Override
    public boolean canHandle(@Nullable ItemStack itemStack) {
        GemItemInstance instance = readInstance(itemStack);
        GemDefinition definition = readDefinition(instance);
        return definition != null && definition.stages().enabled();
    }

    @Override
    public int readLevel(@Nullable ItemStack itemStack) {
        GemItemInstance instance = readInstance(itemStack);
        return instance == null ? 0 : instance.level();
    }

    @Override
    public int readTemper(@Nullable ItemStack itemStack) {
        GemItemInstance instance = readInstance(itemStack);
        return instance == null ? 0 : instance.stage();
    }

    @Override
    public @NotNull String readRecipeId(@Nullable ItemStack itemStack) {
        GemItemInstance instance = readInstance(itemStack);
        return instance == null ? "" : instance.gemId();
    }

    @Override
    public void writeLevel(@Nullable ItemStack itemStack, int level) {
        GemItemInstance current = readInstance(itemStack);
        GemDefinition definition = readDefinition(current);
        int targetLevel = Math.max(1, level);
        if (definition == null || targetLevel > definition.stages().maxLevel()
                || (targetLevel > 1 && definition.stage(targetLevel) == null)) {
            return;
        }
        writeInstance(itemStack, withLevel(current, definition, targetLevel));
    }

    @Override
    public void writeTemper(@Nullable ItemStack itemStack, int temper) {
        GemItemInstance current = readInstance(itemStack);
        if (current == null) {
            return;
        }
        writeInstance(itemStack, withStage(current, temper));
    }

    @Override
    public void writeRecipeId(@Nullable ItemStack itemStack, @Nullable String recipeId) {
        // 宝石的「配方 ID」就是宝石定义 ID，由物品自身决定，不接受强化流程改写。
        // 静默忽略而非抛异常：框架会对所有目标统一调用写回，抛异常会中断正常升级。
    }

    @Override
    public void clearEnhancement(@Nullable ItemStack itemStack) {
        GemItemInstance current = readInstance(itemStack);
        GemDefinition definition = readDefinition(current);
        if (definition == null) {
            return;
        }
        writeInstance(itemStack, withLevel(current, definition, 1));
    }

    private GemItemInstance readInstance(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir() || plugin == null
                || plugin.itemMatcher() == null) {
            return null;
        }
        GemItemInstance instance = plugin.itemMatcher().readGemInstance(itemStack);
        if (instance == null || Texts.isBlank(instance.gemId())) {
            return null;
        }
        // 只承认确实存在定义的宝石，避免把任意带残留 PDC 的物品当成宝石目标。
        return readDefinition(instance) == null ? null : instance;
    }

    private GemDefinition readDefinition(GemItemInstance instance) {
        return instance == null || plugin == null || plugin.gemLoader() == null
                ? null
                : plugin.gemLoader().get(instance.gemId());
    }

    private void writeInstance(ItemStack itemStack, GemItemInstance instance) {
        if (plugin == null || plugin.itemFactory() == null || itemStack == null || instance == null) {
            return;
        }
        plugin.itemFactory().applyInstance(itemStack, instance);
    }

    private static GemItemInstance withLevel(GemItemInstance source, GemDefinition definition, int level) {
        Map<String, String> matrices = new LinkedHashMap<>(source.matrices());
        definition.matricesForLevel(level).forEach((key, value) -> {
            if (Texts.isBlank(value)) {
                matrices.remove(key);
            } else {
                matrices.put(key, value);
            }
        });
        return new GemItemInstance(
                source.gemId(),
                level,
                System.currentTimeMillis(),
                source.instanceId(),
                level <= 1 ? 0 : level,
                source.affixes(),
                matrices,
                source.extensions(),
                source.dataVersion()
        );
    }

    private static GemItemInstance withStage(GemItemInstance source, int stage) {
        return new GemItemInstance(
                source.gemId(),
                source.level(),
                System.currentTimeMillis(),
                source.instanceId(),
                stage,
                source.affixes(),
                source.matrices(),
                source.extensions(),
                source.dataVersion()
        );
    }
}
