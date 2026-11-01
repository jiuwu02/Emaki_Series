package emaki.jiuwu.craft.dungeon.script;

import java.util.Map;

import emaki.jiuwu.craft.dungeon.trigger.TriggerType;

@FunctionalInterface
public interface TriggerSink {

    void fire(TriggerType type, String instanceId, String dungeonId, Map<String, String> context);
}
