package emaki.jiuwu.craft.level.api;

import java.util.List;
import java.util.Map;

/**
 * Read-only snapshot of one configured level type.
 *
 * <p>The view is a pure projection of the type's configuration and holds no reference to the
 * live registry, so a snapshot taken before a reload goes stale. The compact constructor
 * normalizes a {@code null} {@code id} to an empty string, a {@code null} {@code displayName} to
 * the id, and a {@code null} {@code description} or {@code attributes} map to an empty immutable
 * collection. Instances are immutable.
 *
 * @param id            the level type id; {@code null} becomes an empty string
 * @param displayName   the configured display name with MiniMessage markup unrendered;
 *                      {@code null} falls back to {@code id}
 * @param description   the lore lines shown for the type; {@code null} becomes empty
 * @param primary       whether the type is flagged as a primary level type in its configuration
 * @param enabled       whether the type takes effect; disabled types reject operations
 * @param startLevel    the level new players start at
 * @param maxLevel      the level cap
 * @param autoUpgrade   whether the level upgrades automatically once the requirement is met
 * @param manualUpgrade whether the player may trigger the upgrade from the GUI or command
 * @param attributes    configured attribute id to contribution expression entries evaluated
 *                      against the player's level to feed EmakiAttribute; {@code null} becomes
 *                      empty
 */
public record LevelTypeView(String id,
        String displayName,
        List<String> description,
        boolean primary,
        boolean enabled,
        int startLevel,
        int maxLevel,
        boolean autoUpgrade,
        boolean manualUpgrade,
        Map<String, String> attributes) {

    /**
     * Normalizes null identity and collection fields.
     *
     * @param id            the level type id; {@code null} becomes an empty string
     * @param displayName   the display name; {@code null} becomes {@code id}
     * @param description   the lore lines; {@code null} becomes empty
     * @param primary       the primary flag
     * @param enabled       the enabled flag
     * @param startLevel    the start level
     * @param maxLevel      the level cap
     * @param autoUpgrade   the auto-upgrade flag
     * @param manualUpgrade the manual-upgrade flag
     * @param attributes    attribute contribution expressions; {@code null} becomes empty
     */
    public LevelTypeView {
        id = id == null ? "" : id;
        displayName = displayName == null ? id : displayName;
        description = description == null ? List.of() : List.copyOf(description);
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
