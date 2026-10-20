package emaki.jiuwu.craft.dungeon.orchestration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import emaki.jiuwu.craft.dungeon.instance.DungeonInstance;
import emaki.jiuwu.craft.dungeon.instance.InstanceState;
import emaki.jiuwu.craft.dungeon.instance.WorldCommandQueue;

class DestroyArbiterTest {

    @Test
    void tryArbitrateIsIdempotentPerInstance() {
        DestroyArbiter arbiter = new DestroyArbiter();
        assertTrue(arbiter.tryArbitrate("emd_a"));
        assertFalse(arbiter.tryArbitrate("emd_a"));
        assertTrue(arbiter.tryArbitrate("emd_b"));
        arbiter.reset();
        assertTrue(arbiter.tryArbitrate("emd_a"));
    }

    @Test
    void tryArbitrateRejectsNullInstance() {
        DestroyArbiter arbiter = new DestroyArbiter();
        assertFalse(arbiter.tryArbitrate(null));
    }

    @Test
    void instanceStatesCoverLifecycle() {
        assertArrayEquals(new InstanceState[] {
                InstanceState.CREATING,
                InstanceState.MOUNTING,
                InstanceState.IDLE,
                InstanceState.RUNNING,
                InstanceState.SETTLING,
                InstanceState.ABORTING,
                InstanceState.FAILING,
                InstanceState.DISPOSING,
                InstanceState.DISPOSED
        }, InstanceState.values());
    }

    @Test
    void transitionIsCasAndRejectsStaleSource() {
        DungeonInstance instance = new DungeonInstance("emd_t", "demo",
                new WorldCommandQueue(total -> {
                }));
        assertTrue(instance.state() == InstanceState.CREATING);
        assertTrue(instance.transition(InstanceState.CREATING, InstanceState.RUNNING));
        assertFalse(instance.transition(InstanceState.CREATING, InstanceState.MOUNTING));
        assertTrue(instance.transition(InstanceState.RUNNING, InstanceState.DISPOSING));
        assertTrue(instance.transition(InstanceState.DISPOSING, InstanceState.DISPOSED));
        assertFalse(instance.transition(InstanceState.DISPOSING, InstanceState.DISPOSED));
    }
}
