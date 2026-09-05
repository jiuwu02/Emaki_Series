package emaki.jiuwu.craft.strengthen.enhancement.cost;

import java.util.List;
import java.util.Locale;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.matcher.ItemRequirement;
import emaki.jiuwu.craft.corelib.quantity.Quantity;

public record MaterialSlotConfig(
        @NotNull ItemRequirement requirement,
        @NotNull Quantity quantity,
        @NotNull ConsumeTimingEnum consumeTiming,
        boolean required,
        @NotNull TargetCompareEnum targetCompare,
        @NotNull String materialId,
        @NotNull String countKey
) {

    public MaterialSlotConfig {
        if (requirement == null) {
            throw new IllegalArgumentException("Material requirement cannot be null");
        }
        if (quantity == null) {
            throw new IllegalArgumentException("Material quantity cannot be null");
        }
        if (consumeTiming == null) {
            throw new IllegalArgumentException("Material consume timing cannot be null");
        }
        targetCompare = targetCompare == null ? TargetCompareEnum.NONE : targetCompare;
        materialId = materialId == null || materialId.isBlank()
                ? requirement.identity() : materialId.trim().toLowerCase(Locale.ROOT);
        countKey = countKey == null || countKey.isBlank()
                ? materialId : countKey.trim().toLowerCase(Locale.ROOT);
    }

    public MaterialSlotConfig(@NotNull ItemRequirement requirement,
            @NotNull Quantity quantity,
            @NotNull ConsumeTimingEnum consumeTiming) {
        this(requirement, quantity, consumeTiming, true, TargetCompareEnum.NONE,
                requirement.identity(), requirement.identity());
    }

    public MaterialSlotConfig(@NotNull ItemRequirement requirement,
            @NotNull Quantity quantity,
            @NotNull ConsumeTimingEnum consumeTiming,
            boolean required,
            @NotNull TargetCompareEnum targetCompare) {
        this(requirement, quantity, consumeTiming, required, targetCompare,
                requirement.identity(), requirement.identity());
    }

    public static @Nullable MaterialSlotConfig fromConfig(@Nullable Object config) {
        if (!(config instanceof YamlSection section)) {
            return null;
        }
        ItemRequirement parsed = ItemRequirement.fromConfig(section);
        List<ItemSourceRef> sources = parsed.sources();
        if (sources.isEmpty() && section.contains("item")) {
            sources = ItemRequirement.parseSources(section.get("item"));
        }
        String materialId = section.getString("material_id", "");
        if (materialId == null || materialId.isBlank()) {
            materialId = section.getString("count_key", "");
        }
        if (materialId == null || materialId.isBlank()) {
            materialId = ItemRequirement.sourceIdentity(sources);
        }
        if (materialId == null || materialId.isBlank()) {
            materialId = parsed.identity();
        }
        ItemRequirement requirement = new ItemRequirement(sources, parsed.matcher(), materialId);
        Quantity quantity = Quantity.fromConfig(quantityNode(section, "quantity"));
        ConsumeTimingEnum timing = ConsumeTimingEnum.fromStringOrDefault(
                section.getString("consume", "always"),
                ConsumeTimingEnum.ALWAYS
        );
        boolean required = resolveRequired(section);
        TargetCompareEnum targetCompare = TargetCompareEnum.fromStringOrDefault(
                section.getString("target_compare", ""), TargetCompareEnum.NONE);
        String countKey = section.getString("count_key", materialId);
        if (requirement.empty() || materialId == null || materialId.isBlank()) {
            return null;
        }
        return new MaterialSlotConfig(requirement, quantity, timing, required, targetCompare, materialId, countKey);
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
