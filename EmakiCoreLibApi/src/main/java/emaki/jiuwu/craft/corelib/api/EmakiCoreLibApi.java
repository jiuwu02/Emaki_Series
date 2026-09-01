package emaki.jiuwu.craft.corelib.api;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.action.CoreActionGate;
import emaki.jiuwu.craft.corelib.api.action.CoreActionSource;
import emaki.jiuwu.craft.corelib.api.action.CoreActionStage;
import emaki.jiuwu.craft.corelib.api.action.CoreActionTrigger;
import emaki.jiuwu.craft.corelib.api.action.CoreStageKind;
import emaki.jiuwu.craft.corelib.api.action.CoreStageRebuildRegistration;
import emaki.jiuwu.craft.corelib.api.action.CoreStageRegistration;
import emaki.jiuwu.craft.corelib.api.action.CoreTriggerDispatch;
import emaki.jiuwu.craft.corelib.api.action.CoreTriggerRegistration;
import emaki.jiuwu.craft.corelib.api.action.descriptor.CoreActionStageDescriptor;
import emaki.jiuwu.craft.corelib.api.action.descriptor.CoreActionTriggerDescriptor;
import emaki.jiuwu.craft.corelib.api.action.execution.CoreActionExecutionContext;
import emaki.jiuwu.craft.corelib.api.action.execution.CoreActionExecutionResult;
import emaki.jiuwu.craft.corelib.api.capability.ApiCapability;
import emaki.jiuwu.craft.corelib.api.capability.CapabilityRegistration;
import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.Unit;
import emaki.jiuwu.craft.corelib.api.dialog.CoreLibDialogs;
import emaki.jiuwu.craft.corelib.api.item.ConfiguredItemDefinition;
import emaki.jiuwu.craft.corelib.api.item.ItemBuildResult;
import emaki.jiuwu.craft.corelib.api.item.ItemComponentCapability;
import emaki.jiuwu.craft.corelib.api.readiness.ModuleReadinessListener;
import emaki.jiuwu.craft.corelib.api.readiness.ModuleReadinessPhase;
import emaki.jiuwu.craft.corelib.api.readiness.ReadinessRegistration;
import emaki.jiuwu.craft.corelib.api.scheduling.EmakiScheduling;

/**
 * Public facade for the shared EmakiCoreLib runtime.
 *
 * <p>Check {@link #status()} before relying on results. When CoreLib is absent, accessors return
 * no-op views and operations return {@link EmakiResult#unavailable()} or their documented empty value.
 * Depend on {@code emaki-corelib-api} as {@code provided} or {@code compileOnly}; do not shade it.
 */
public final class EmakiCoreLibApi {

    private static volatile Bridge bridge;

    private EmakiCoreLibApi() {
    }

    /** Installs the backing bridge; internal lifecycle use only. */
    @ApiStatus.Internal
    public static void install(@NotNull Bridge bridge) {
        EmakiCoreLibApi.bridge = bridge;
    }

    /** Removes {@code bridge} only when it is still active; otherwise no-op. */
    @ApiStatus.Internal
    public static void uninstall(@Nullable Bridge bridge) {
        if (EmakiCoreLibApi.bridge == bridge) {
            EmakiCoreLibApi.bridge = null;
        }
    }

    /**
     * {@return availability and identity metadata; never {@code null}, and
     * {@link emaki.jiuwu.craft.corelib.api.contract.ApiStatus#notInstalled()} when no bridge is
     * installed}
     */
    public static @NotNull emaki.jiuwu.craft.corelib.api.contract.ApiStatus status() {
        Bridge resolved = bridge;
        return resolved == null
                ? emaki.jiuwu.craft.corelib.api.contract.ApiStatus.notInstalled()
                : resolved.status();
    }

    /**
     * {@return the vanilla dialog layer; never {@code null}, and a no-op implementation when
     * EmakiCoreLib is unavailable}
     */
    public static @NotNull CoreLibDialogs dialogs() {
        Bridge resolved = bridge;
        return resolved == null ? UnavailableDialogs.INSTANCE : resolved.dialogs();
    }

    /**
     * {@return the Folia-safe scheduling view; never {@code null}, and a no-op implementation whose
     * tokens are already cancelled when EmakiCoreLib is unavailable}
     */
    public static @NotNull EmakiScheduling scheduling() {
        Bridge resolved = bridge;
        return resolved == null ? UnavailableScheduling.INSTANCE : resolved.scheduling();
    }

    /**
     * Resolves a unified display name for an item source shorthand or identifier.
     *
     * <p>The returned value is MiniMessage text and may contain translatable components so the
     * client renders vanilla names in its active language. When no resolver produces a name, the
     * given identifier is echoed back as a {@code Partial} result so callers still have renderable
     * text while knowing resolution did not really happen.
     *
     * <p><strong>Thread:</strong> any thread.
     *
     * @param itemSource item source shorthand such as {@code minecraft-apple} or
     *                   {@code craftengine-namespace:id}
     * @return the resolved display name
     */
    public static @NotNull EmakiResult<String> itemDisplayName(@Nullable String itemSource) {
        Bridge resolved = bridge;
        return resolved == null ? EmakiResult.unavailable() : resolved.itemDisplayName(itemSource);
    }

    /**
     * Resolves a unified display name for a real item stack.
     *
     * <p><strong>Thread:</strong> any thread, but the stack must not be mutated concurrently.
     *
     * @param itemStack item stack to inspect
     * @return the resolved display name
     */
    public static @NotNull EmakiResult<String> itemDisplayName(@Nullable ItemStack itemStack) {
        Bridge resolved = bridge;
        return resolved == null ? EmakiResult.unavailable() : resolved.itemDisplayName(itemStack);
    }

    /**
     * Creates an item from a version-independent configured definition.
     *
     * <p>This method deliberately does not return
     * {@link EmakiResult}. {@link ItemBuildResult} is already a complete result model: it separates
     * {@link ItemBuildResult#success()} from {@link ItemBuildResult#issues()} and reports
     * unavailability as an error issue. Wrapping it would hide the component-level diagnostics that
     * callers need exactly when the build failed.
     *
     * <p><strong>Thread:</strong> any thread.
     *
     * @param definition the configured definition
     * @return the build outcome with diagnostics; never {@code null}
     */
    public static @NotNull ItemBuildResult createConfiguredItem(@Nullable ConfiguredItemDefinition definition) {
        return createConfiguredItem(definition, Map.of());
    }

    /**
     * Creates an item after recursively replacing string values in the definition.
     *
     * <p><strong>Thread:</strong> any thread.
     *
     * @param definition   the configured definition
     * @param replacements placeholder substitutions applied to every string value
     * @return the build outcome with diagnostics; never {@code null}
     */
    public static @NotNull ItemBuildResult createConfiguredItem(@Nullable ConfiguredItemDefinition definition,
            @Nullable Map<String, ?> replacements) {
        Bridge resolved = bridge;
        return resolved == null
                ? ItemBuildResult.unavailable("EmakiCoreLib is unavailable.")
                : resolved.createConfiguredItem(definition, replacements == null ? Map.of() : replacements);
    }

    /**
     * Applies a configured definition as a patch to an existing item stack.
     *
     * <p><strong>Thread:</strong> any thread.
     *
     * @param itemStack  the stack to patch
     * @param definition the configured definition to apply
     * @return the patch outcome with diagnostics; never {@code null}
     */
    public static @NotNull ItemBuildResult applyConfiguredItem(@Nullable ItemStack itemStack,
            @Nullable ConfiguredItemDefinition definition) {
        return applyConfiguredItem(itemStack, definition, Map.of());
    }

    /**
     * Applies a configured definition after recursively replacing its string values.
     *
     * <p><strong>Thread:</strong> any thread.
     *
     * @param itemStack    the stack to patch
     * @param definition   the configured definition to apply
     * @param replacements placeholder substitutions applied to every string value
     * @return the patch outcome with diagnostics; never {@code null}
     */
    public static @NotNull ItemBuildResult applyConfiguredItem(@Nullable ItemStack itemStack,
            @Nullable ConfiguredItemDefinition definition,
            @Nullable Map<String, ?> replacements) {
        Bridge resolved = bridge;
        return resolved == null
                ? ItemBuildResult.unavailable("EmakiCoreLib is unavailable.")
                : resolved.applyConfiguredItem(itemStack, definition, replacements == null ? Map.of() : replacements);
    }

    /** {@return the current runtime and catalog item component capabilities; empty when unavailable} */
    public static @NotNull List<ItemComponentCapability> itemComponentCapabilities() {
        Bridge resolved = bridge;
        return resolved == null ? List.of() : List.copyOf(resolved.itemComponentCapabilities());
    }

    /**
     * Looks up one component capability by id. The id is normalised with {@link Locale#ROOT} and
     * given the {@code minecraft:} namespace when none is present.
     *
     * @param componentId the component id, with or without namespace
     * @return the capability when known, otherwise an empty optional
     */
    public static @NotNull Optional<ItemComponentCapability> itemComponentCapability(@Nullable String componentId) {
        if (componentId == null || componentId.isBlank()) {
            return Optional.empty();
        }
        String trimmed = componentId.trim().toLowerCase(Locale.ROOT);
        String normalized = trimmed.contains(":") ? trimmed : "minecraft:" + trimmed;
        return itemComponentCapabilities().stream()
                .filter(capability -> capability.componentId().equals(normalized))
                .findFirst();
    }

    /**
     * Compiles and executes one action pipeline line through EmakiCoreLib's shared action engine.
     *
     * <p>The method may be called from any thread. It does not promise a fixed completion thread: the
     * future may complete on the calling thread for immediate validation results, or on the final stage's
     * execution domain. Schedule through {@link #scheduling()} before touching Bukkit state in a
     * continuation.</p>
     *
     * <p>Compilation and business failures complete normally as a structured result. The runtime parser,
     * compiled representation and interpreter remain internal implementation details.</p>
     *
     * @param owner plugin that owns the execution lifecycle
     * @param line one complete action pipeline line
     * @param context immutable caster, target, origin and typed-data input; defaults when {@code null}
     * @return a future carrying compile diagnostics and the final execution outcome
     */
    public static @NotNull CompletableFuture<CoreActionExecutionResult> executeActionLineAsync(
            @Nullable Plugin owner,
            @Nullable String line,
            @Nullable CoreActionExecutionContext context) {
        Bridge resolved = bridge;
        return resolved == null
                ? CompletableFuture.completedFuture(
                        CoreActionExecutionResult.unavailable("action.execution.corelib_unavailable"))
                : resolved.executeActionLineAsync(owner, line, context);
    }

    /** {@return immutable metadata for every currently registered action stage} */
    public static @NotNull List<CoreActionStageDescriptor> actionStages() {
        Bridge resolved = bridge;
        return resolved == null ? List.of() : List.copyOf(resolved.actionStages());
    }

    /**
     * Looks up one registered stage by id.
     *
     * @param stageId stage id, matched case-insensitively
     * @return the immutable descriptor, or an empty optional
     */
    public static @NotNull Optional<CoreActionStageDescriptor> actionStage(@Nullable String stageId) {
        Bridge resolved = bridge;
        return resolved == null ? Optional.empty() : resolved.actionStage(stageId);
    }

    /** {@return immutable metadata for every currently registered action trigger} */
    public static @NotNull List<CoreActionTriggerDescriptor> actionTriggers() {
        Bridge resolved = bridge;
        return resolved == null ? List.of() : List.copyOf(resolved.actionTriggers());
    }

    /**
     * Looks up one registered trigger by id.
     *
     * @param triggerId namespaced trigger id, matched case-insensitively
     * @return the immutable descriptor, or an empty optional
     */
    public static @NotNull Optional<CoreActionTriggerDescriptor> actionTrigger(@Nullable String triggerId) {
        Bridge resolved = bridge;
        return resolved == null ? Optional.empty() : resolved.actionTrigger(triggerId);
    }

    /**
     * Registers an action stage into EmakiCoreLib's single stage registry.
     *
     * <p>There is no {@code source} parameter and no unregister-by-id method: a stage is revoked only
     * through the returned handle or by its owner being disabled, so one plugin can no longer retire
     * another plugin's stage.</p>
     *
     * <p>Keep the handle and close it in {@code onDisable}. Because a EmakiCoreLib reload rebuilds the stage
     * table, also register a rebuild callback through {@link #onStageRegistryRebuilt} or the stage will be
     * gone after the first reload.</p>
     *
     * @param owner plugin that owns the stage lifecycle
     * @param stage the stage implementation
     * @return a revocable handle; an inactive handle when EmakiCoreLib is unavailable
     */
    public static @NotNull CoreStageRegistration registerActionStage(@Nullable Plugin owner,
            @Nullable CoreActionStage stage) {
        Bridge resolved = bridge;
        return resolved == null
                ? CoreStageRegistration.unavailable(CoreStageKind.ACTION, "corelib_unavailable")
                : resolved.registerActionStage(owner, stage);
    }

    /**
     * Registers a source stage, which produces the target flow a pipeline starts from.
     *
     * @param owner plugin that owns the stage lifecycle
     * @param source the source implementation
     * @return a revocable handle; an inactive handle when EmakiCoreLib is unavailable
     */
    public static @NotNull CoreStageRegistration registerActionSource(@Nullable Plugin owner,
            @Nullable CoreActionSource source) {
        Bridge resolved = bridge;
        return resolved == null
                ? CoreStageRegistration.unavailable(CoreStageKind.SOURCE, "corelib_unavailable")
                : resolved.registerActionSource(owner, source);
    }

    /**
     * Registers a gate stage, which narrows or transforms the target flow.
     *
     * @param owner plugin that owns the stage lifecycle
     * @param gate the gate implementation
     * @return a revocable handle; an inactive handle when EmakiCoreLib is unavailable
     */
    public static @NotNull CoreStageRegistration registerActionGate(@Nullable Plugin owner,
            @Nullable CoreActionGate gate) {
        Bridge resolved = bridge;
        return resolved == null
                ? CoreStageRegistration.unavailable(CoreStageKind.GATE, "corelib_unavailable")
                : resolved.registerActionGate(owner, gate);
    }

    /**
     * Registers a namespaced trigger contract; the owner still dispatches the business moment.
     * Duplicate or unqualified ids fail. Close the handle on disable and re-register after reload via
     * {@link #onStageRegistryRebuilt}; the handle is inactive when CoreLib is unavailable.
     */
    public static @NotNull CoreTriggerRegistration registerActionTrigger(@Nullable Plugin owner,
            @Nullable CoreActionTrigger trigger) {
        Bridge resolved = bridge;
        return resolved == null
                ? CoreTriggerRegistration.unavailable("corelib_unavailable")
                : resolved.registerActionTrigger(owner, trigger);
    }

    /**
     * Dispatches a registered trigger after validating its declared phase contract. Unknown ids return
     * {@code NOT_FOUND}. The future may complete on any stage's execution domain; schedule through
     * {@link #scheduling()} before touching Bukkit state in continuations.
     */
    public static @NotNull java.util.concurrent.CompletableFuture<EmakiResult<Unit>> dispatchTriggerAsync(
            @Nullable Plugin owner,
            @Nullable String triggerId,
            @Nullable CoreTriggerDispatch dispatch) {
        Bridge resolved = bridge;
        return resolved == null
                ? java.util.concurrent.CompletableFuture.completedFuture(EmakiResult.unavailable())
                : resolved.dispatchTriggerAsync(owner, triggerId, dispatch);
    }

    /**
     * Registers a stage/trigger re-registration callback for reload. A second callback for the same owner
     * replaces the first; owner disable removes it automatically.
     *
     * <p>This compatibility method retains replacement semantics. Prefer
     * {@link #addStageRegistryRebuildListener} when one plugin has independent registrars.</p>
     */
    public static boolean onStageRegistryRebuilt(@Nullable Plugin owner, @Nullable Runnable reregister) {
        Bridge resolved = bridge;
        return resolved != null && resolved.onStageRegistryRebuilt(owner, reregister);
    }

    /**
     * Registers one independent stage/trigger re-registration callback for reload.
     *
     * <p>Callbacks from the same owner do not replace each other. Keep and close the returned handle when
     * the registrar is retired; owner disable also prevents future replay.</p>
     *
     * @param owner plugin that owns the callback lifecycle
     * @param reregister registration routine to replay after a registry rebuild
     * @return a revocable registration, inactive when CoreLib is unavailable or input is invalid
     */
    public static @NotNull CoreStageRebuildRegistration addStageRegistryRebuildListener(
            @Nullable Plugin owner,
            @Nullable Runnable reregister) {
        Bridge resolved = bridge;
        return resolved == null
                ? CoreStageRebuildRegistration.inactive()
                : resolved.addStageRegistryRebuildListener(owner, reregister);
    }

    /**
     * Publishes currently callable optional capabilities. Publication is all-or-nothing on ownership
     * conflict. Close the handle before uninstalling the provider bridge; it is inactive when unavailable.
     */
    public static @NotNull CapabilityRegistration publishCapabilities(@Nullable Plugin owner,
            @Nullable Set<ApiCapability> capabilities) {
        Bridge resolved = bridge;
        return resolved == null
                ? CapabilityRegistration.unavailable("corelib_unavailable")
                : resolved.publishCapabilities(owner, capabilities);
    }

    /**
     * Revokes every capability published by {@code owner}.
     *
     * @param owner the publishing plugin
     * @return how many capabilities were revoked; {@code 0} when EmakiCoreLib is unavailable
     */
    public static int revokeCapabilities(@Nullable Plugin owner) {
        Bridge resolved = bridge;
        return resolved == null ? 0 : resolved.revokeCapabilities(owner);
    }

    /**
     * Tests an optional capability from any thread. Keep provider calls inside the guarded branch so
     * absent optional methods are not eagerly resolved; build ids with {@link ApiCapability#of(String)}.
     */
    public static boolean hasCapability(@Nullable ApiCapability capability) {
        Bridge resolved = bridge;
        return resolved != null && resolved.hasCapability(capability);
    }

    /** {@return every published capability, immutable; empty when EmakiCoreLib is unavailable} */
    public static @NotNull Set<ApiCapability> capabilities() {
        Bridge resolved = bridge;
        return resolved == null ? Set.of() : Set.copyOf(resolved.capabilities());
    }

    /**
     * Lists the capabilities published by one plugin.
     *
     * @param pluginName the publishing plugin's name, matched case-insensitively
     * @return that plugin's capabilities, immutable; empty when unknown or unavailable
     */
    public static @NotNull Set<ApiCapability> capabilitiesOf(@Nullable String pluginName) {
        Bridge resolved = bridge;
        return resolved == null ? Set.of() : Set.copyOf(resolved.capabilitiesOf(pluginName));
    }

    /**
     * Runs {@code callback} once when a module becomes ready. If already ready, it runs synchronously
     * before return and the handle is inactive. Reloads do not re-fire it; use
     * {@link #addModuleListener} for every transition. The callback runs on the publishing thread, so
     * schedule before touching Bukkit state. Owner disable drops pending callbacks.
     */
    public static @NotNull ReadinessRegistration whenReady(@Nullable Plugin owner,
            @Nullable String moduleName,
            @Nullable Runnable callback) {
        Bridge resolved = bridge;
        return resolved == null
                ? ReadinessRegistration.inactive()
                : resolved.whenReady(owner, moduleName, callback);
    }

    /**
     * Reports whether a module has finished loading its data right now.
     *
     * <p>This is the polling counterpart of {@link #whenReady(Plugin, String, Runnable)} and is meant
     * for diagnostics or for a call site that can simply skip its work. Prefer {@code whenReady} for
     * one-off initialisation, and prefer checking at the point of use over caching the answer, since
     * a reload flips it back to {@code false} for the duration of the reload.</p>
     *
     * <p><strong>Thread:</strong> any thread.</p>
     *
     * @param moduleName the watched module's plugin name, matched case-insensitively
     * @return whether that module has published a ready state; {@code false} when EmakiCoreLib is
     *         unavailable or the module never published anything
     */
    public static boolean isModuleReady(@Nullable String moduleName) {
        Bridge resolved = bridge;
        return resolved != null && resolved.isModuleReady(moduleName);
    }

    /**
     * Registers a listener for every readiness transition. The same owner/module registration replaces
     * the previous listener and does not emit the current state immediately; query {@link #isModuleReady}
     * when needed. The listener runs on the publishing thread until the handle closes or owner disables.
     */
    public static @NotNull ReadinessRegistration addModuleListener(@Nullable Plugin owner,
            @Nullable String moduleName,
            @Nullable ModuleReadinessListener listener) {
        Bridge resolved = bridge;
        return resolved == null
                ? ReadinessRegistration.inactive()
                : resolved.addModuleListener(owner, moduleName, listener);
    }

    /**
     * Bridge contract implemented by EmakiCoreLib. Third-party plugins must not implement it.
     */
    @ApiStatus.NonExtendable
    public interface Bridge {

        /** {@return availability and identity metadata; must never be {@code null}} */
        @NotNull
        emaki.jiuwu.craft.corelib.api.contract.ApiStatus status();

        /** {@return the vanilla dialog layer} */
        @NotNull
        CoreLibDialogs dialogs();

        /** {@return the Folia-safe scheduling view} */
        @NotNull
        EmakiScheduling scheduling();

        /**
         * Backs {@link EmakiCoreLibApi#itemDisplayName(String)} by parsing the shorthand and asking the
         * registered item source providers to name it. A blank or unparseable input is reported as
         * invalid input; an unresolvable but non-blank input echoes the given text as a {@code Partial}.
         *
         * @param itemSource item source shorthand
         * @return the unified MiniMessage display name
         */
        @NotNull
        EmakiResult<String> itemDisplayName(@Nullable String itemSource);

        /**
         * Backs {@link EmakiCoreLibApi#itemDisplayName(ItemStack)} by identifying the stack's source and
         * naming it. A {@code null} or air stack is reported as invalid input; when no provider names it,
         * the stack's own effective name text is returned as a {@code Partial}, or {@code NOT_FOUND} when
         * even that is blank.
         *
         * @param itemStack the stack to inspect
         * @return the unified MiniMessage display name
         */
        @NotNull
        EmakiResult<String> itemDisplayName(@Nullable ItemStack itemStack);

        /**
         * Backs {@link EmakiCoreLibApi#createConfiguredItem(ConfiguredItemDefinition, Map)}. The facade
         * substitutes an empty map for a {@code null} {@code replacements} before calling. When the
         * runtime's configured item service is not yet built, the result carries an unavailable issue
         * rather than throwing.
         *
         * @param definition   the configured definition
         * @param replacements placeholder substitutions
         * @return the build outcome with diagnostics
         */
        @NotNull
        ItemBuildResult createConfiguredItem(@Nullable ConfiguredItemDefinition definition,
                @Nullable Map<String, ?> replacements);

        /**
         * Backs {@link EmakiCoreLibApi#applyConfiguredItem(ItemStack, ConfiguredItemDefinition, Map)}.
         * The facade substitutes an empty map for a {@code null} {@code replacements} before calling.
         * When the runtime's configured item service is not yet built, the result carries an unavailable
         * issue rather than throwing.
         *
         * @param itemStack    the stack to patch
         * @param definition   the configured definition
         * @param replacements placeholder substitutions
         * @return the patch outcome with diagnostics
         */
        @NotNull
        ItemBuildResult applyConfiguredItem(@Nullable ItemStack itemStack,
                @Nullable ConfiguredItemDefinition definition,
                @Nullable Map<String, ?> replacements);

        /** {@return the current runtime and catalog component capabilities} */
        @NotNull
        List<ItemComponentCapability> itemComponentCapabilities();

        /**
         * Backs {@link EmakiCoreLibApi#executeActionLineAsync(Plugin, String, CoreActionExecutionContext)}.
         * A default unavailable result keeps a newer API facade safe when it encounters an older runtime
         * bridge that does not implement the execution boundary yet.
         *
         * @param owner plugin that owns the execution lifecycle
         * @param line one complete action pipeline line
         * @param context immutable execution input
         * @return the structured execution result
         */
        @NotNull
        default CompletableFuture<CoreActionExecutionResult> executeActionLineAsync(
                @Nullable Plugin owner,
                @Nullable String line,
                @Nullable CoreActionExecutionContext context) {
            return CompletableFuture.completedFuture(
                    CoreActionExecutionResult.unavailable("action.execution.runtime_unsupported"));
        }

        /** {@return immutable metadata for every registered action stage} */
        @NotNull
        default List<CoreActionStageDescriptor> actionStages() {
            return List.of();
        }

        /** {@return one stage descriptor, or an empty optional} */
        @NotNull
        default Optional<CoreActionStageDescriptor> actionStage(@Nullable String stageId) {
            return Optional.empty();
        }

        /** {@return immutable metadata for every registered action trigger} */
        @NotNull
        default List<CoreActionTriggerDescriptor> actionTriggers() {
            return List.of();
        }

        /** {@return one trigger descriptor, or an empty optional} */
        @NotNull
        default Optional<CoreActionTriggerDescriptor> actionTrigger(@Nullable String triggerId) {
            return Optional.empty();
        }

        /**
         * Backs {@link EmakiCoreLibApi#registerActionStage(Plugin, CoreActionStage)} by delegating to the
         * runtime stage registry. Returns an inactive handle carrying a stable {@code reasonKey} when the
         * registry has not been built yet, instead of throwing.
         *
         * @param owner plugin that owns the stage lifecycle
         * @param stage the action stage implementation
         * @return a revocable handle
         */
        @NotNull
        CoreStageRegistration registerActionStage(@Nullable Plugin owner, @Nullable CoreActionStage stage);

        /**
         * Backs {@link EmakiCoreLibApi#registerActionSource(Plugin, CoreActionSource)} by delegating to
         * the runtime stage registry. Returns an inactive handle carrying a stable {@code reasonKey} when
         * the registry has not been built yet, instead of throwing.
         *
         * @param owner plugin that owns the stage lifecycle
         * @param source the source stage implementation
         * @return a revocable handle
         */
        @NotNull
        CoreStageRegistration registerActionSource(@Nullable Plugin owner, @Nullable CoreActionSource source);

        /**
         * Backs {@link EmakiCoreLibApi#registerActionGate(Plugin, CoreActionGate)} by delegating to the
         * runtime stage registry. Returns an inactive handle carrying a stable {@code reasonKey} when the
         * registry has not been built yet, instead of throwing.
         *
         * @param owner plugin that owns the stage lifecycle
         * @param gate the gate stage implementation
         * @return a revocable handle
         */
        @NotNull
        CoreStageRegistration registerActionGate(@Nullable Plugin owner, @Nullable CoreActionGate gate);

        /**
         * Backs {@link EmakiCoreLibApi#registerActionTrigger(Plugin, CoreActionTrigger)} by delegating to
         * the runtime trigger registry. Returns an inactive handle carrying a stable {@code reasonKey}
         * when the registry has not been built yet, instead of throwing.
         *
         * @param owner plugin that owns the trigger lifecycle
         * @param trigger the trigger declaration
         * @return a revocable handle
         */
        @NotNull
        CoreTriggerRegistration registerActionTrigger(@Nullable Plugin owner,
                @Nullable CoreActionTrigger trigger);

        /**
         * Backs {@link EmakiCoreLibApi#dispatchTriggerAsync(Plugin, String, CoreTriggerDispatch)} by
         * resolving the trigger contract and running the supplied lines through the pipeline. An
         * unregistered id resolves to a {@code notFound} failure rather than a permissive run.
         *
         * @param owner plugin dispatching the moment
         * @param triggerId the registered, namespaced trigger id
         * @param dispatch what fired, for whom, with what values
         * @return a future carrying the run outcome
         */
        @NotNull
        java.util.concurrent.CompletableFuture<EmakiResult<Unit>> dispatchTriggerAsync(
                @Nullable Plugin owner,
                @Nullable String triggerId,
                @Nullable CoreTriggerDispatch dispatch);

        /**
         * Backs {@link EmakiCoreLibApi#onStageRegistryRebuilt(Plugin, Runnable)} by storing the callback
         * in the runtime's rebuild listener table. Registering twice for one owner replaces the previous
         * callback; callbacks are dropped when the owning plugin is disabled.
         *
         * @param owner plugin whose stages need re-registering after a reload
         * @param reregister the registration routine to re-run
         * @return whether the callback was accepted
         */
        boolean onStageRegistryRebuilt(@Nullable Plugin owner, @Nullable Runnable reregister);

        /**
         * Backs {@link EmakiCoreLibApi#addStageRegistryRebuildListener(Plugin, Runnable)} with an
         * independent owner-scoped registration.
         */
        @NotNull
        default CoreStageRebuildRegistration addStageRegistryRebuildListener(@Nullable Plugin owner,
                @Nullable Runnable reregister) {
            return CoreStageRebuildRegistration.inactive();
        }

        /**
         * Backs {@link EmakiCoreLibApi#publishCapabilities(Plugin, Set)} by delegating to the runtime
         * capability registry. Publication is all-or-nothing: a capability already owned by another
         * plugin fails the whole batch and names the first owner through
         * {@link CapabilityRegistration#reasonKey()}.
         *
         * @param owner plugin that owns the capability lifecycle
         * @param capabilities the capabilities to publish
         * @return a revocable handle
         */
        @NotNull
        CapabilityRegistration publishCapabilities(@Nullable Plugin owner,
                @Nullable Set<ApiCapability> capabilities);

        /**
         * Backs {@link EmakiCoreLibApi#revokeCapabilities(Plugin)} by revoking every capability the given
         * owner published.
         *
         * @param owner the publishing plugin
         * @return how many capabilities were revoked
         */
        int revokeCapabilities(@Nullable Plugin owner);

        /**
         * Backs {@link EmakiCoreLibApi#hasCapability(ApiCapability)}. Callable from any thread.
         *
         * @param capability the capability to test
         * @return whether it is published and its owner is still enabled
         */
        boolean hasCapability(@Nullable ApiCapability capability);

        /**
         * {@return every published capability}
         *
         * <p>The facade defensively copies this into an immutable set before handing it out.
         */
        @NotNull
        Set<ApiCapability> capabilities();

        /**
         * Backs {@link EmakiCoreLibApi#capabilitiesOf(String)}, matching the plugin name
         * case-insensitively. The facade defensively copies the result into an immutable set.
         *
         * @param pluginName the publishing plugin's name
         * @return that plugin's capabilities
         */
        @NotNull
        Set<ApiCapability> capabilitiesOf(@Nullable String pluginName);

        /**
         * Backs {@link EmakiCoreLibApi#whenReady(Plugin, String, Runnable)} by delegating to the
         * runtime readiness registry. Runs the callback synchronously and returns an inactive handle
         * when the module is already ready.
         *
         * @param owner      plugin that owns the callback lifecycle
         * @param moduleName the watched module's plugin name
         * @param callback   what to run once that module's data is loaded
         * @return a revocable handle
         */
        @NotNull
        ReadinessRegistration whenReady(@Nullable Plugin owner,
                @Nullable String moduleName,
                @Nullable Runnable callback);

        /**
         * Backs {@link EmakiCoreLibApi#isModuleReady(String)}, matching the plugin name
         * case-insensitively. Callable from any thread.
         *
         * @param moduleName the watched module's plugin name
         * @return whether that module has published a ready state
         */
        boolean isModuleReady(@Nullable String moduleName);

        /**
         * Backs {@link EmakiCoreLibApi#addModuleListener(Plugin, String, ModuleReadinessListener)} by
         * delegating to the runtime readiness registry. Replaces that owner's previous listener for
         * the same module and does not invoke it at registration time.
         *
         * @param owner      plugin that owns the listener lifecycle
         * @param moduleName the watched module's plugin name
         * @param listener   what to notify on every transition
         * @return a revocable handle
         */
        @NotNull
        ReadinessRegistration addModuleListener(@Nullable Plugin owner,
                @Nullable String moduleName,
                @Nullable ModuleReadinessListener listener);
    }
}
