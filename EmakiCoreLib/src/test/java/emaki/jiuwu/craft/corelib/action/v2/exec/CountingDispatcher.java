package emaki.jiuwu.craft.corelib.action.v2.exec;

import java.util.ArrayList;
import java.util.List;

/**
 * Counts dispatches so tests can assert the merging rule itself.
 *
 * <p>The design states that same-domain stages must merge and that one dispatch per stage would be
 * worse than v1. That is a claim about dispatch count, so behavioural assertions alone cannot catch a
 * regression here.</p>
 */
final class CountingDispatcher {

    private final List<String> dispatches = new ArrayList<>();

    StageDispatcher asStageDispatcher() {
        return StageDispatcher.counting(dispatches::add);
    }

    List<String> dispatches() {
        return List.copyOf(dispatches);
    }

    int count() {
        return dispatches.size();
    }
}
