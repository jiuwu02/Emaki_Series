package emaki.jiuwu.craft.item.model;

import java.util.List;

import emaki.jiuwu.craft.corelib.text.Texts;

/**
 * Configuration for item repair when durability reaches zero.
 */
public record RepairConfig(boolean enabled,
        List<RepairMaterial> materials,
        DisabledDisplay disabledDisplay,
        List<String> onDisabledActions,
        List<String> onRepairedActions) {

    public RepairConfig {
        materials = materials == null ? List.of() : List.copyOf(materials);
        disabledDisplay = disabledDisplay == null ? DisabledDisplay.empty() : disabledDisplay;
        onDisabledActions = onDisabledActions == null ? List.of() : List.copyOf(onDisabledActions);
        onRepairedActions = onRepairedActions == null ? List.of() : List.copyOf(onRepairedActions);
    }

    public static RepairConfig disabled() {
        return new RepairConfig(false, List.of(), DisabledDisplay.empty(), List.of(), List.of());
    }

    public boolean hasRepairMaterials() {
        return !materials.isEmpty();
    }
}
