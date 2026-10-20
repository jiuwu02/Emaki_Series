package emaki.jiuwu.craft.dungeon.api;

import java.util.Map;

@FunctionalInterface
public interface ScriptTriggerHandler {

    void handle(String triggerId, Map<String, String> context);
}
