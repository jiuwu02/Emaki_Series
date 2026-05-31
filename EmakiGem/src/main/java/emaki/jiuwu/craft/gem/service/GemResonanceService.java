package emaki.jiuwu.craft.gem.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import emaki.jiuwu.craft.gem.loader.GemResonanceLoader;
import emaki.jiuwu.craft.gem.model.GemDefinition;
import emaki.jiuwu.craft.gem.model.GemResonanceDefinition;
import emaki.jiuwu.craft.gem.model.ResonanceChain;
import emaki.jiuwu.craft.gem.model.ResonancePatternEntry;

public final class GemResonanceService {

    public record GemEntry(GemDefinition gem, int level) {

        public GemEntry {
            level = Math.max(1, level);
        }
    }

    private GemResonanceLoader resonanceLoader;

    public GemResonanceService(GemResonanceLoader resonanceLoader) {
        this.resonanceLoader = resonanceLoader;
    }

    public void refresh(GemResonanceLoader resonanceLoader) {
        this.resonanceLoader = resonanceLoader;
    }

    public List<GemResonanceDefinition> evaluate(Collection<GemDefinition> inlaidGems) {
        if (inlaidGems == null || inlaidGems.isEmpty()) {
            return List.of();
        }
        List<GemEntry> entries = new ArrayList<>(inlaidGems.size());
        for (GemDefinition gem : inlaidGems) {
            entries.add(new GemEntry(gem, Integer.MAX_VALUE));
        }
        return evaluateWithLevels(entries);
    }

    public List<GemResonanceDefinition> evaluateWithLevels(Collection<GemEntry> inlaidGems) {
        if (inlaidGems == null || inlaidGems.isEmpty()) {
            return List.of();
        }
        List<GemEntry> gemList = new ArrayList<>(inlaidGems);
        List<GemResonanceDefinition> sortedResonances = new ArrayList<>(resonanceLoader.all().values());
        sortedResonances.sort((a, b) -> Integer.compare(b.priority(), a.priority()));

        List<GemResonanceDefinition> activeResonances = new ArrayList<>();
        Set<Integer> usedGemIndices = new HashSet<>();
        Set<String> activatedGroups = new HashSet<>();

        for (GemResonanceDefinition resonance : sortedResonances) {
            ResonanceChain chain = resonance.chain();
            if (chain.pattern().isEmpty()) {
                continue;
            }
            String group = resonance.exclusiveGroup();
            if (!group.isEmpty() && activatedGroups.contains(group)) {
                continue;
            }
            Set<Integer> matchedIndices;
            if (chain.isOrdered()) {
                matchedIndices = matchesOrderedWithIndices(chain.pattern(), gemList, usedGemIndices);
            } else {
                matchedIndices = matchesUnorderedWithIndices(chain.pattern(), gemList, usedGemIndices);
            }
            if (matchedIndices != null) {
                activeResonances.add(resonance);
                usedGemIndices.addAll(matchedIndices);
                if (!group.isEmpty()) {
                    activatedGroups.add(group);
                }
            }
        }
        return List.copyOf(activeResonances);
    }

    private Set<Integer> matchesUnorderedWithIndices(List<ResonancePatternEntry> pattern,
            List<GemEntry> gems, Set<Integer> excluded) {
        Set<Integer> used = new HashSet<>();
        for (ResonancePatternEntry entry : pattern) {
            boolean found = false;
            for (int i = 0; i < gems.size(); i++) {
                if (excluded.contains(i) || used.contains(i)) {
                    continue;
                }
                GemEntry gemEntry = gems.get(i);
                if (entry.matches(gemEntry.gem(), gemEntry.level())) {
                    used.add(i);
                    found = true;
                    break;
                }
            }
            if (!found) {
                return null;
            }
        }
        return used;
    }

    private Set<Integer> matchesOrderedWithIndices(List<ResonancePatternEntry> pattern,
            List<GemEntry> gems, Set<Integer> excluded) {
        int patternSize = pattern.size();
        int gemsSize = gems.size();
        if (patternSize > gemsSize) {
            return null;
        }
        for (int start = 0; start <= gemsSize - patternSize; start++) {
            boolean matched = true;
            Set<Integer> candidate = new HashSet<>();
            for (int j = 0; j < patternSize; j++) {
                int idx = start + j;
                if (excluded.contains(idx)) {
                    matched = false;
                    break;
                }
                GemEntry gemEntry = gems.get(idx);
                if (!pattern.get(j).matches(gemEntry.gem(), gemEntry.level())) {
                    matched = false;
                    break;
                }
                candidate.add(idx);
            }
            if (matched) {
                return candidate;
            }
        }
        return null;
    }
}
