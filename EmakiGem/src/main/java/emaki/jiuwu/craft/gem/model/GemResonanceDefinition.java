package emaki.jiuwu.craft.gem.model;

import java.util.List;

import emaki.jiuwu.craft.corelib.api.text.Texts;

public record GemResonanceDefinition(
        String id,
        String displayName,
        int priority,
        String exclusiveGroup,
        ResonanceChain chain,
        ResonanceEffects effects) {

    public GemResonanceDefinition {
        id = Texts.lower(id);
        displayName = Texts.isBlank(displayName) ? id : displayName;
        priority = Math.max(0, priority);
        exclusiveGroup = Texts.isBlank(exclusiveGroup) ? "" : Texts.lower(exclusiveGroup);
        chain = chain == null ? new ResonanceChain("unordered", List.of()) : chain;
        effects = effects == null ? new ResonanceEffects(null, null, null, null, null) : effects;
    }

    public GemResonanceDefinition(String id, String displayName, ResonanceChain chain, ResonanceEffects effects) {
        this(id, displayName, 0, "", chain, effects);
    }
}
