package emaki.jiuwu.craft.item.action;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import emaki.jiuwu.craft.item.service.ItemComponentInspector;

class ItemComponentActionTest {

    private final ItemComponentInspector inspector = new ItemComponentInspector();

    @Test
    void enforcesDistinctAddModifyAndRemoveExistenceSemantics() {
        var add = new ItemComponentAction(inspector, ItemComponentAction.Operation.ADD);
        assertTrue(add.validateExistence(false).success());
        assertFalse(add.validateExistence(true).success());

        var modify = new ItemComponentAction(inspector, ItemComponentAction.Operation.MODIFY);
        assertTrue(modify.validateExistence(true).success());
        assertFalse(modify.validateExistence(false).success());

        var remove = new ItemComponentAction(inspector, ItemComponentAction.Operation.REMOVE);
        assertTrue(remove.validateExistence(true).success());
        assertTrue(remove.validateExistence(false).success());
        assertTrue(remove.validateExistence(false).skipped());
    }
}
