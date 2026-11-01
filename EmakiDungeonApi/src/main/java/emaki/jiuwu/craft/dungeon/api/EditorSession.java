package emaki.jiuwu.craft.dungeon.api;

/**
 * Handle of an open dungeon editor session.
 *
 * @param dungeonId the dungeon being edited
 * @param openedAt the time the session was opened, in epoch milliseconds
 */
public record EditorSession(String dungeonId, long openedAt) {
}
