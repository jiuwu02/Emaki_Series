package emaki.jiuwu.craft.item.model;

import java.util.List;

public record RepairConfig(boolean enabled,
        List<RepairMaterial> materials,
        RepairEconomyConfig economy,
        DisabledDisplay disabledDisplay,
        List<String> onDisabledActions,
        List<String> onRepairedActions) {

    public RepairConfig {
        materials = materials == null ? List.of() : List.copyOf(materials);
        economy = economy == null ? RepairEconomyConfig.disabled() : economy;
        disabledDisplay = disabledDisplay == null ? DisabledDisplay.empty() : disabledDisplay;
        onDisabledActions = onDisabledActions == null ? List.of() : List.copyOf(onDisabledActions);
        onRepairedActions = onRepairedActions == null ? List.of() : List.copyOf(onRepairedActions);
    }

    public static RepairConfig disabled() {
        return new RepairConfig(false, List.of(), RepairEconomyConfig.disabled(), DisabledDisplay.empty(), List.of(), List.of());
    }

    public boolean hasRepairMaterials() {
        return !materials.isEmpty();
    }

    public boolean hasEconomyRepair() {
        return economy.enabled() && economy.hasCurrencies();
    }
}
