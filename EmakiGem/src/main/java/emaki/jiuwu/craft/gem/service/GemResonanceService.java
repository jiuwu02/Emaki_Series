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
        List<GemDefinition> gemList = new ArrayList<>(inlaidGems);
        List<GemResonanceDefinition> activeResonances = new ArrayList<>();
        for (GemResonanceDefinition resonance : resonanceLoader.all().values()) {
            ResonanceChain chain = resonance.chain();
            if (chain.pattern().isEmpty()) {
                continue;
            }
            if (chain.isOrdered()) {
                if (matchesOrdered(chain.pattern(), gemList)) {
                    activeResonances.add(resonance);
                }
            } else {
                if (matchesUnordered(chain.pattern(), gemList)) {
                    activeResonances.add(resonance);
                }
            }
        }
        return List.copyOf(activeResonances);
    }

    private boolean matchesUnordered(List<ResonancePatternEntry> pattern, List<GemDefinition> gems) {
        Set<Integer> used = new HashSet<>();
        for (ResonancePatternEntry entry : pattern) {
            boolean found = false;
            for (int i = 0; i < gems.size(); i++) {
                if (used.contains(i)) {
                    continue;
                }
                if (entry.matches(gems.get(i))) {
                    used.add(i);
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesOrdered(List<ResonancePatternEntry> pattern, List<GemDefinition> gems) {
        int patternSize = pattern.size();
        int gemsSize = gems.size();
        if (patternSize > gemsSize) {
            return false;
        }
        for (int start = 0; start <= gemsSize - patternSize; start++) {
            boolean matched = true;
            for (int j = 0; j < patternSize; j++) {
                if (!pattern.get(j).matches(gems.get(start + j))) {
                    matched = false;
                    break;
                }
            }
            if (matched) {
                return true;
            }
        }
        return false;
    }
}
