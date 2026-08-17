package emaki.jiuwu.craft.strengthen.enhancement.cost;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.quantity.Quantity;

public record CurrencyConfig(
        @NotNull String provider,
        @NotNull String currencyId,
        @NotNull Quantity amount
) {

    public CurrencyConfig {
        provider = Texts.toStringSafe(provider);
        currencyId = Texts.toStringSafe(currencyId);
    }

    public static @Nullable CurrencyConfig fromConfig(@Nullable Object config) {
        if (!(config instanceof YamlSection section)) {
            return null;
        }
        String provider = section.getString("provider", "");
        String currencyId = section.getString("currency_id", section.getString("currency", ""));
        if (Texts.isBlank(currencyId)) {
            return null;
        }
        Quantity amount = Quantity.fromConfig(section.get("amount"));
        return new CurrencyConfig(provider, currencyId, amount);
    }
}
