package emaki.jiuwu.craft.station.recipe;

import java.util.List;
import java.util.Locale;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.matcher.ItemRequirement;
import emaki.jiuwu.craft.corelib.matcher.MatchContext;
import emaki.jiuwu.craft.corelib.matcher.Matcher;
import emaki.jiuwu.craft.station.api.model.MaterialRequirementView;

public record MaterialRequirement(String materialId,
        String requirementId,
        String countKey,
        List<ItemSourceRef> sources,
        long amount,
        boolean consume,
        Matcher matcher) {

    public MaterialRequirement {
        materialId = normalize(materialId);
        requirementId = normalize(requirementId);
        countKey = normalize(countKey);
        if (materialId.isEmpty()) {
            materialId = requirementId;
        }
        if (requirementId.isEmpty()) {
            requirementId = materialId;
        }
        if (countKey.isEmpty()) {
            countKey = materialId;
        }
        sources = sources == null ? List.of() : List.copyOf(sources);
        if (sources.isEmpty() && matcher == null) {
            throw new IllegalArgumentException("sources must not be empty");
        }
        if (amount <= 0L) {
            throw new IllegalArgumentException("amount must be positive: " + amount);
        }
    }

    public MaterialRequirement(List<ItemSourceRef> sources, long amount, boolean consume) {
        this(legacyIdentity(sources), legacyIdentity(sources), legacyIdentity(sources), sources, amount, consume, null);
    }

    public MaterialRequirement(List<ItemSourceRef> sources, long amount, boolean consume, Matcher matcher) {
        this(legacyIdentity(sources), legacyIdentity(sources), legacyIdentity(sources), sources, amount, consume, matcher);
    }

    public boolean hasMatcher() {
        return matcher != null;
    }

    public ItemRequirement asItemRequirement() {
        return new ItemRequirement(sources, matcher, countKey);
    }

    public boolean matches(MatchContext context) {
        return asItemRequirement().test(context);
    }

    public ItemSourceRef primarySource() {
        return sources.isEmpty() ? null : sources.getFirst();
    }

    public long totalFor(long batch) {
        long safeBatch = Math.max(1L, batch);
        try {
            return Math.multiplyExact(amount, safeBatch);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    public MaterialRequirementView toView() {
        return new MaterialRequirementView(materialId, requirementId, countKey, sources, amount, consume);
    }

    private static String legacyIdentity(List<ItemSourceRef> sources) {
        if (sources == null || sources.isEmpty()) {
            return "legacy";
        }
        String shorthand = ItemRequirement.sourceIdentity(sources);
        return shorthand.isBlank() ? "legacy" : shorthand;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
