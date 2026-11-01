package emaki.jiuwu.craft.dungeon.api;

import org.jetbrains.annotations.NotNull;

/**
 * Facade for compiling flow scripts and subscribing to dungeon triggers.
 */
public interface ScriptApi {

    /**
     * Compiles {@code source} as the flow script of {@code dungeonId} and
     * stages it for the dungeon's future instances.
     *
     * @param dungeonId the dungeon the script is loaded for; must exist
     * @param source the script source to compile
     * {@return the outcome; on success the value is the {@link ScriptId} of
     * the loaded script. Failures use {@code DUNGEON_NOT_FOUND},
     * {@code SCRIPT_COMPILE_ERROR}, or {@code ENGINE_UNAVAILABLE}}
     */
    @NotNull Result<ScriptId> loadFlowScript(@NotNull String dungeonId, @NotNull String source);

    /**
     * Subscribes {@code handler} to a dungeon trigger event.
     *
     * <p>The handler is invoked when the named event fires, with the
     * {@code triggerId} passed here and the trigger context.
     *
     * @param triggerId a non-blank id, unique among registered triggers
     * @param event the name of a {@code TriggerType} constant, such as
     *         {@code onReadyAll}
     * @param handler the callback to invoke when the event fires
     * {@return the outcome; fails with {@code TRIGGER_DUPLICATE} when the id
     * is already registered or {@code SCRIPT_NOT_LOADED} when no script is
     * loaded}
     * @throws IllegalArgumentException when {@code event} is not a
     *         {@code TriggerType} name or {@code triggerId} is blank
     */
    @NotNull Result<Void> registerTrigger(@NotNull String triggerId,
            @NotNull String event,
            @NotNull ScriptTriggerHandler handler);
}
