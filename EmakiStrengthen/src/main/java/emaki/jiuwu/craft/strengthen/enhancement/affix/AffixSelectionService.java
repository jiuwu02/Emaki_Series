package emaki.jiuwu.craft.strengthen.enhancement.affix;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;

/**
 * 枚举一件装备上「可强化词条」，并记录每位玩家当前选中的是哪一条。
 *
 * <p>词条来源是结构化属性：装备上任何一条结构化属性 key 都是一个候选词条。已达等级上限的词条
 * 不进入列表（ES-05 要求「不可强化词条不进入列表」）。
 *
 * <p>选择状态按玩家隔离并保存在内存中：它是一次交互会话内的临时选择，不需要持久化。
 *
 * <p><strong>线程：</strong>选择表基于 {@link ConcurrentHashMap}；枚举方法读取物品 PDC，需在持有该
 * 物品的所有者线程调用。
 */
public final class AffixSelectionService {

    private final EmakiStrengthenPlugin plugin;
    private final AffixLayerCodec layerCodec;
    private final Map<UUID, String> selections = new ConcurrentHashMap<>();

    public AffixSelectionService(EmakiStrengthenPlugin plugin, AffixLayerCodec layerCodec) {
        this.plugin = plugin;
        this.layerCodec = layerCodec;
    }

    /**
     * 列出物品上所有可强化词条的属性 key。
     *
     * <p>顺序稳定（按 key 排序），否则「左键下一条」在两次打开之间会跳序。
     *
     * @param itemStack  目标装备
     * @param maxLevel   单条词条的等级上限；{@code <= 0} 表示不限
     * @return 可强化词条 key 列表；读不到结构化属性时为空
     */
    public @NotNull List<String> enhanceableAffixes(@Nullable ItemStack itemStack, int maxLevel) {
        List<String> affixes = affixes(itemStack);
        if (affixes.isEmpty()) {
            return List.of();
        }
        AffixLayer layer = layerCodec.readOrEmpty(itemStack, 0);
        return affixes.stream()
                .filter(key -> maxLevel <= 0 || layer.affix(key).level() < maxLevel)
                .toList();
    }

    /**
     * 列出物品上的全部基础词条，包括已达到强化上限的词条。
     *
     * <p>词条强化自己的属性来源必须排除，否则基础来源被移除后，强化增量会反过来把自己保留成候选词条。
     */
    public @NotNull List<String> affixes(@Nullable ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir() || plugin == null
                || plugin.pdcAttributeGateway() == null) {
            return List.of();
        }
        Map<String, Map<String, Double>> bySource = plugin.pdcAttributeGateway().readAllAttributes(itemStack);
        if (bySource.isEmpty()) {
            return List.of();
        }
        return bySource.entrySet().stream()
                .filter(entry -> !AffixTargetProvider.ATTRIBUTE_SOURCE_ID.equals(Texts.lower(entry.getKey())))
                .flatMap(entry -> entry.getValue().keySet().stream())
                .map(Texts::lower)
                .filter(Texts::isNotBlank)
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * {@return 该玩家当前选中的词条 key；未选择或选择已失效时回退为列表首条，列表为空则返回空串}
     */
    public @NotNull String selected(@Nullable UUID playerId, @NotNull List<String> candidates) {
        if (candidates.isEmpty()) {
            return "";
        }
        String current = playerId == null ? null : selections.get(playerId);
        // 目标或材料变化后候选集会变，旧选择可能已不在列表中——此时必须重新校验回落，
        // 否则玩家会对一条不存在的词条确认强化（ES-05「目标或材料变化后重新校验」）。
        if (current != null && candidates.contains(current)) {
            return current;
        }
        String fallback = candidates.getFirst();
        if (playerId != null) {
            selections.put(playerId, fallback);
        }
        return fallback;
    }

    /** 选择下一条词条（左键）。{@return 选中的 key} */
    public @NotNull String selectNext(@Nullable UUID playerId, @NotNull List<String> candidates) {
        return cycle(playerId, candidates, 1);
    }

    /** 选择上一条词条（右键）。{@return 选中的 key} */
    public @NotNull String selectPrevious(@Nullable UUID playerId, @NotNull List<String> candidates) {
        return cycle(playerId, candidates, -1);
    }

    /** 清除该玩家的选择状态。 */
    public void clear(@Nullable UUID playerId) {
        if (playerId != null) {
            selections.remove(playerId);
        }
    }

    /** 清除全部选择状态，用于重载。 */
    public void clearAll() {
        selections.clear();
    }

    private String cycle(UUID playerId, List<String> candidates, int delta) {
        if (candidates.isEmpty()) {
            return "";
        }
        String current = selected(playerId, candidates);
        int index = candidates.indexOf(current);
        if (index < 0) {
            index = 0;
        }
        int size = candidates.size();
        int next = ((index + delta) % size + size) % size;
        String selection = candidates.get(next);
        if (playerId != null) {
            selections.put(playerId, selection);
        }
        return selection;
    }
}
