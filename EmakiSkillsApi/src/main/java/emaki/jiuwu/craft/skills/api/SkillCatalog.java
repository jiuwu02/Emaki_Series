package emaki.jiuwu.craft.skills.api;

import java.util.List;
import java.util.Optional;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.skills.api.model.PlayerSkillView;
import emaki.jiuwu.craft.skills.api.model.SkillDefinitionView;
import emaki.jiuwu.craft.skills.api.model.SkillUpgradePreview;

/**
 * Read-only skill definitions and player state.
 *
 * <p>Reached through {@code EmakiSkillsApi.catalog()}. Collection and {@link Optional} returns are empty
 * rather than {@code null} when EmakiSkills is absent, and the {@link EmakiResult} returns carry
 * {@link emaki.jiuwu.craft.corelib.api.contract.FailureKind#UNAVAILABLE}; availability must be classified
 * on the result, never by catching {@code NullPointerException}.
 *
 * <p><strong>Thread:</strong> {@link #skills()} and {@link #skill(String)} read loaded definitions and are
 * safe from any thread. Every per-player query reads live player state and must run on the player's owner
 * thread, returning {@link emaki.jiuwu.craft.corelib.api.contract.FailureKind#WRONG_THREAD} elsewhere.
 *
 * <p>Skill ids are normalized before matching (trimmed, lower-cased with {@code Locale.ROOT}, spaces folded
 * to {@code _}), so caller casing and padding are irrelevant.
 */
@ApiStatus.NonExtendable
public interface SkillCatalog {

    /**
     * Snapshots every loaded skill definition, including definitions that are currently disabled.
     *
     * <p><strong>Thread:</strong> any thread.
     *
     * @return an immutable list of definition snapshots sorted by canonical id, empty while EmakiSkills is
     *         unavailable or no definitions are loaded; never {@code null}
     */
    @NotNull List<SkillDefinitionView> skills();

    /**
     * Looks up one skill definition.
     *
     * <p><strong>Thread:</strong> any thread.
     *
     * @param skillId the id to resolve; {@code null} or blank simply yields an empty result rather than an
     *                error, and matching is case-insensitive after id normalization
     * @return the matching definition snapshot, or empty when the id is blank, unknown, or EmakiSkills is
     *         unavailable; never {@code null}
     */
    @NotNull Optional<SkillDefinitionView> skill(@Nullable String skillId);

    /**
     * Snapshots one player's slot bindings and cast-mode flag.
     *
     * <p><strong>Thread:</strong> the player's owner thread.
     *
     * @param player the player to read; {@code null} yields {@code INVALID_INPUT} and offline yields
     *               {@code TARGET_OFFLINE}
     * @return the player's skill snapshot, or {@code NOT_FOUND} when their profile is not loaded yet (a
     *         join that has not finished loading, for example)
     */
    @NotNull EmakiResult<PlayerSkillView> playerSkills(@Nullable Player player);

    /**
     * Reads a player's current level for one skill.
     *
     * <p><strong>Thread:</strong> the player's owner thread.
     *
     * @param player  the player to read
     * @param skillId the skill to read; blank yields {@code INVALID_INPUT} and an unknown id yields
     *                {@code NOT_FOUND}
     * @return the stored level clamped into the skill's valid range, which is {@code 1} for a player who has
     *         never upgraded it, or a classified failure
     */
    @NotNull EmakiResult<Integer> level(@Nullable Player player, @Nullable String skillId);

    /**
     * Quotes the next upgrade attempt: target level, success rate and the currency and material costs.
     *
     * <p>This is a quote, not a reservation. Nothing is charged or locked, and a later
     * {@link SkillOperations#upgrade} may see different numbers if the player's state changed in between.
     *
     * <p><strong>Thread:</strong> the player's owner thread.
     *
     * @param player  the player the quote is built for
     * @param skillId the skill to quote; blank yields {@code INVALID_INPUT} and an unknown id yields
     *                {@code NOT_FOUND}
     * @return the upgrade quote, {@code REJECTED} when the skill has upgrades disabled or is already at
     *         maximum level, or {@code INTERNAL_ERROR} when the preview could not be built
     */
    @NotNull EmakiResult<SkillUpgradePreview> upgradePreview(@Nullable Player player, @Nullable String skillId);

    /**
     * Tests only the manually learned set, the source written by {@link SkillOperations#learn}. A skill
     * granted purely by equipment or by a third-party {@link SkillSourceProvider} reads as {@code false}
     * here even though the player can use it, so this is not a general "can the player use this skill" check.
     *
     * <p><strong>Thread:</strong> the player's owner thread.
     *
     * @param player  the player to test
     * @param skillId the skill to test; blank yields {@code INVALID_INPUT} and an unknown id yields
     *                {@code NOT_FOUND}
     * @return whether the manual source contains the skill, kept distinct from the unavailable and
     *         wrong-thread failures so a legitimate {@code false} is never confused with "could not check"
     */
    @NotNull EmakiResult<Boolean> hasLearned(@Nullable Player player, @Nullable String skillId);

    /**
     * Reads the player's cast-mode flag, which gates the trigger-bound cast paths.
     *
     * <p><strong>Thread:</strong> the player's owner thread.
     *
     * @param player the player to test
     * @return whether cast mode is enabled, without collapsing unavailable or wrong-thread into the
     *         legitimate business value {@code false}
     */
    @NotNull EmakiResult<Boolean> castModeEnabled(@Nullable Player player);

    /**
     * Tests whether a skill has reached its configured maximum level for this player. A skill with upgrades
     * disabled has a maximum of {@code 1} and therefore reports {@code true}.
     *
     * <p><strong>Thread:</strong> the player's owner thread.
     *
     * @param player  the player to test
     * @param skillId the skill to test; blank yields {@code INVALID_INPUT} and an unknown id yields
     *                {@code NOT_FOUND}
     * @return whether the current level is at or above the maximum, without collapsing unavailable or
     *         wrong-thread into the legitimate business value {@code false}
     */
    @NotNull EmakiResult<Boolean> atMaxLevel(@Nullable Player player, @Nullable String skillId);
}
