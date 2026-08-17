package emaki.jiuwu.craft.strengthen.enhancement.affix;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.api.text.Texts;

/**
 * 单条词条的强化状态。
 *
 * @param attributeKey 词条对应的结构化属性 key
 * @param level        该词条已强化的等级；0 表示未强化
 * @param bonus        该词条因强化累计获得的增量
 * @param capacityCost 该词条当前占用的容量
 */
public record AffixState(@NotNull String attributeKey,
        int level,
        double bonus,
        int capacityCost) {

    public AffixState {
        attributeKey = Texts.lower(attributeKey);
        level = Math.max(0, level);
        capacityCost = Math.max(0, capacityCost);
        if (!Double.isFinite(bonus)) {
            bonus = 0D;
        }
    }

    /** {@return 一条尚未强化的词条状态} */
    public static @NotNull AffixState fresh(@NotNull String attributeKey) {
        return new AffixState(attributeKey, 0, 0D, 0);
    }

    /** {@return 在当前状态上推进一级后的新状态} */
    public @NotNull AffixState advanced(double bonusDelta, int capacityDelta) {
        return new AffixState(attributeKey, level + 1, bonus + bonusDelta, capacityCost + Math.max(0, capacityDelta));
    }

    /** {@return 是否已被强化过} */
    public boolean enhanced() {
        return level > 0;
    }
}
