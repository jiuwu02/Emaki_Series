package emaki.jiuwu.craft.item.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;

import emaki.jiuwu.craft.corelib.api.action.CoreActionContext;
import emaki.jiuwu.craft.corelib.api.action.CoreActionErrorType;

class ItemActionTargetResolverTest {

    private final ItemActionTargetResolver resolver = new ItemActionTargetResolver();

    @Test
    void reportsInvalidExplicitSlotsAndMissingTargets() {
        CoreActionContext emptyContext = CoreActionContext.create(null, null, "test", false);

        var invalidSlot = resolver.resolve(emptyContext, Map.of("slot", "slot_41"));
        assertFalse(invalidSlot.resolved());
        assertEquals(CoreActionErrorType.INVALID_ARGUMENT, invalidSlot.failure().errorType());

        var blankSlot = resolver.resolve(emptyContext, Map.of("slot", " "));
        assertFalse(blankSlot.resolved());
        assertEquals(CoreActionErrorType.INVALID_ARGUMENT, blankSlot.failure().errorType());

        var validSlotWithoutPlayer = resolver.resolve(emptyContext, Map.of("slot", "offhand"));
        assertFalse(validSlotWithoutPlayer.resolved());
        assertEquals(CoreActionErrorType.INVALID_STATE, validSlotWithoutPlayer.failure().errorType());

        var noTarget = resolver.resolve(emptyContext, Map.of());
        assertFalse(noTarget.resolved());
        assertEquals(CoreActionErrorType.INVALID_STATE, noTarget.failure().errorType());
    }

    @Test
    void ignoresCompatibilityKeysThatDoNotContainItemStacks() {
        Map<String, Object> sharedState = new ConcurrentHashMap<>();
        sharedState.put("item_stack", "not-an-item");
        sharedState.put("result_item", 12);
        CoreActionContext context = new CoreActionContext(
                null, null, "test", false, Map.of(), Map.of(), sharedState);

        var resolution = resolver.resolve(context, Map.of());
        assertFalse(resolution.resolved());
        assertEquals(CoreActionErrorType.INVALID_STATE, resolution.failure().errorType());
    }
}
