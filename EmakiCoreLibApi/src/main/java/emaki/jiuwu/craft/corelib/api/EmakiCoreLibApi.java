package emaki.jiuwu.craft.corelib.api;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.action.CoreAction;
import emaki.jiuwu.craft.corelib.api.action.CoreActionDescriptor;
import emaki.jiuwu.craft.corelib.api.action.CoreActionErrorType;
import emaki.jiuwu.craft.corelib.api.action.CoreActionRegistration;
import emaki.jiuwu.craft.corelib.api.action.CoreActionResult;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionGate;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionSource;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionStage;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageKind;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageRegistration;
import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.dialog.CoreLibDialogs;
import emaki.jiuwu.craft.corelib.api.item.ConfiguredItemDefinition;
import emaki.jiuwu.craft.corelib.api.item.ItemBuildResult;
import emaki.jiuwu.craft.corelib.api.item.ItemComponentCapability;
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

    /** {@return the current runtime compatibility report} */
    public static @NotNull CompatibilityReport compatibilityReport() {
        Bridge resolved = bridge;
        return resolved == null ? CompatibilityReport.unavailable() : resolved.compatibilityReport();
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
     * Registers an action owned by another plugin.
     *
     * <p>Use a stable lowercase source id such as your plugin id. The returned handle may be stored
     * and closed on plugin disable; {@link #unregisterActions(Plugin)} is an alternative.
     *
     * @param owner  plugin that owns the action lifecycle
     * @param source stable source id for grouping actions
     * @param action action implementation
     * @return registration handle and result
     */
    public static @NotNull CoreActionRegistration registerAction(@Nullable Plugin owner,
            @Nullable String source,
            @Nullable CoreAction action) {
        Bridge resolved = bridge;
        return resolved == null
                ? CoreActionRegistration.unavailable(CoreActionResult.failure(CoreActionErrorType.INVALID_STATE,
                        "EmakiCoreLib is unavailable."))
                : resolved.registerAction(owner, source, action);
    }

    /**
     * Unregisters one action by id.
     *
     * @param actionId the action id
     */
    public static void unregisterAction(@Nullable String actionId) {
        Bridge resolved = bridge;
        if (resolved != null) {
            resolved.unregisterAction(actionId);
        }
    }

    /**
     * Unregisters all actions owned by a plugin.
     *
     * @param owner the owning plugin
     */
    public static void unregisterActions(@Nullable Plugin owner) {
        Bridge resolved = bridge;
        if (resolved != null) {
            resolved.unregisterActions(owner);
        }
    }

    /**
     * Unregisters all actions registered with the given source id.
     *
     * @param source the source id
     */
    public static void unregisterActionsBySource(@Nullable String source) {
        Bridge resolved = bridge;
        if (resolved != null) {
            resolved.unregisterActionsBySource(source);
        }
    }

    /**
     * @param actionId the action id
     * @return whether that action id is currently registered
     */
    public static boolean actionRegistered(@Nullable String actionId) {
        Bridge resolved = bridge;
        return resolved != null && resolved.actionRegistered(actionId);
    }

    /**
     * @param actionId the action id
     * @return the descriptor when registered, otherwise an empty optional
     */
    public static @NotNull Optional<CoreActionDescriptor> action(@Nullable String actionId) {
        Bridge resolved = bridge;
        return resolved == null ? Optional.empty() : Optional.ofNullable(resolved.action(actionId));
    }

    /** {@return descriptors for all currently registered actions; empty when unavailable} */
    public static @NotNull List<CoreActionDescriptor> actions() {
        Bridge resolved = bridge;
        return resolved == null ? List.of() : resolved.actions();
    }

    /**
     * @param owner the owning plugin
     * @return descriptors for actions owned by that plugin; empty when unavailable
     */
    public static @NotNull List<CoreActionDescriptor> actionsByOwner(@Nullable Plugin owner) {
        Bridge resolved = bridge;
        return resolved == null ? List.of() : resolved.actionsByOwner(owner);
    }

    /**
     * @param source the source id
     * @return descriptors for actions registered with that source id; empty when unavailable
     */
    public static @NotNull List<CoreActionDescriptor> actionsBySource(@Nullable String source) {
        Bridge resolved = bridge;
        return resolved == null ? List.of() : resolved.actionsBySource(source);
    }

    /**
     * Registers an action stage into EmakiCoreLib's single stage registry.
     *
     * <p>This is the v2 counterpart of {@link #registerAction}. There is no {@code source} parameter and no
     * unregister-by-id method: a stage is revoked only through the returned handle or by its owner being
     * disabled, so one plugin can no longer retire another plugin's stage.</p>
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

        /** {@return the current runtime compatibility report} */
        @NotNull
        CompatibilityReport compatibilityReport();

        /**
         * @param itemSource item source shorthand
         * @return the unified MiniMessage display name
         */
        @NotNull
        EmakiResult<String> itemDisplayName(@Nullable String itemSource);

        /**
         * @param itemStack the stack to inspect
         * @return the unified MiniMessage display name
         */
        @NotNull
        EmakiResult<String> itemDisplayName(@Nullable ItemStack itemStack);

        /**
         * @param definition   the configured definition
         * @param replacements placeholder substitutions
         * @return the build outcome with diagnostics
         */
        @NotNull
        ItemBuildResult createConfiguredItem(@Nullable ConfiguredItemDefinition definition,
                @Nullable Map<String, ?> replacements);

        /**
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
         * @param owner  plugin that owns the action lifecycle
         * @param source stable source id
         * @param action action implementation
         * @return registration handle and result
         */
        @NotNull
        CoreActionRegistration registerAction(@Nullable Plugin owner, @Nullable String source, @Nullable CoreAction action);

        /**
         * @param actionId the action id
         */
        void unregisterAction(@Nullable String actionId);

        /**
         * @param owner the owning plugin
         */
        void unregisterActions(@Nullable Plugin owner);

        /**
         * @param source the source id
         */
        void unregisterActionsBySource(@Nullable String source);

        /**
         * @param actionId the action id
         * @return whether it is registered
         */
        boolean actionRegistered(@Nullable String actionId);

        /**
         * @param actionId the action id
         * @return the descriptor, or {@code null} when not found
         */
        @Nullable
        CoreActionDescriptor action(@Nullable String actionId);

        /** {@return descriptors for all registered actions} */
        @NotNull
        List<CoreActionDescriptor> actions();

        /**
         * @param owner the owning plugin
         * @return descriptors for actions owned by that plugin
         */
        @NotNull
        List<CoreActionDescriptor> actionsByOwner(@Nullable Plugin owner);

        /**
         * @param source the source id
         * @return descriptors for actions with that source id
         */
        @NotNull
        List<CoreActionDescriptor> actionsBySource(@Nullable String source);

        /**
         * @param owner plugin that owns the stage lifecycle
         * @param stage the action stage implementation
         * @return a revocable handle
         */
        @NotNull
        CoreStageRegistration registerActionStage(@Nullable Plugin owner, @Nullable CoreActionStage stage);

        /**
         * @param owner plugin that owns the stage lifecycle
         * @param source the source stage implementation
         * @return a revocable handle
         */
        @NotNull
        CoreStageRegistration registerActionSource(@Nullable Plugin owner, @Nullable CoreActionSource source);

        /**
         * @param owner plugin that owns the stage lifecycle
         * @param gate the gate stage implementation
         * @return a revocable handle
         */
        @NotNull
        CoreStageRegistration registerActionGate(@Nullable Plugin owner, @Nullable CoreActionGate gate);

        /**
         * @param owner plugin whose stages need re-registering after a reload
         * @param reregister the registration routine to re-run
         * @return whether the callback was accepted
         */
        boolean onStageRegistryRebuilt(@Nullable Plugin owner, @Nullable Runnable reregister);
    }
}
