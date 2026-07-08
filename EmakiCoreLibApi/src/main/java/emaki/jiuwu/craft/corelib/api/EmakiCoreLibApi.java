package emaki.jiuwu.craft.corelib.api;

import java.util.List;

import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.action.CoreAction;
import emaki.jiuwu.craft.corelib.api.action.CoreActionDescriptor;
import emaki.jiuwu.craft.corelib.api.action.CoreActionErrorType;
import emaki.jiuwu.craft.corelib.api.action.CoreActionRegistration;
import emaki.jiuwu.craft.corelib.api.action.CoreActionResult;

/**
 * Static public API facade for the shared EmakiCoreLib runtime core.
 *
 * <p>Third-party plugins should call these static methods directly. EmakiCoreLib
 * installs the backing bridge during its enable lifecycle and removes it on
 * disable.
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
    public static void install(@NotNull Bridge bridge) {
        EmakiCoreLibApi.bridge = bridge;
    }

    /**
     * Removes the backing bridge when it is still the active bridge.
     *
     * @param bridge the bridge to remove; ignored when it is not the active bridge
     */
    public static void uninstall(@Nullable Bridge bridge) {
        if (EmakiCoreLibApi.bridge == bridge) {
            EmakiCoreLibApi.bridge = null;
        }
    }

    /** {@return whether EmakiCoreLib has installed its API bridge} */
    public static boolean available() {
        return bridge != null;
    }

    /** {@return the semantic version string of this API, or an empty string when unavailable} */
    public static @NotNull String apiVersion() {
        Bridge resolved = bridge;
        return resolved == null ? "" : resolved.apiVersion();
    }

    /** {@return the owning plugin's name, or an empty string when unavailable} */
    public static @NotNull String pluginName() {
        Bridge resolved = bridge;
        return resolved == null ? "" : resolved.pluginName();
    }

    /** {@return whether the plugin has finished initializing and is usable} */
    public static boolean isReady() {
        Bridge resolved = bridge;
        return resolved != null && resolved.isReady();
    }

    /**
     * Resolves a unified display name for an item source shorthand or identifier.
     *
     * <p>The returned value is MiniMessage text and may contain translatable
     * components so the client can render vanilla names in its active language.
     *
     * @param itemSource item source shorthand such as {@code minecraft-apple},
     *                   {@code craftengine-namespace:id}, or another registered source
     * @return the resolved display name, or an empty string when unavailable
     */
    public static @NotNull String itemDisplayName(@Nullable String itemSource) {
        Bridge resolved = bridge;
        return resolved == null ? "" : resolved.itemDisplayName(itemSource);
    }

    /**
     * Resolves a unified display name for a real item stack.
     *
     * <p>The returned value is MiniMessage text and may contain translatable
     * components so the client can render vanilla names in its active language.
     *
     * @param itemStack item stack to inspect
     * @return the resolved display name, or an empty string when unavailable
     */
    public static @NotNull String itemDisplayName(@Nullable ItemStack itemStack) {
        Bridge resolved = bridge;
        return resolved == null ? "" : resolved.itemDisplayName(itemStack);
    }

    /**
     * Registers an action owned by another plugin.
     *
     * <p>Use a stable lowercase source id such as {@code emakiattribute} or your
     * plugin id. The returned handle may be stored and closed on plugin disable;
     * callers may also use {@link #unregisterActions(Plugin)}.</p>
     *
     * @param owner plugin that owns the action lifecycle
     * @param source stable source id for grouping actions
     * @param action action implementation
     * @return registration handle and result
     */
    public static @NotNull CoreActionRegistration registerAction(@Nullable Plugin owner,
            @Nullable String source,
            @Nullable CoreAction action) {
        Bridge resolved = bridge;
        return resolved == null
                ? CoreActionRegistration.unavailable(CoreActionResult.failure(CoreActionErrorType.INVALID_STATE, "EmakiCoreLib is unavailable."))
                : resolved.registerAction(owner, source, action);
    }

    /** Unregisters one action by id. */
    public static void unregisterAction(@Nullable String actionId) {
        Bridge resolved = bridge;
        if (resolved != null) {
            resolved.unregisterAction(actionId);
        }
    }

    /** Unregisters all actions owned by a plugin. */
    public static void unregisterActions(@Nullable Plugin owner) {
        Bridge resolved = bridge;
        if (resolved != null) {
            resolved.unregisterActions(owner);
        }
    }

    /** Unregisters all actions registered with the given source id. */
    public static void unregisterActionsBySource(@Nullable String source) {
        Bridge resolved = bridge;
        if (resolved != null) {
            resolved.unregisterActionsBySource(source);
        }
    }

    /** {@return whether an action id is currently registered} */
    public static boolean actionRegistered(@Nullable String actionId) {
        Bridge resolved = bridge;
        return resolved != null && resolved.actionRegistered(actionId);
    }

    /** {@return a descriptor for one action, or null when not found} */
    public static @Nullable CoreActionDescriptor action(@Nullable String actionId) {
        Bridge resolved = bridge;
        return resolved == null ? null : resolved.action(actionId);
    }

    /** {@return descriptors for all currently registered actions} */
    public static @NotNull List<CoreActionDescriptor> actions() {
        Bridge resolved = bridge;
        return resolved == null ? List.of() : resolved.actions();
    }

    /** {@return descriptors for actions owned by a plugin} */
    public static @NotNull List<CoreActionDescriptor> actionsByOwner(@Nullable Plugin owner) {
        Bridge resolved = bridge;
        return resolved == null ? List.of() : resolved.actionsByOwner(owner);
    }

    /** {@return descriptors for actions registered with a source id} */
    public static @NotNull List<CoreActionDescriptor> actionsBySource(@Nullable String source) {
        Bridge resolved = bridge;
        return resolved == null ? List.of() : resolved.actionsBySource(source);
    }

    /** Internal bridge installed by EmakiCoreLib. */
    public interface Bridge {
        /** {@return the semantic version string of the backing plugin} */
        @NotNull
        String apiVersion();

        /** {@return the owning plugin's name} */
        @NotNull
        String pluginName();

        /** {@return whether the backing plugin is initialized and usable} */
        boolean isReady();

        /** {@return unified MiniMessage display name for an item source shorthand} */
        @NotNull
        String itemDisplayName(@Nullable String itemSource);

        /** {@return unified MiniMessage display name for a real item stack} */
        @NotNull
        String itemDisplayName(@Nullable ItemStack itemStack);

        /** Registers an external action. */
        @NotNull
        CoreActionRegistration registerAction(@Nullable Plugin owner, @Nullable String source, @Nullable CoreAction action);

        /** Unregisters one action by id. */
        void unregisterAction(@Nullable String actionId);

        /** Unregisters all actions owned by a plugin. */
        void unregisterActions(@Nullable Plugin owner);

        /** Unregisters all actions registered with a source id. */
        void unregisterActionsBySource(@Nullable String source);

        /** {@return whether an action id is currently registered} */
        boolean actionRegistered(@Nullable String actionId);

        /** {@return a descriptor for one action, or null when not found} */
        @Nullable
        CoreActionDescriptor action(@Nullable String actionId);

        /** {@return descriptors for all currently registered actions} */
        @NotNull
        List<CoreActionDescriptor> actions();

        /** {@return descriptors for actions owned by a plugin} */
        @NotNull
        List<CoreActionDescriptor> actionsByOwner(@Nullable Plugin owner);

        /** {@return descriptors for actions registered with a source id} */
        @NotNull
        List<CoreActionDescriptor> actionsBySource(@Nullable String source);
    }
}
