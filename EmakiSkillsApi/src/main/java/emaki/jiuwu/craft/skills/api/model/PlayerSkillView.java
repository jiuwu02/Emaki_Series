package emaki.jiuwu.craft.skills.api.model;

import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Read-only snapshot of a player's skill state.
 *
 * @param slots     the player's skill slots, ordered by index; empty when EmakiSkills is unavailable
 * @param castMode  whether the player currently has cast mode enabled
 */
public record PlayerSkillView(@NotNull List<SkillSlotView> slots, boolean castMode) {

    private static final PlayerSkillView EMPTY = new PlayerSkillView(List.of(), false);

    /**
     * Normalises the slot list so the accessor cannot return {@code null}.
     *
     * @param slots    skill slots
     * @param castMode whether cast mode is enabled
     */
    public PlayerSkillView {
        slots = slots == null ? List.of() : List.copyOf(slots);
    }

    /** {@return an empty state used when EmakiSkills is unavailable or the player has no data} */
    public static @NotNull PlayerSkillView empty() {
        return EMPTY;
    }

    /**
     * One skill slot.
     *
     * @param slotIndex the zero-based slot index
     * @param skillId   the id of the equipped skill, or {@code null} when the slot is empty
     * @param triggerId the trigger bound to this slot, or {@code null} when none is bound
     */
    public record SkillSlotView(int slotIndex,
                                @Nullable String skillId,
                                @Nullable String triggerId) {

        /**
         * Normalises blank strings to {@code null} so callers can test presence with a simple null check.
         *
         * @param slotIndex the slot index
         * @param skillId   the equipped skill id
         * @param triggerId the bound trigger id
         */
        public SkillSlotView {
            skillId = skillId == null || skillId.isBlank() ? null : skillId;
            triggerId = triggerId == null || triggerId.isBlank() ? null : triggerId;
        }

        /** {@return whether a skill is equipped in this slot} */
        public boolean occupied() {
            return skillId != null;
        }

        /** {@return the equipped skill id when present} */
        public @NotNull Optional<String> skill() {
            return Optional.ofNullable(skillId);
        }

        /** {@return the bound trigger id when one is configured} */
        public @NotNull Optional<String> trigger() {
            return Optional.ofNullable(triggerId);
        }
    }
}
