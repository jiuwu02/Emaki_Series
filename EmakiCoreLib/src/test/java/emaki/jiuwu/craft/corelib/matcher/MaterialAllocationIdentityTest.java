package emaki.jiuwu.craft.corelib.matcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class MaterialAllocationIdentityTest {

    private static MaterialRequest request(int quantity) {
        ItemRequirement requirement = new ItemRequirement(List.of(), new Matcher.AllMatcher(List.of()), "derived", "canonical");
        return new MaterialRequest(requirement, quantity,
                "material", "requirement", "shared-count", "slot", "audit");
    }

    @Test
    void assignmentRetainsRequestIdentity() {
        MaterialRequest request = request(2);
        MaterialAllocation.Assignment assignment = new MaterialAllocation.Assignment(0, null, 2,
                request.materialId(), request.requirementId(), request.countKey(), request.slotId(), request.auditId());
        MaterialAllocation allocation = MaterialAllocation.success(List.of(assignment));

        assertTrue(allocation.satisfied());
        assertEquals("material", assignment.materialId());
        assertEquals("requirement", assignment.requirementId());
        assertEquals("shared-count", assignment.countKey());
        assertEquals("slot", assignment.slotId());
        assertEquals("audit", assignment.auditId());
    }

    @Test
    void shortageRetainsRequestIdentity() {
        MaterialAllocation allocation = MaterialAllocator.allocate(
                List.of(request(2)), List.of(), item -> MatchContext.of(item, null, null));

        assertFalse(allocation.satisfied());
        MaterialAllocation.Shortage shortage = allocation.shortages().getFirst();
        assertEquals(2, shortage.missing());
        assertEquals("material", shortage.materialId());
        assertEquals("requirement", shortage.requirementId());
        assertEquals("shared-count", shortage.countKey());
        assertEquals("slot", shortage.slotId());
        assertEquals("audit", shortage.auditId());
    }
}
