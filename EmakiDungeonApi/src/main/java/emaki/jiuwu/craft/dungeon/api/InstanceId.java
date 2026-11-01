package emaki.jiuwu.craft.dungeon.api;

/**
 * Identifier of a dungeon instance.
 *
 * <p>The value doubles as the name of the world hosting the instance, so it
 * can be used anywhere a Bukkit world name is expected.
 *
 * @param value the instance id; equal to the name of the instance world
 */
public record InstanceId(String value) {
}
