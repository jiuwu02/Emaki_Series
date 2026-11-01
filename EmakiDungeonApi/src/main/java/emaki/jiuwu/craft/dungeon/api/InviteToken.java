package emaki.jiuwu.craft.dungeon.api;

/**
 * Opaque token returned when a party invite is issued.
 *
 * @param value the token value; in the bundled implementation the text form
 *         of the invited player's {@code UUID}
 */
public record InviteToken(String value) {
}
