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

/** Read-only skill definitions and player state. */
@ApiStatus.NonExtendable
public interface SkillCatalog {

    /** Definition snapshots, sorted by canonical id. Safe from any thread. */
    @NotNull List<SkillDefinitionView> skills();

    /** Case-insensitive definition lookup. Safe from any thread. */
    @NotNull Optional<SkillDefinitionView> skill(@Nullable String skillId);

    /** Player snapshot. Must run on the player's owner thread. */
    @NotNull EmakiResult<PlayerSkillView> playerSkills(@Nullable Player player);

    /** Current level. Must run on the player's owner thread. */
    @NotNull EmakiResult<Integer> level(@Nullable Player player, @Nullable String skillId);

    /** Quote for the next upgrade attempt. Must run on the player's owner thread. */
    @NotNull EmakiResult<SkillUpgradePreview> upgradePreview(@Nullable Player player, @Nullable String skillId);

    /** Whether the manual source has learned the skill. Must run on the player's owner thread. */
    @NotNull EmakiResult<Boolean> hasLearned(@Nullable Player player, @Nullable String skillId);

    /** Whether cast mode is enabled. Must run on the player's owner thread. */
    @NotNull EmakiResult<Boolean> castModeEnabled(@Nullable Player player);

    /** Whether the skill is already at maximum level. Must run on the player's owner thread. */
    @NotNull EmakiResult<Boolean> atMaxLevel(@Nullable Player player, @Nullable String skillId);
}
