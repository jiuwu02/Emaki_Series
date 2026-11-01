/**
 * Action pipeline contracts for sources, gates, actions, and triggers — the extension SDK.
 *
 * <p><b>Registration entry points.</b> All six live on
 * {@link emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi} and are owner-scoped (owner = your plugin):
 * {@code registerActionSource(Plugin, CoreActionSource)} produces the target flow at line start;
 * {@code registerActionGate(Plugin, CoreActionGate)} filters or reorders the flow;
 * {@code registerActionStage(Plugin, CoreActionStage)} produces an effect;
 * {@code registerActionTrigger(Plugin, CoreActionTrigger)} adds a trigger contract;
 * {@code registerTargetCondition(Plugin, CoreTargetCondition)} adds a condition type usable in
 * {@code filter}; {@code registerTargetIdentityProvider(Plugin, CoreTargetIdentityProvider)} adds an
 * external-system identity. There is no unregister-by-id method: a stage is revoked only through the
 * {@link emaki.jiuwu.craft.corelib.api.action.CoreStageRegistration} handle returned at registration,
 * or automatically by CoreLib when the owner plugin is disabled. Re-registering the same id by the
 * same owner replaces the previous stage.</p>
 *
 * <p><b>Domain declaration (mandatory).</b> Every source and action must implement
 * {@code executionTarget(CoreStagePlanningContext)} and return a declared
 * {@link emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain}:
 * {@code SERVER_GLOBAL} for server-wide lookups and config writes,
 * {@code CONTEXT_ENTITY} for the current target's owner thread (inventory edits, teleport, messages),
 * {@code LOCATION_REGION} for region work (nearby searches, blocks at the origin),
 * {@code ASYNC_COMPUTE} for pure async computation — which additionally requires
 * {@link emaki.jiuwu.craft.corelib.api.action.CoreTargetRequirement#NONE}, otherwise the
 * registration is refused with {@code async_needs_target}. {@code UNDECLARED} is refused at
 * registration: on Folia an undeclared domain is a crash, not an error. Gates declare
 * {@link emaki.jiuwu.craft.corelib.api.action.CoreGateThread} instead: {@code PURE} (touches no
 * Bukkit state, always Folia-safe, the default) or {@code NEEDS_ENTITY_READ}.</p>
 *
 * <p><b>Contract checklist (validated at registration).</b> (1) {@code id()}: non-empty, lowercase,
 * no {@code =}; a duplicate id returns {@code duplicate_id} naming the current owner. (2) Domain:
 * see above. (3) Parameter table via
 * {@link emaki.jiuwu.craft.corelib.api.action.CoreStageParameter}: undeclared arguments are rejected
 * at compile time as {@code unknown_argument}, missing required ones as
 * {@code missing_required_argument}. (4) {@code targetRequirement()}: declaring
 * {@code REQUIRED_ENTITY} / {@code REQUIRED_LOCATION} / {@code REQUIRED_ANY} makes target-less calls
 * skip with {@code action.run.no_target} instead of requiring manual null checks. (5)
 * {@code requiredContext()}: typed {@link emaki.jiuwu.craft.corelib.api.action.CoreActionKey} reads
 * are checked against the trigger's {@link emaki.jiuwu.craft.corelib.api.action.pipeline.compile.PhaseContract}
 * at load time, so a missing key fails compilation rather than throwing at runtime. (6) Argument
 * types via {@link emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType}: literal values are
 * type-checked at compile time ({@code invalid_argument_type}).</p>
 *
 * <p><b>Folia notes.</b> Your stage runs in whichever domain it declared — never assume the main
 * thread. A declaration is a tightening: declaring too wide (for example an inventory-editing stage
 * as {@code SERVER_GLOBAL}) throws on Folia. Cross-domain hops go through
 * {@code EmakiCoreLibApi.scheduling()}; never touch Bukkit from an async body. Async work your stage
 * starts is yours to cancel — CoreLib does not track it.</p>
 *
 * <p><b>Re-register after reload.</b> The stage table is rebuilt when CoreLib reloads, so every
 * registration must be replayed: call {@code onStageRegistryRebuilt(owner, replayCallback)} right
 * after the first registration. {@code addStageRegistryRebuildListener(owner, callback)} appends
 * several independent callbacks for the same owner. Animation definitions are not rebuilt on reload
 * and need no callback. Check {@code EmakiCoreLibApi.status().usable()} first; when CoreLib is
 * unavailable the entry points return inactive handles and never throw.</p>
 *
 * <p><b>Verification.</b> {@code <root> debug action registry} lists every registered stage grouped
 * by source/gate/action with its owner plugin name; {@code debug action check "<stage> args"} runs
 * parse + static validation and prints every diagnostic with column and candidate suggestions;
 * {@code debug action run shadow "<pipeline line>"} dry-runs the line (sources and gates evaluate
 * for real, actions only print what would run). The same commands are documented in the project
 * docs under CoreLib / Action Extension SDK.</p>
 *
 * <p>Stages declare scheduler ownership; async stages cannot require Bukkit targets. Registrations
 * are owner-scoped, must be closed on disable, and become inactive when CoreLib is unavailable.
 */
package emaki.jiuwu.craft.corelib.api.action;
