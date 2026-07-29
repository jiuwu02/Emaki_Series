package emaki.jiuwu.craft.gem.api.model;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.NotNull;

/**
 * Read-only view of a gem definition.
 *
 * <p>Stats, attributes, and skills are resolved <em>for a specific level</em>, because EmakiGem scales
 * them per level. The {@link #level()} component records which level the values belong to.
 *
 * <p>Cost tables, upgrade chains, and name/lore action scripts are configuration internals and are not
 * exposed.
 *
 * @param id                  canonical lowercase gem id
 * @param displayName         display name resolved for {@link #level()}
 * @param gemType             gem type used for socket compatibility; defaults to {@code universal}
 * @param level               the level these values were resolved for
 * @param stats               stat id to value at this level
 * @param attributes          attribute id to value at this level
 * @param skillIds            skill ids granted at this level
 * @param socketCompatibility socket types this gem may be inlaid into; empty means unrestricted
 * @param dependencies        gem ids that must already be inlaid
 * @param conflicts           gem ids that must not be inlaid
 */
public record GemDefinitionView(@NotNull String id,
                                @NotNull String displayName,
                                @NotNull String gemType,
                                int level,
                                @NotNull Map<String, Double> stats,
                                @NotNull Map<String, Double> attributes,
                                @NotNull List<String> skillIds,
                                @NotNull Set<String> socketCompatibility,
                                @NotNull List<String> dependencies,
                                @NotNull List<String> conflicts) {

    /**
     * Normalises every reference component so no accessor can return {@code null}.
     *
     * @param id                  canonical lowercase gem id
     * @param displayName         display name
     * @param gemType             gem type
     * @param level               resolved level
     * @param stats               stat values
     * @param attributes          attribute values
     * @param skillIds            granted skill ids
     * @param socketCompatibility compatible socket types
     * @param dependencies        required gem ids
     * @param conflicts           conflicting gem ids
     */
    public GemDefinitionView {
        id = id == null ? "" : id;
        displayName = displayName == null || displayName.isBlank() ? id : displayName;
        gemType = gemType == null || gemType.isBlank() ? "universal" : gemType;
        level = Math.max(1, level);
        stats = stats == null ? Map.of() : Map.copyOf(stats);
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        skillIds = skillIds == null ? List.of() : List.copyOf(skillIds);
        socketCompatibility = socketCompatibility == null ? Set.of() : Set.copyOf(socketCompatibility);
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        conflicts = conflicts == null ? List.of() : List.copyOf(conflicts);
    }

    /**
     * @param socketType the socket type to test
     * @return whether this gem may be inlaid into that socket type
     */
    public boolean supportsSocketType(@NotNull String socketType) {
        return socketCompatibility.isEmpty() || socketCompatibility.contains(socketType);
    }
}
