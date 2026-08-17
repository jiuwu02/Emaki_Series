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
        Matcher matcher = Matcher.fromConfig(section.getSection("matcher"));
        Quantity quantity = Quantity.fromConfig(quantityNode(section, "quantity"));
        ConsumeTimingEnum timing = ConsumeTimingEnum.fromStringOrDefault(
                section.getString("consume", "always"),
                ConsumeTimingEnum.ALWAYS
        );
        return new MaterialSlotConfig(matcher, quantity, timing);
    }

    /**
     * Resolves a Quantity node that may be written either as a nested typed section or as a bare
     * scalar. {@code YamlSection.get} unwraps a nested map into a plain {@code Map}, which
     * {@code Quantity.fromConfig} rejects, so a section must be fetched through
     * {@code getSection} instead.
     */
    public static @Nullable Object quantityNode(@NotNull YamlSection section, @NotNull String path) {
        YamlSection nested = section.getSection(path);
        return nested != null ? nested : section.get(path);
    }
}
