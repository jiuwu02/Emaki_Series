package emaki.jiuwu.craft.level.api;

import java.util.Map;
import java.util.UUID;

/**
 * Read-only snapshot of one player's levels across every loaded level type.
 *
 * <p>{@code levels} is keyed by level type id and only covers the types loaded when the
 * snapshot was taken. The compact constructor normalizes a {@code null} {@code name} to an empty
 * string and a {@code null} map to an empty immutable map, and takes {@code uuid} as given.
 * Instances are immutable; a snapshot is not refreshed after reloads or further writes.
 *
 * @param uuid   the player the snapshot belongs to
 * @param name   the player's name at snapshot time; {@code null} becomes an empty string
 * @param levels level type id to entry; {@code null} becomes an empty immutable map
 */
public record PlayerLevelView(UUID uuid,
        String name,
        Map<String, PlayerLevelEntryView> levels) {

    /**
     * Normalizes the name and freezes the level map.
     *
     * @param uuid   the player the snapshot belongs to
     * @param name   the player's name; {@code null} becomes an empty string
     * @param levels level type id to entry; {@code null} becomes an empty immutable map
     */
    public PlayerLevelView {
        name = name == null ? "" : name;
        levels = levels == null ? Map.of() : Map.copyOf(levels);
    }
}
