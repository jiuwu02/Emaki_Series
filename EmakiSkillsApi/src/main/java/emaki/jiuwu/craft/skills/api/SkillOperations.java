package emaki.jiuwu.craft.skills.api;

import java.util.concurrent.CompletableFuture;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.Unit;
import emaki.jiuwu.craft.skills.api.model.SkillCastOutcome;
import emaki.jiuwu.craft.skills.api.model.SkillUpgradeOutcome;

/** State-changing skill operations. */
@ApiStatus.NonExtendable
public interface SkillOperations {

    /**
     * Casts a skill resolved through the player's equipped trigger binding.
     *
     * <p>The returned future is genuine: scripts and integrations may complete later. The runtime moves all
     * Bukkit work to the player's owner thread and fires pre/post cast events there.
     */
    @NotNull CompletableFuture<EmakiResult<SkillCastOutcome>> cast(
            @Nullable Player player, @Nullable String skillId);

    /** Casts the skill currently bound to a trigger id. See {@link #cast(Player, String)}. */
    @NotNull CompletableFuture<EmakiResult<SkillCastOutcome>> castByTrigger(
            @Nullable Player player, @Nullable String triggerId);

    /** The remaining operations are synchronous and require the player's owner thread. */
    @NotNull EmakiResult<Unit> learn(@Nullable Player player, @Nullable String skillId);
    @NotNull EmakiResult<Unit> forget(@Nullable Player player, @Nullable String skillId);
    @NotNull EmakiResult<Integer> forgetAll(@Nullable Player player);
    @NotNull EmakiResult<Unit> equip(@Nullable Player player, int slotIndex, @Nullable String skillId);
    @NotNull EmakiResult<Unit> unequip(@Nullable Player player, int slotIndex);
    @NotNull EmakiResult<Unit> bindTrigger(@Nullable Player player, int slotIndex, @Nullable String triggerId);

    /**
     * Runs one upgrade process. A failed success-rate roll is still a successful process and is represented
     * by {@link SkillUpgradeOutcome#successfulRoll()} being {@code false}.
     */
    @NotNull EmakiResult<SkillUpgradeOutcome> upgrade(@Nullable Player player, @Nullable String skillId);

    @NotNull EmakiResult<Integer> setLevel(@Nullable Player player, @Nullable String skillId, int level);
    @NotNull EmakiResult<Integer> addLevel(@Nullable Player player, @Nullable String skillId, int delta);
    @NotNull EmakiResult<Unit> setCastMode(@Nullable Player player, boolean enabled);
}
