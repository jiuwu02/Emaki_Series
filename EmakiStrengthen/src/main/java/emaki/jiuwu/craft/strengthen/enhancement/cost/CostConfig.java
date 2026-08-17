package emaki.jiuwu.craft.strengthen.enhancement.cost;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;

public record CostConfig(
        @NotNull List<CurrencyConfig> currencies,
        @NotNull List<MaterialSlotConfig> materials
) {

    public CostConfig {
        currencies = List.copyOf(currencies);
        materials = List.copyOf(materials);
    }

    public static @NotNull CostConfig fromConfig(@Nullable Object config) {
        if (!(config instanceof YamlSection section)) {
            return new CostConfig(List.of(), List.of());
        }
        List<CurrencyConfig> currencies = new ArrayList<>();
        List<?> costsList = section.getList("costs");
        if (costsList != null) {
            for (Object item : costsList) {
                CurrencyConfig currency = CurrencyConfig.fromConfig(item);
                if (currency != null) {
                    currencies.add(currency);
                }
            }
        }
        List<MaterialSlotConfig> materials = new ArrayList<>();
        List<?> materialsList = section.getList("materials");
        if (materialsList != null) {
            for (Object item : materialsList) {
                MaterialSlotConfig material = MaterialSlotConfig.fromConfig(item);
                if (material != null) {
                    materials.add(material);
                }
            }
        }
        return new CostConfig(List.copyOf(currencies), List.copyOf(materials));
    }
}
