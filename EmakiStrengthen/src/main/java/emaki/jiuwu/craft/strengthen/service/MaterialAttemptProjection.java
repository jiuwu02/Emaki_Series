package emaki.jiuwu.craft.strengthen.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.strengthen.api.model.AttemptMaterial;

final class MaterialAttemptProjection {

    private MaterialAttemptProjection() {
    }

    static List<AttemptMaterial> project(List<MaterialIdentityPlanner.Definition> definitions,
            List<Input> inputs,
            MaterialIdentityPlanner.Plan plan) {
        if (plan == null) {
            return List.of();
        }
        Map<Integer, MaterialIdentityPlanner.Definition> definitionsByOrder = new LinkedHashMap<>();
        for (MaterialIdentityPlanner.Definition definition : definitions == null
                ? List.<MaterialIdentityPlanner.Definition>of()
                : definitions) {
            definitionsByOrder.put(definition.order(), definition);
        }
        Map<Integer, Input> inputsByIndex = new LinkedHashMap<>();
        for (Input input : inputs == null ? List.<Input>of() : inputs) {
            inputsByIndex.put(input.index(), input);
        }
        List<AttemptMaterial> result = new ArrayList<>();
        for (MaterialIdentityPlanner.Allocation allocation : plan.allocations()) {
            MaterialIdentityPlanner.Definition definition = definitionsByOrder.get(allocation.definitionOrder());
            Input input = inputsByIndex.get(allocation.inputIndex());
            if (definition == null || input == null || allocation.assigned() <= 0) {
                continue;
            }
            String item = input.item().isBlank() ? allocation.materialId() : input.item();
            int assigned = Math.min(input.availableAmount(), allocation.assigned());
            result.add(new AttemptMaterial(item, assigned, assigned, definition.optional(), allocation.protection(),
                    allocation.temperBoost(), allocation.consumed(), allocation.materialId(), allocation.countKey(),
                    allocation.inputIndex(), input.sourceToken()));
        }
        return List.copyOf(result);
    }

    record Input(int index, int availableAmount, String item, String sourceToken) {
        Input {
            availableAmount = Math.max(0, availableAmount);
            item = item == null ? "" : item;
            sourceToken = sourceToken == null ? "" : sourceToken;
        }
    }
}
