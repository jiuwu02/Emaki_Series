package emaki.jiuwu.craft.strengthen.enhancement.affix;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.api.text.Texts;

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

    public static @NotNull AffixState fresh(@NotNull String attributeKey) {
        return new AffixState(attributeKey, 0, 0D, 0);
    }

    public @NotNull AffixState advanced(double bonusDelta, int capacityDelta) {
        return new AffixState(attributeKey, level + 1, bonus + bonusDelta, capacityCost + Math.max(0, capacityDelta));
    }

    public boolean enhanced() {
        return level > 0;
    }
}
