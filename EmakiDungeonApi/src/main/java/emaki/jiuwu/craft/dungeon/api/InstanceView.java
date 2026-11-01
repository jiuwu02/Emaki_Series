package emaki.jiuwu.craft.dungeon.api;

/**
 * Immutable snapshot of a live dungeon instance, as reported by
 * {@link DungeonApi#listInstances()} and {@link DungeonApi#getInstance(java.util.UUID)}.
 *
 * @param id the instance id; equal to the name of the instance world
 * @param dungeonId the id of the dungeon definition the instance runs
 * @param state the instance state name, e.g. {@code RUNNING} or
 *         {@code SETTLING}
 * @param members the current party size bound to the instance, or {@code 0}
 *         when no party is bound
 * @param worldName the name of the world hosting the instance
 */
public record InstanceView(String id,
        String dungeonId,
        String state,
        int members,
        String worldName) {
}
