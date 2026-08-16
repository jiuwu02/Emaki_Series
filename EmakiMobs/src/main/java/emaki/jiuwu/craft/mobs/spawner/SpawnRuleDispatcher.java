package emaki.jiuwu.craft.mobs.spawner;

import java.util.List;
import java.util.Map;

public final class SpawnRuleDispatcher {

    private final Map<String, SpawnHandler> handlers;

    public SpawnRuleDispatcher(NaturalSpawnHandler natural, AutonomousSpawnHandler autonomous) {
        handlers = Map.of(
                "natural",    natural,
                "autonomous", autonomous
        );
    }

    public void reload(List<SpawnRule> newRules) {
        handlers.values().forEach(SpawnHandler::clear);
        for (SpawnRule rule : newRules) {
            SpawnHandler handler = handlers.get(rule.type());
            if (handler != null) {
                handler.register(rule);
            }
        }
    }
}
