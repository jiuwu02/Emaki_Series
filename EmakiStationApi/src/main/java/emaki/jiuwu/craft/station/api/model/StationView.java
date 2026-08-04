package emaki.jiuwu.craft.station.api.model;

import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Read-only view of one configured crafting station.
 *
 * @param id             the station id, which is also its configuration file name
 * @param displayName    the configured display name, unrendered
 * @param layoutId       the GUI layout this station opens
 * @param permission     the permission required to open it, or an empty string to inherit the base node
 * @param recipeIds      the recipe ids resolved for this station, in stable order
 * @param baseQueueLength the station's baseline queue length
 * @param progressMode   how this station's queues advance
 * @param outputRouting  the default output destination
 * @param backpackChannel whether the inventory channel is enabled
 * @param storageChannel whether the warehouse channel is enabled by configuration
 */
public record StationView(@NotNull String id,
        @NotNull String displayName,
        @NotNull String layoutId,
        @NotNull String permission,
        @NotNull List<String> recipeIds,
        int baseQueueLength,
        @NotNull ProgressMode progressMode,
        @NotNull OutputRouting outputRouting,
        boolean backpackChannel,
        boolean storageChannel) {

    /**
     * Creates a station view with a defensively copied recipe list.
     *
     * @param id             the station id
     * @param displayName    the configured display name; {@code null} becomes the id
     * @param layoutId       the GUI layout id
     * @param permission     the access permission; {@code null} becomes an empty string
     * @param recipeIds      the resolved recipe ids; {@code null} becomes empty
     * @param baseQueueLength the baseline queue length
     * @param progressMode   how queues advance
     * @param outputRouting  the default output destination
     * @param backpackChannel whether the inventory channel is enabled
     * @param storageChannel whether the warehouse channel is enabled
     * @throws NullPointerException when {@code id}, {@code layoutId}, {@code progressMode}, or
     *         {@code outputRouting} is {@code null}
     */
    public StationView {
        if (id == null) {
            throw new NullPointerException("id");
        }
        if (layoutId == null) {
            throw new NullPointerException("layoutId");
        }
        if (progressMode == null) {
            throw new NullPointerException("progressMode");
        }
        if (outputRouting == null) {
            throw new NullPointerException("outputRouting");
        }
        displayName = displayName == null ? id : displayName;
        permission = permission == null ? "" : permission;
        recipeIds = recipeIds == null ? List.of() : List.copyOf(recipeIds);
    }

    /** {@return whether this station declares its own access permission} */
    public boolean hasOwnPermission() {
        return !permission.isBlank();
    }

    /**
     * Resolves the permission a player needs to open this station.
     *
     * @param basePermission the plugin-wide fallback node
     * @return the station's own node when configured, otherwise {@code basePermission}
     */
    public @NotNull String effectivePermission(@Nullable String basePermission) {
        if (hasOwnPermission()) {
            return permission;
        }
        return basePermission == null ? "" : basePermission;
    }
}
