package emaki.jiuwu.craft.dungeon.api;

/**
 * Identifier of a dungeon challenge that has been started but not yet bound
 * to an instance world.
 *
 * <p>Entry calls report success with a {@link ChallengeId} while the party is
 * still waiting for an instance world to be assigned, and with an
 * {@link InstanceId} once a player has been placed into one.
 *
 * @param value the id of the party that launched the challenge, in text form
 */
public record ChallengeId(String value) {
}
