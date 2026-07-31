package emaki.jiuwu.craft.corelib.action.builtin.v2;

import java.util.List;

import emaki.jiuwu.craft.corelib.action.v2.registry.RegisteredStage;
import emaki.jiuwu.craft.corelib.action.v2.registry.StageRegistry;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionGate;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionSource;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionStage;

/**
 * Reads the builtin stage instances back out of a registry.
 *
 * <p>Going through {@link BuiltinStages#registerAll} rather than instantiating each class keeps the tests honest
 * about what is actually registered: a stage that exists but was left out of the registration list would not
 * appear here either.</p>
 *
 * <p>Temporary asset for phase 3 verification.</p>
 */
final class BuiltinStageFixtures {

    private BuiltinStageFixtures() {
    }

    private static StageRegistry registry() {
        StageRegistry registry = new StageRegistry();
        BuiltinStages.registerAll(registry, null, null, null, null, null, null, null, null);
        return registry;
    }

    static List<CoreActionSource> sources() {
        return registry().sources().all().stream()
                .map(RegisteredStage::stage)
                .filter(CoreActionSource.class::isInstance)
                .map(CoreActionSource.class::cast)
                .toList();
    }

    static List<CoreActionGate> gates() {
        return registry().gates().all().stream()
                .map(RegisteredStage::stage)
                .filter(CoreActionGate.class::isInstance)
                .map(CoreActionGate.class::cast)
                .toList();
    }

    static List<CoreActionStage> actions() {
        return registry().actions().all().stream()
                .map(RegisteredStage::stage)
                .filter(CoreActionStage.class::isInstance)
                .map(CoreActionStage.class::cast)
                .toList();
    }
}
