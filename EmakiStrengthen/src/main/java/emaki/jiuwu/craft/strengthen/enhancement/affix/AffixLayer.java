package emaki.jiuwu.craft.strengthen.enhancement.affix;

import java.util.LinkedHashMap;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.text.Texts;

/**
 * 一件装备的词条强化层。
 *
 * <p>按 ES-02 保存 {@code affix_capacity_max} / {@code affix_capacity_used} 与每条词条的容量消耗。
 * 容量是词条强化独有的资源，<strong>不与 Forge 的锻造材料容量共用</strong>——两者的键与存储位置
 * 完全分离，Forge 侧写的是 {@code emakiforge} 命名空间下的锻造变量。
 *
 * @param capacityMax 最大容量
 * @param affixes     词条 key → 词条状态
 */
public record AffixLayer(int capacityMax, @NotNull Map<String, AffixState> affixes) {

    public AffixLayer {
        capacityMax = Math.max(0, capacityMax);
        affixes = affixes == null ? Map.of() : Map.copyOf(affixes);
    }

    /** {@return 一个容量为 {@code capacityMax} 且没有任何已强化词条的空层} */
    public static @NotNull AffixLayer empty(int capacityMax) {
        return new AffixLayer(capacityMax, Map.of());
    }

    /**
     * {@return 已用容量，等于各词条容量消耗之和}
     *
     * <p>刻意由词条状态求和而非独立保存：独立字段会与词条明细产生两份真相，词条降级或移除时
     * 极易漏改而导致容量永久泄漏。ES-02 要求的「返还容量」在这里是自动成立的。
     */
    public int capacityUsed() {
        int used = 0;
        for (AffixState state : affixes.values()) {
            used += state.capacityCost();
        }
        return used;
    }

    /** {@return 剩余可用容量} */
    public int capacityRemaining() {
        return Math.max(0, capacityMax - capacityUsed());
    }

    /**
     * {@return 是否还能承担 {@code cost} 的容量占用}
     *
     * @param cost 本次强化需要占用的容量
     */
    public boolean canAfford(int cost) {
        return cost <= 0 || capacityRemaining() >= cost;
    }

    /** {@return 指定词条的状态；从未强化过时返回一条 fresh 状态而非 {@code null}} */
    public @NotNull AffixState affix(@Nullable String attributeKey) {
        String key = Texts.lower(attributeKey);
        AffixState state = affixes.get(key);
        return state == null ? AffixState.fresh(key) : state;
    }

    /** {@return 用给定词条状态替换后的新层} */
    public @NotNull AffixLayer with(@NotNull AffixState state) {
        Map<String, AffixState> next = new LinkedHashMap<>(affixes);
        next.put(state.attributeKey(), state);
        return new AffixLayer(capacityMax, next);
    }

    /** {@return 移除指定词条后的新层；容量随之自动返还} */
    public @NotNull AffixLayer without(@Nullable String attributeKey) {
        String key = Texts.lower(attributeKey);
        if (!affixes.containsKey(key)) {
            return this;
        }
        Map<String, AffixState> next = new LinkedHashMap<>(affixes);
        next.remove(key);
        return new AffixLayer(capacityMax, next);
    }

    /** {@return 以新的最大容量替换后的新层} */
    public @NotNull AffixLayer withCapacityMax(int newCapacityMax) {
        return new AffixLayer(newCapacityMax, affixes);
    }
}
