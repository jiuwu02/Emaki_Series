package emaki.jiuwu.craft.strengthen.enhancement.cost;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.matcher.Matcher;
import emaki.jiuwu.craft.corelib.quantity.Quantity;

public record MaterialSlotConfig(
        @NotNull Matcher matcher,
        @NotNull Quantity quantity,
        @NotNull ConsumeTimingEnum consumeTiming,
        boolean required,
        @NotNull TargetCompareEnum targetCompare
) {

    public MaterialSlotConfig {
        if (matcher == null) {
            throw new IllegalArgumentException("Material matcher cannot be null");
        }
        if (quantity == null) {
            throw new IllegalArgumentException("Material quantity cannot be null");
        }
        if (consumeTiming == null) {
            throw new IllegalArgumentException("Material consume timing cannot be null");
        }
        targetCompare = targetCompare == null ? TargetCompareEnum.NONE : targetCompare;
    }

    public MaterialSlotConfig(@NotNull Matcher matcher,
            @NotNull Quantity quantity,
            @NotNull ConsumeTimingEnum consumeTiming) {
        this(matcher, quantity, consumeTiming, true, TargetCompareEnum.NONE);
    }

    public static @Nullable MaterialSlotConfig fromConfig(@Nullable Object config) {
        if (!(config instanceof YamlSection section)) {
            return null;
        }
        Matcher matcher = Matcher.fromConfig(section.getSection("matcher"));
        Quantity quantity = Quantity.fromConfig(quantityNode(section, "quantity"));
        ConsumeTimingEnum timing = ConsumeTimingEnum.fromStringOrDefault(
                section.getString("consume", "always"),
                ConsumeTimingEnum.ALWAYS
        );
        boolean required = resolveRequired(section);
        TargetCompareEnum targetCompare = TargetCompareEnum.fromStringOrDefault(
                section.getString("target_compare", ""), TargetCompareEnum.NONE);
        return new MaterialSlotConfig(matcher, quantity, timing, required, targetCompare);
    }

    private static boolean resolveRequired(@NotNull YamlSection section) {
        if (section.contains("optional")) {
            return !Boolean.TRUE.equals(section.getBoolean("optional", Boolean.FALSE));
        }
        return !Boolean.FALSE.equals(section.getBoolean("required", Boolean.TRUE));
    }

    public boolean comparesTarget() {
        return targetCompare != TargetCompareEnum.NONE;
    }

    public static @Nullable Object quantityNode(@NotNull YamlSection section, @NotNull String path) {
        YamlSection nested = section.getSection(path);
        return nested != null ? nested : section.get(path);
    }
}
