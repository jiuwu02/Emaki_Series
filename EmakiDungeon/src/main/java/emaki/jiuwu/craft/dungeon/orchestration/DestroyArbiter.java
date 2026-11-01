package emaki.jiuwu.craft.dungeon.orchestration;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class DestroyArbiter {

    private final Set<String> arbitrating = ConcurrentHashMap.newKeySet();

    public boolean tryArbitrate(String instanceId) {
        return instanceId != null && arbitrating.add(instanceId);
    }

    public void reset() {
        arbitrating.clear();
    }
}
