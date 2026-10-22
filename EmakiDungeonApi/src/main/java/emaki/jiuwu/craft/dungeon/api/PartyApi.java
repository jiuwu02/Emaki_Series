package emaki.jiuwu.craft.dungeon.api;

import java.util.UUID;

import org.jetbrains.annotations.NotNull;

/**
 * Facade for founding parties, inviting members, and advancing challenge
 * readiness.
 */
public interface PartyApi {

    /**
     * Creates a new party led by {@code player}.
     *
     * @param player the player founding the party; must be online
     * {@return the outcome; on success the value is the new {@link PartyId}}
     */
    @NotNull Result<PartyId> create(@NotNull UUID player);

    /**
     * Invites {@code target} to the party led by {@code player}.
     *
     * @param player the party leader issuing the invite; must be online
     * @param target the player to invite; must be online
     * {@return the outcome; on success the value is an {@link InviteToken}}
     */
    @NotNull Result<InviteToken> invite(@NotNull UUID player, @NotNull UUID target);

    /**
     * Removes {@code member} from their party on the leader's behalf.
     *
     * @param member the id of the member to remove
     * {@return the outcome; fails with {@code MEMBER_NOT_FOUND} when the
     * player is in no party, or {@code NOT_LEADER} when the acting leader is
     * offline or not the leader}
     */
    @NotNull Result<Void> kick(@NotNull UUID member);

    /**
     * Confirms that {@code player} is ready, advancing the active challenge
     * session of their party.
     *
     * @param player the player confirming readiness; must be online
     * {@return the outcome; fails with {@code CHALLENGE_NOT_ACTIVE} when the
     * player's party has no challenge session}
     */
    @NotNull Result<Void> confirmReady(@NotNull UUID player);
}
