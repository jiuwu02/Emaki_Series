package emaki.jiuwu.craft.gem.model;

import emaki.jiuwu.craft.corelib.text.Texts;

public record GemResonanceDefinition(
        String id,
        String displayName,
        ResonanceChain chain,
        ResonanceEffects effects) {

    public GemResonanceDefinition {
        id = Texts.lower(id);
        displayName = Texts.isBlank(displayName) ? id : displayName;
        chain = chain == null ? new ResonanceChain("unordered", java.util.List.of()) : chain;
        effects = effects == null ? new ResonanceEffects(null, null, null, null, null) : effects;
    }
}
