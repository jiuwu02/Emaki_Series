package emaki.jiuwu.craft.dungeon.api;

import org.jetbrains.annotations.NotNull;

public interface ScriptApi {

    @NotNull Result<ScriptId> loadFlowScript(@NotNull String dungeonId, @NotNull String source);

    @NotNull Result<Void> registerTrigger(@NotNull String triggerId,
            @NotNull String event,
            @NotNull ScriptTriggerHandler handler);
}
