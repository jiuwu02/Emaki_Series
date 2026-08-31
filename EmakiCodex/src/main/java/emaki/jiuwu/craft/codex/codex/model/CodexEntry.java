package emaki.jiuwu.craft.codex.codex.model;

import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.codex.advancement.model.AdvancementTrigger;

public record CodexEntry(String entryId,
        String title,
        String description,
        String icon,
        boolean hidden,
        List<AdvancementTrigger> triggers,
        List<String> advancements,
        Map<String, Double> attributeRewards,
        List<String> claimActions) {

    public CodexEntry {
        triggers = triggers == null ? List.of() : List.copyOf(triggers);
        advancements = advancements == null ? List.of() : List.copyOf(advancements);
        attributeRewards = attributeRewards == null ? Map.of() : Map.copyOf(attributeRewards);
        claimActions = claimActions == null ? List.of() : List.copyOf(claimActions);
    }

    public boolean hasAttributeRewards() {
        return !attributeRewards.isEmpty();
    }

    public boolean hasClaimActions() {
        return !claimActions.isEmpty();
    }
}
