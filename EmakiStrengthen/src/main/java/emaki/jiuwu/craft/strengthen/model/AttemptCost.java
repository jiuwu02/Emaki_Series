package emaki.jiuwu.craft.strengthen.model;

import emaki.jiuwu.craft.corelib.text.Texts;

public record AttemptCost(String provider,
        String currencyId,
        String displayName,
        long amount) {

    public AttemptCost {
        provider = Texts.lower(provider);
        currencyId = Texts.toStringSafe(currencyId);
        displayName = Texts.toStringSafe(displayName);
        amount = Math.max(0L, amount);
    }

    public boolean itemCost() {
        return "items".equals(provider);
    }
}
