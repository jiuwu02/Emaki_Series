package emaki.jiuwu.craft.strengthen.enhancement.cost;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.matcher.Matcher;
import emaki.jiuwu.craft.corelib.quantity.Quantity;

public record MaterialSlotConfig(
        @NotNull Matcher matcher,
        @NotNull Quantity quantity,
        @NotNull ConsumeTimingEnum consumeTiming
) {

    public static @Nullable MaterialSlotConfig fromConfig(@Nullable Object config) {
        if (!(config instanceof YamlSection section)) {
            return null;
        }
        Matcher matcher = Matcher.fromConfig(section.get("matcher"));
        Quantity quantity = Quantity.fromConfig(section.get("quantity"));
        ConsumeTimingEnum timing = ConsumeTimingEnum.fromStringOrDefault(
                section.getString("consume", "always"),
                ConsumeTimingEnum.ALWAYS
        );
        return new MaterialSlotConfig(matcher, quantity, timing);
    }
}
