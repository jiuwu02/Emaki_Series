package emaki.jiuwu.craft.gem.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import emaki.jiuwu.craft.corelib.api.math.Numbers;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.random.WeightedPool;
import emaki.jiuwu.craft.corelib.math.Randoms;
import emaki.jiuwu.craft.gem.model.GemDefinition;
import emaki.jiuwu.craft.gem.model.GemItemInstance;
import emaki.jiuwu.craft.gem.model.GemAffix;

/** Generates reroll candidates without mutating the formal gem instance. */
public final class GemRerollCandidateGenerator {

    public record GenerationResult(boolean success, String errorKey, GemItemInstance candidate) {
        public static GenerationResult failure(String errorKey) {
            return new GenerationResult(false, errorKey, null);
        }

        public static GenerationResult success(GemItemInstance candidate) {
            return new GenerationResult(true, "", candidate);
        }
    }

    public GenerationResult fullReroll(GemItemInstance original, GemDefinition definition, String group) {
        if (original == null || definition == null || !definition.reroll().enabled()) {
            return GenerationResult.failure("gem.reroll.disabled");
        }
        int stage = Math.max(1, original.stage() <= 0 ? original.level() : original.stage());
        List<GemDefinition.AffixPoolEntry> entries = definition.reroll().poolFor(group, stage);
        if (entries.isEmpty()) {
            return GenerationResult.failure("gem.reroll.pool_empty");
        }
        int count = original.affixes().isEmpty()
                ? definition.reroll().maxAffixes()
                : Math.min(definition.reroll().maxAffixes(), original.affixes().size());
        List<GemAffix> rolled = new ArrayList<>();
        for (int index = 0; index < Math.max(1, count); index++) {
            GemDefinition.AffixPoolEntry entry = rollEntry(entries);
            if (entry == null) {
                return GenerationResult.failure("gem.reroll.pool_invalid");
            }
            rolled.add(rollAffix(entry, stage));
        }
        return GenerationResult.success(rebuild(original, rolled));
    }

    public GenerationResult valueReroll(GemItemInstance original, GemDefinition definition, String group) {
        if (original == null || definition == null || !definition.reroll().enabled()) {
            return GenerationResult.failure("gem.reroll.disabled");
        }
        if (original.affixes().isEmpty()) {
            return GenerationResult.failure("gem.reroll.no_affixes");
        }
        int stage = Math.max(1, original.stage() <= 0 ? original.level() : original.stage());
        List<GemDefinition.AffixPoolEntry> entries = definition.reroll().poolFor(group, stage);
        if (entries.isEmpty()) {
            return GenerationResult.failure("gem.reroll.pool_empty");
        }
        List<GemAffix> rolled = new ArrayList<>();
        for (GemAffix existing : GemAffix.decodeAll(original.affixes())) {
            GemDefinition.AffixPoolEntry entry = entries.stream()
                    .filter(candidate -> candidate.id().equalsIgnoreCase(existing.id()))
                    .findFirst()
                    .orElse(null);
            if (entry == null) {
                return GenerationResult.failure("gem.reroll.affix_not_configured");
            }
            double value = Randoms.uniform(entry.minValue(), entry.maxValue());
            rolled.add(new GemAffix(existing.id(), existing.stage(), value));
        }
        return GenerationResult.success(rebuild(original, rolled));
    }

    private GemDefinition.AffixPoolEntry rollEntry(List<GemDefinition.AffixPoolEntry> entries) {
        WeightedPool<GemDefinition.AffixPoolEntry> pool = new WeightedPool<>();
        entries.forEach(entry -> pool.add(entry, entry.weight()));
        return pool.roll().orElse(null);
    }

    private GemAffix rollAffix(GemDefinition.AffixPoolEntry entry, int fallbackStage) {
        int minStage = Math.max(1, entry.minStage());
        int maxStage = Math.max(minStage, Math.min(entry.maxStage(), Math.max(minStage, fallbackStage)));
        int stage = Randoms.randomInt(minStage, maxStage);
        double value = Randoms.uniform(entry.minValue(), entry.maxValue());
        return new GemAffix(entry.id(), stage, value);
    }

    private GemItemInstance rebuild(GemItemInstance original, List<GemAffix> affixes) {
        long version = Math.max(System.currentTimeMillis(), original.updatedAt() + 1L);
        return new GemItemInstance(
                original.gemId(),
                original.level(),
                version,
                original.instanceId(),
                original.stage(),
                affixes.stream().map(GemAffix::encode).toList(),
                original.matrices(),
                original.extensions(),
                original.dataVersion()
        );
    }
}
