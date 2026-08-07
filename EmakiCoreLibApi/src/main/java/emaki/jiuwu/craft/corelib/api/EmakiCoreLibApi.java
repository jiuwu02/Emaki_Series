package emaki.jiuwu.craft.corelib.api;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.action.CoreActionGate;
import emaki.jiuwu.craft.corelib.api.action.CoreActionSource;
import emaki.jiuwu.craft.corelib.api.action.CoreActionStage;
import emaki.jiuwu.craft.corelib.api.action.CoreStageKind;
import emaki.jiuwu.craft.corelib.api.action.CoreStageRegistration;
import emaki.jiuwu.craft.corelib.api.capability.ApiCapability;
import emaki.jiuwu.craft.corelib.api.capability.CapabilityRegistration;
import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.dialog.CoreLibDialogs;
import emaki.jiuwu.craft.corelib.api.item.ConfiguredItemDefinition;
import emaki.jiuwu.craft.corelib.api.item.ItemBuildResult;
import emaki.jiuwu.craft.corelib.api.item.ItemComponentCapability;
import emaki.jiuwu.craft.corelib.api.readiness.ModuleReadinessListener;
import emaki.jiuwu.craft.corelib.api.readiness.ModuleReadinessPhase;
import emaki.jiuwu.craft.corelib.api.readiness.ReadinessRegistration;
import emaki.jiuwu.craft.corelib.api.scheduling.EmakiScheduling;

/**
 * Static public API facade for the shared EmakiCoreLib runtime core.
 *
 * <p>This facade is a deliberately narrow door for third-party plugins. The ten Emaki business
 * modules do not go through it; they link EmakiCoreLib's implementation classes directly via
 * {@code join-classpath: true}. Consequently this facade exposes only what an outside plugin needs
 * and will not grow into a full isolation layer.
 *
 * <h2>Availability</h2>
 * Call {@link #status()} and check {@link emaki.jiuwu.craft.corelib.api.contract.ApiStatus#usable()}
 * before relying on results. Every method degrades safely when EmakiCoreLib is absent: accessors
 * return no-op implementations and operations return
 * {@link EmakiResult#unavailable()}.
 *
 * <h2>Not exposed</h2>
 * Text rendering, YAML services, GUI infrastructure, the expression engine, language loading,
 * bootstrap, condition evaluation, PDC services, lifecycle coordinators, economy, the internal event
 * bus, placeholder registry, and the assembly subsystem are EmakiCoreLib internals. Third-party
 * plugins should use their own implementations.
 *
 * <h2>Do not shade</h2>
 * Depend on {@code emaki-corelib-api} with {@code provided} or {@code compileOnly}. EmakiCoreLib's
 * jar already carries an un-relocated copy of these classes.
 */
public final class EmakiCoreLibApi {

    private static volatile Bridge bridge;

    private EmakiCoreLibApi() {
    }

    /**
     * Installs the backing bridge. Intended for EmakiCoreLib's lifecycle only.
     *
     * @param bridge the active bridge implementation supplied by EmakiCoreLib
     */
    @ApiStatus.Internal
    public static void install(@NotNull Bridge bridge) {
        EmakiCoreLibApi.bridge = bridge;
    }

    /**
     * Removes the backing bridge when it is still the active bridge.
     *
     * @param bridge the bridge to remove; ignored when it is not the active bridge
     */
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
     * Asks EmakiCoreLib to call {@code reregister} whenever it rebuilds the stage table.
     *
     * <p>A EmakiCoreLib reload builds a fresh stage table and retires the previous one, so stages registered
     * once at {@code onEnable} would silently disappear after the first {@code /emakicorelib reload}. Register
     * the same method that performs your initial registration here and it will be re-run against the new
     * table.</p>
     *
     * <p>Registering twice for one owner replaces the previous callback rather than adding a second one.
     * Callbacks are removed automatically when the owning plugin is disabled.</p>
     *
     * @param owner plugin whose stages need re-registering
     * @param reregister the registration routine to re-run; ignored when {@code null}
     * @return whether the callback was accepted
     */
    public static boolean onStageRegistryRebuilt(@Nullable Plugin owner, @Nullable Runnable reregister) {
        Bridge resolved = bridge;
        return resolved != null && resolved.onStageRegistryRebuilt(owner, reregister);
    }

    /**
     * Publishes optional API capabilities on behalf of {@code owner}.
     *
     * <p>Call this right after installing your own API bridge in {@code onEnable}, and close the
     * returned handle right before uninstalling it in {@code onDisable}. Publish a capability only
     * while the matching method is genuinely callable: if a config switch turns the feature off, do
     * not publish it, because a consumer that gated on it and then got rejected is worse off than a
     * consumer that never called at all.</p>
     *
     * <p>Publication is all-or-nothing. A capability already owned by another plugin fails the whole
     * batch and names the first owner in {@link CapabilityRegistration#reasonKey()} instead of
     * silently taking it over, because one capability identifier must have exactly one owner.</p>
     *
     * @param owner plugin that owns the capability lifecycle
     * @param capabilities the capabilities to publish
     * @return a revocable handle; an inactive handle when EmakiCoreLib is unavailable
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
     * Reports whether an optional capability is available right now.
     *
     * <p>Keep the guarded call inside the {@code if} body. The JVM only resolves a method reference
     * when the {@code invoke} instruction executes, so a guarded call never triggers
     * {@link NoSuchMethodError} while the capability is absent &mdash; but that only holds if the call
     * is not hoisted into a field initialiser, a static block, or an eagerly evaluated argument. See
     * {@link ApiCapability} for the full rule, including why the identifier must come from
     * {@link ApiCapability#of(String)} rather than from the provider's own API jar.</p>
     *
     * <p><strong>Thread:</strong> any thread.</p>
     *
     * @param capability the capability to test
     * @return whether it is published and its owner is still enabled
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
     * Runs {@code callback} once the named module has finished loading its data.
     *
     * <p>This exists because {@code softdepend} cannot express it. Plugin dependencies only order
     * {@code onEnable} calls; a module that loads asynchronously and publishes its data through the
     * scheduler finishes after the enable loop, so reading {@code status().usable()} from another
     * plugin's {@code onEnable} observes {@code ready == false} structurally rather than
     * occasionally. Waiting here is the supported way to observe the transition.</p>
     *
     * <p>When the module is already ready the callback runs <strong>synchronously, before this
     * method returns</strong>, and the returned handle is already inactive. There is therefore no
     * window in which a late registration silently misses the signal.</p>
     *
     * <p>The callback runs <strong>once</strong>. A module that reloads goes back to not-ready and
     * becomes ready again, but an already-fired callback is not re-run. To follow every reload use
     * {@link #addModuleListener(Plugin, String, ModuleReadinessListener)}, or simply re-check at the
     * point of use. Registering the same owner twice adds a second callback rather than replacing the
     * first.</p>
     *
     * <p><strong>Do not re-register from inside the callback</strong> to emulate a standing listener.
     * The module is already marked ready by the time callbacks run, so the re-registration takes the
     * already-ready path, fires synchronously, registers again, and recurses until the stack
     * overflows.</p>
     *
     * <p><strong>Thread:</strong> callable from any thread. The callback runs on whichever thread
     * marked the module ready, which is not guaranteed to be a Bukkit owner thread; schedule
     * explicitly before touching players, inventories, worlds or GUIs.</p>
     *
     * @param owner      plugin that owns the callback lifecycle; its callbacks are dropped when it is
     *                   disabled
     * @param moduleName the watched module's plugin name, such as {@code "EmakiItem"}, matched
     *                   case-insensitively. Pass a literal rather than a constant from that module's
     *                   API jar, for the same class-loading reason documented on
     *                   {@link ApiCapability#of(String)}
     * @param callback   what to run once the module's data is loaded
     * @return a revocable handle; an inactive handle when EmakiCoreLib is unavailable, the arguments
     *         are unusable, or the callback already ran synchronously
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
     * Registers a standing listener for one module's readiness transitions.
     *
     * <p>This is the "follow every reload" counterpart of
     * {@link #whenReady(Plugin, String, Runnable)}. Use it when you cache another module's content:
     * invalidate on {@link ModuleReadinessPhase#LOADING}, rebuild on
     * {@link ModuleReadinessPhase#READY}.</p>
     *
     * <pre>{@code
     * EmakiCoreLibApi.addModuleListener(this, "EmakiItem", phase -> {
     *     switch (phase) {
     *         case LOADING, ABSENT -> myCache.invalidate();
     *         case READY -> myCache.rebuild();
     *     }
     * });
     * }</pre>
     *
     * <p>The listener stays registered until the returned handle is closed, so unlike
     * {@code whenReady} it is notified on every transition rather than once. Registering the same
     * owner for the same module again <strong>replaces</strong> the previous listener instead of
     * adding a second one, which keeps a plugin whose {@code onEnable} runs twice from rebuilding its
     * cache twice.</p>
     *
     * <p>Registering while the module is already ready does <strong>not</strong> invoke the listener
     * immediately. {@code whenReady} does that to close its missed-signal window; a standing listener
     * has no such window, and an immediate call would make "registered" and "notified"
     * indistinguishable. Query {@link #isModuleReady(String)} if you need the current state at
     * registration time.</p>
     *
     * <p>Do <strong>not</strong> emulate this by re-registering from inside a {@code whenReady}
     * callback: the module is already marked ready when callbacks run, so the re-registration fires
     * synchronously and recurses until the stack overflows.</p>
     *
     * <p><strong>Thread:</strong> callable from any thread. The listener runs on whichever thread
     * published the transition, which is not guaranteed to be a Bukkit owner thread; schedule
     * explicitly before touching players, inventories, worlds or GUIs.</p>
     *
     * @param owner      plugin that owns the listener lifecycle; its listeners are dropped when it is
     *                   disabled
     * @param moduleName the watched module's plugin name, such as {@code "EmakiItem"}, matched
     *                   case-insensitively. Pass a literal rather than a constant from that module's
     *                   API jar, for the same class-loading reason documented on
     *                   {@link ApiCapability#of(String)}
     * @param listener   what to notify on every transition
     * @return a revocable handle; an inactive handle when EmakiCoreLib is unavailable or the arguments
     *         are unusable
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
