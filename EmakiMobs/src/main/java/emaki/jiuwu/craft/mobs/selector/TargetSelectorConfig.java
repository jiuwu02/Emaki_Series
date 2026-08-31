package emaki.jiuwu.craft.mobs.selector;

import java.util.Map;
import java.util.Set;

public record TargetSelectorConfig(
        int snapshotIntervalTicks,
        int snapshotPlayersPerTick,
        Map<String, EquipmentWeightTable> equipmentTables,
        Map<String, SelectorDefinition> selectors,
        Map<String, String> expressions,
        Set<String> referencedEquipmentTables
) {

    public TargetSelectorConfig {
        snapshotIntervalTicks = Math.max(0, snapshotIntervalTicks);
        snapshotPlayersPerTick = Math.max(1, snapshotPlayersPerTick);
        equipmentTables = equipmentTables == null ? Map.of() : Map.copyOf(equipmentTables);
        selectors = selectors == null ? Map.of() : Map.copyOf(selectors);
        expressions = expressions == null ? Map.of() : Map.copyOf(expressions);
        referencedEquipmentTables = referencedEquipmentTables == null
                ? Set.of() : Set.copyOf(referencedEquipmentTables);
    }

    public static TargetSelectorConfig empty() {
        return new TargetSelectorConfig(0, 20, Map.of(), Map.of(), Map.of(), Set.of());
    }
}
