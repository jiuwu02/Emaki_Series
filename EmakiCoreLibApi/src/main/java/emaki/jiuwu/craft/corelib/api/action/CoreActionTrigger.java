package emaki.jiuwu.craft.corelib.api.action;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.api.action.pipeline.compile.TriggerContract;

/**
 * A declared pipeline trigger: what context a business moment promises to supply.
 *
 * <p>A trigger is deliberately <em>not</em> a fourth stage kind. {@link CoreActionSource},
 * {@link CoreActionGate} and {@link CoreActionStage} are invoked by the pipeline; a trigger is the
 * other direction — the owning plugin decides when its own business moment happened and dispatches a
 * pipeline itself through
 * {@code EmakiCoreLibApi#dispatchTriggerAsync(org.bukkit.plugin.Plugin, String, CoreTriggerDispatch)}.</p>
 *
 * <h2>No execute method on purpose</h2>
 * <p>This interface carries no {@code execute} or {@code dispatch} method. If CoreLib owned the
 * invocation it would also have to own the schedule — a per-tick listener or polling loop for every
 * registered trigger — and that cost lands on the server owner whether any configuration uses the
 * trigger or not. Declaring the contract here and letting the owner call the dispatch entry point
 * keeps the cost proportional to actual use.</p>
 *
 * <h2>Ids are permanent</h2>
 * <p>{@link #id()} must carry a namespace prefix, for example {@code emakiforge:forge_success}. The
 * registry rejects an unprefixed id rather than letting two plugins race for a bare name. Once an id
 * ships it appears in server-owner configuration, so renaming it silently breaks every pipeline bound
 * to the old name.</p>
 */
@ApiStatus.Experimental
public interface CoreActionTrigger {

    /**
     * The namespaced trigger id used in configuration and at the dispatch entry point.
     *
     * <p>Must be {@code namespace:name}. Registration fails when the prefix is missing.</p>
     *
     * @return the trigger id
     */
    @NotNull
    String id();

    /**
     * What this trigger provides, per phase.
     *
     * <p>Returning {@link TriggerContract#permissive(String)} keeps the load-time check off for this
     * trigger, which is what an existing untyped call site behaves like today. Returning a declared
     * contract is how a module opts into the stricter check, so declaring one can turn a runtime null
     * into a config-load error.</p>
     *
     * @return the trigger contract
     */
    @NotNull
    TriggerContract contract();

    /** {@return a short human-readable description, used by listings and documentation} */
    @NotNull
    default String description() {
        return "";
    }
}
