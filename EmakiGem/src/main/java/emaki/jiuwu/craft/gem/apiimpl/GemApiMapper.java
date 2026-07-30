package emaki.jiuwu.craft.gem.apiimpl;

import java.util.List;
import java.util.Map;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.FailureKind;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.gem.api.model.GemDefinitionView;
import emaki.jiuwu.craft.gem.api.model.GemRelationshipCheck;
import emaki.jiuwu.craft.gem.api.model.GemResonanceSlotView;
import emaki.jiuwu.craft.gem.api.model.GemResonanceView;
import emaki.jiuwu.craft.gem.api.model.GemSlotView;
import emaki.jiuwu.craft.gem.api.model.GemStateView;
import emaki.jiuwu.craft.gem.model.GemDefinition;
import emaki.jiuwu.craft.gem.model.GemItemDefinition;
import emaki.jiuwu.craft.gem.model.GemItemInstance;
import emaki.jiuwu.craft.gem.model.GemResonanceDefinition;
import emaki.jiuwu.craft.gem.model.GemState;
import emaki.jiuwu.craft.gem.service.GemStateService;

/** Mapping functions between private runtime models and stable API values. */
final class GemApiMapper {

    private GemApiMapper() {
    }

    static GemStateView stateView(GemItemDefinition itemDefinition, GemState state) {
        List<GemSlotView> slots = itemDefinition.slots().stream().map(slot -> {
            GemItemInstance assignment = state.assignment(slot.index());
            return new GemSlotView(
                    slot.index(),
                    slot.type(),
                    slot.displayName(),
                    state.isOpened(slot.index()),
                    assignment == null ? null : assignment.gemId(),
                    assignment == null ? 0 : assignment.level());
        }).toList();
        return new GemStateView(itemDefinition.id(), slots, state.updatedAt());
    }

    static GemDefinitionView definitionView(GemDefinition definition, int level) {
        int resolvedLevel = Math.max(1, level);
        return new GemDefinitionView(
                definition.id(),
                definition.displayNameForLevel(resolvedLevel),
                definition.gemType(),
                resolvedLevel,
                definition.statsForLevel(resolvedLevel),
                definition.attributesForLevel(resolvedLevel),
                definition.skillIdsForLevel(resolvedLevel),
                definition.socketCompatibility(),
                definition.dependencies(),
                definition.conflicts());
    }

    static GemRelationshipCheck relationshipCheck(GemStateService.RelationshipCheck check) {
        return check == null
                ? new GemRelationshipCheck(false, "gem.relationship_check_missing", Map.of())
                : new GemRelationshipCheck(check.allowed(), check.messageKey(), check.placeholders());
    }

    static GemResonanceView resonanceView(GemResonanceDefinition definition) {
        List<GemResonanceSlotView> pattern = definition.chain().pattern().stream()
                .map(entry -> new GemResonanceSlotView(entry.id(), entry.type(), entry.minLevel()))
                .toList();
        return new GemResonanceView(
                definition.id(),
                definition.displayName(),
                definition.priority(),
                definition.exclusiveGroup(),
                pattern,
                definition.chain().isOrdered());
    }

    static GemItemInstance readAssignment(GemStateService stateService, ItemStack equipment, int slotIndex) {
        if (stateService == null || equipment == null || equipment.getType().isAir()) {
            return null;
        }
        GemItemDefinition itemDefinition = stateService.resolveItemDefinition(equipment);
        if (itemDefinition == null) {
            return null;
        }
        GemState state = stateService.resolveState(equipment, itemDefinition);
        return state == null ? null : state.assignment(slotIndex);
    }

    static <T> EmakiResult<T> failure(String reasonKey, Map<String, Object> placeholders) {
        String key = Texts.isBlank(reasonKey) ? "gem.operation.rejected" : reasonKey;
        return EmakiResult.failure(classify(key), key, placeholders == null ? Map.of() : placeholders);
    }

    private static FailureKind classify(String reasonKey) {
        String key = Texts.lower(reasonKey);
        if (key.contains("cancel")) {
            return FailureKind.CANCELLED;
        }
        if (key.contains("player_not_found") || key.contains("target_offline")) {
            return FailureKind.TARGET_OFFLINE;
        }
        if (key.contains("wrong_thread")) {
            return FailureKind.WRONG_THREAD;
        }
        if (key.contains("invalid_args") || key.contains("invalid_input") || key.contains("amount_invalid")
                || key.contains("level_invalid") || key.contains("slot_invalid")) {
            return FailureKind.INVALID_INPUT;
        }
        if (key.contains("not_found") || key.contains("slot_empty") || key.contains("no_layer")
                || key.contains("hold_gem") || key.contains("hold_opener") || key.contains("not_recognized")) {
            return FailureKind.NOT_FOUND;
        }
        if (key.contains("unknown_error") || key.contains("apply_failed") || key.contains("result_missing")
                || key.contains("commit_missing")) {
            return FailureKind.INTERNAL_ERROR;
        }
        return FailureKind.REJECTED;
    }

}
