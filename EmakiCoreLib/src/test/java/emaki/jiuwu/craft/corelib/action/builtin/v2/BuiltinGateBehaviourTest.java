package emaki.jiuwu.craft.corelib.action.builtin.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import emaki.jiuwu.craft.corelib.action.builtin.v2.gate.ChanceGate;
import emaki.jiuwu.craft.corelib.action.builtin.v2.gate.KeepGate;
import emaki.jiuwu.craft.corelib.action.builtin.v2.gate.LimitGate;
import emaki.jiuwu.craft.corelib.action.builtin.v2.gate.SetGate;
import emaki.jiuwu.craft.corelib.action.builtin.v2.gate.StopGate;
import emaki.jiuwu.craft.corelib.action.builtin.v2.gate.WhereGate;
import emaki.jiuwu.craft.corelib.action.v2.PipelineContext;
import emaki.jiuwu.craft.corelib.action.v2.ResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreGateResult;

/**
 * Behaviour of the gates whose logic is pure enough to run without a server.
 *
 * <p>Temporary asset for phase 3 verification.</p>
 */
class BuiltinGateBehaviourTest {

    private static final List<CoreActionSubject> THREE = List.of(
            CoreActionSubject.absent(), CoreActionSubject.absent(), CoreActionSubject.absent());

    private static PipelineContext context() {
        return PipelineContext.root(null, CoreActionSubject.absent(), null, "test", false, null);
    }

    private static ResolvedArguments args(Map<String, String> values, Object stage) {
        return ResolvedArguments.of(values, switch (stage) {
            case emaki.jiuwu.craft.corelib.api.action.v2.CoreActionGate gate -> gate.parameters();
            default -> List.of();
        });
    }

    @Test
    void limitTruncatesToTheRequestedCount() {
        LimitGate gate = new LimitGate();
        CoreGateResult result = gate.apply(context(), THREE, args(Map.of("count", "2"), gate));

        CoreGateResult.Passed passed = assertInstanceOf(CoreGateResult.Passed.class, result);
        assertEquals(2, passed.outbound().size());
    }

    @Test
    void limitZeroClearsTheFlowWithoutFailing() {
        // Zero is a legitimate request, distinct from a malformed one: it means "keep nothing".
        LimitGate gate = new LimitGate();
        CoreGateResult result = gate.apply(context(), THREE, args(Map.of("count", "0"), gate));

        assertEquals(0, assertInstanceOf(CoreGateResult.Passed.class, result).outbound().size());
    }

    @Test
    void limitRejectsANegativeCount() {
        LimitGate gate = new LimitGate();
        CoreGateResult result = gate.apply(context(), THREE, args(Map.of("count", "-1"), gate));

        assertInstanceOf(CoreGateResult.Invalid.class, result);
    }

    @Test
    void limitLeavesAShorterFlowUntouched() {
        LimitGate gate = new LimitGate();
        CoreGateResult result = gate.apply(context(), THREE, args(Map.of("count", "10"), gate));

        assertEquals(3, assertInstanceOf(CoreGateResult.Passed.class, result).outbound().size());
    }

    @Test
    void chanceOfOneAlwaysPasses() {
        ChanceGate gate = new ChanceGate();
        for (String written : List.of("100%", "1", "1.0", "1/1")) {
            CoreGateResult result = gate.apply(context(), THREE, args(Map.of("chance", written), gate));
            assertInstanceOf(CoreGateResult.Passed.class, result,
                    () -> "chance " + written + " should always pass");
        }
    }

    @Test
    void chanceOfZeroAlwaysHalts() {
        ChanceGate gate = new ChanceGate();
        for (String written : List.of("0%", "0", "0/5")) {
            CoreGateResult result = gate.apply(context(), THREE, args(Map.of("chance", written), gate));
            assertInstanceOf(CoreGateResult.Halted.class, result,
                    () -> "chance " + written + " should always halt");
        }
    }

    @Test
    void chanceRejectsUnparseableAndOutOfRangeValues() {
        ChanceGate gate = new ChanceGate();
        // A typo must be visible rather than silently behaving as "never".
        for (String written : List.of("abc", "150%", "2", "-0.5")) {
            CoreGateResult result = gate.apply(context(), THREE, args(Map.of("chance", written), gate));
            assertInstanceOf(CoreGateResult.Invalid.class, result,
                    () -> "chance " + written + " should be rejected");
        }
    }

    @Test
    void chanceRequiresAValue() {
        ChanceGate gate = new ChanceGate();
        assertInstanceOf(CoreGateResult.Invalid.class, gate.apply(context(), THREE, args(Map.of(), gate)));
    }

    @Test
    void whereKeepsTheFlowWhenTheConditionHolds() {
        WhereGate gate = new WhereGate();
        CoreGateResult result = gate.apply(context(), THREE, args(Map.of("condition", "3>2"), gate));

        assertEquals(3, assertInstanceOf(CoreGateResult.Passed.class, result).outbound().size());
    }

    @Test
    void whereClearsTheFlowWhenTheConditionFails() {
        WhereGate gate = new WhereGate();
        CoreGateResult result = gate.apply(context(), THREE, args(Map.of("condition", "1>2"), gate));

        assertEquals(0, assertInstanceOf(CoreGateResult.Passed.class, result).outbound().size());
    }

    @Test
    void whereReportsAnUnevaluableCondition() {
        WhereGate gate = new WhereGate();
        CoreGateResult result = gate.apply(context(), THREE,
                args(Map.of("condition", "not a condition"), gate));

        assertInstanceOf(CoreGateResult.Invalid.class, result);
    }

    @Test
    void setEvaluatesArithmeticAndStoresTheNumber() {
        SetGate gate = new SetGate();
        CoreGateResult result = gate.apply(context(), THREE,
                args(Map.of("damage", "4*4+2"), gate));

        CoreGateResult.Passed passed = assertInstanceOf(CoreGateResult.Passed.class, result);
        assertEquals("18", passed.variables().get("damage"));
    }

    @Test
    void setStoresNonArithmeticValuesVerbatim() {
        SetGate gate = new SetGate();
        CoreGateResult result = gate.apply(context(), THREE, args(Map.of("label", "boss_phase"), gate));

        assertEquals("boss_phase",
                assertInstanceOf(CoreGateResult.Passed.class, result).variables().get("label"));
    }

    @Test
    void setAcceptsSeveralAssignmentsAtOnce() {
        SetGate gate = new SetGate();
        CoreGateResult result = gate.apply(context(), THREE,
                args(Map.of("a", "1+1", "b", "text"), gate));

        CoreGateResult.Passed passed = assertInstanceOf(CoreGateResult.Passed.class, result);
        assertEquals("2", passed.variables().get("a"));
        assertEquals("text", passed.variables().get("b"));
    }

    @Test
    void setWithoutAssignmentsIsRejected() {
        SetGate gate = new SetGate();
        assertInstanceOf(CoreGateResult.Invalid.class, gate.apply(context(), THREE, args(Map.of(), gate)));
    }

    @Test
    void setPreservesTheTargetFlow() {
        // set adds a value; it is not a filter, so the flow must come out unchanged.
        SetGate gate = new SetGate();
        CoreGateResult result = gate.apply(context(), THREE, args(Map.of("x", "1"), gate));

        assertEquals(3, assertInstanceOf(CoreGateResult.Passed.class, result).outbound().size());
    }

    @Test
    void stopAlwaysHalts() {
        StopGate gate = new StopGate();
        CoreGateResult result = gate.apply(context(), THREE, ResolvedArguments.empty());

        assertInstanceOf(CoreGateResult.Halted.class, result);
    }

    @Test
    void keepPassesTheFlowThroughAndRecordsItsSize() {
        KeepGate gate = new KeepGate();
        CoreGateResult result = gate.apply(context(), THREE, ResolvedArguments.empty());

        CoreGateResult.Passed passed = assertInstanceOf(CoreGateResult.Passed.class, result);
        assertEquals(3, passed.outbound().size());
        assertEquals("3", passed.variables().get("keep_count"));
    }

    @Test
    void gatesDoNotAliasTheirInboundList() {
        // Returning the caller's list would let a later stage mutate a flow another stage still holds.
        LimitGate gate = new LimitGate();
        CoreGateResult result = gate.apply(context(), THREE, args(Map.of("count", "3"), gate));

        CoreGateResult.Passed passed = assertInstanceOf(CoreGateResult.Passed.class, result);
        assertTrue(passed.outbound() != THREE, "gate returned the inbound list itself");
    }
}
