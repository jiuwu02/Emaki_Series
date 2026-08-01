package emaki.jiuwu.craft.skills.action.v2;

import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionTarget;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionFailureKind;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionStage;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStagePlanningContext;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreTargetRequirement;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.skills.EmakiSkillsPlugin;

/**
 * Equips, unequips or rebinds one of the target's skill slots.
 *
 * <p>The v2 counterpart of {@code SkillSlotAction}. All three v1 ids ({@code skill_equip},
 * {@code skill_unequip}, {@code skill_bind}) are kept verbatim because they already use the v2 naming
 * style.</p>
 *
 * <p>Slot validation stays entirely inside {@code PlayerSkillStateService}: it owns the unlocked-skill set,
 * the trigger conflict table and the slot-change event, and duplicating any of those checks here would let
 * the two drift apart.</p>
 *
 * <p>Domain {@code CONTEXT_ENTITY}: reads and writes one player's slot bindings, and the service fires a
 * player-scoped event while doing so.</p>
 */
public final class SkillSlotStage implements CoreActionStage {

    /** Which slot mutation a stage instance performs. */
    public enum Operation {

        /** Put a skill into a slot. */
        EQUIP("skill_equip", "Equips a skill into one of the target's skill slots."),

        /** Clear a slot. */
        UNEQUIP("skill_unequip", "Clears one of the target's skill slots."),

        /** Change which trigger fires an already equipped slot. */
        BIND("skill_bind", "Binds a trigger to one of the target's skill slots.");

        private final String id;
        private final String description;

        Operation(String id, String description) {
            this.id = id;
            this.description = description;
        }

        /** {@return the pipeline stage id} */
        public String id() {
            return id;
        }
    }

    private final EmakiSkillsPlugin plugin;
    private final Operation operation;

    /**
     * Creates a stage.
     *
     * @param plugin owning plugin, source of the skill state service
     * @param operation which mutation this instance performs
     */
    public SkillSlotStage(@NotNull EmakiSkillsPlugin plugin, @NotNull Operation operation) {
        this.plugin = plugin;
        this.operation = operation;
    }

    @Override
    public @NotNull String id() {
        return operation.id;
    }

    @Override
    public @NotNull String description() {
        return operation.description;
    }

    @Override
    public @NotNull String category() {
        return "skills";
    }

    @Override
    public @NotNull List<CoreStageParameter> parameters() {
        return switch (operation) {
            case EQUIP -> List.of(
                    CoreStageParameter.required("slot", CoreStageParameterType.INTEGER, "Slot index"),
                    CoreStageParameter.required("skill", CoreStageParameterType.STRING, "Skill id"));
            case BIND -> List.of(
                    CoreStageParameter.required("slot", CoreStageParameterType.INTEGER, "Slot index"),
                    CoreStageParameter.required("trigger", CoreStageParameterType.STRING, "Trigger id"));
            case UNEQUIP -> List.of(
                    CoreStageParameter.required("slot", CoreStageParameterType.INTEGER, "Slot index"));
        };
    }

    @Override
    public @NotNull CoreTargetRequirement targetRequirement() {
        return CoreTargetRequirement.REQUIRED_ENTITY;
    }

    @Override
    public @NotNull CoreActionExecutionTarget executionTarget(@NotNull CoreStagePlanningContext context) {
        return CoreActionExecutionTarget.contextEntity();
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        Player target = SkillsStageSupport.player(context.currentTarget());
        if (target == null) {
            return CoreActionOutcome.skipped("action.v2.stage.common.not_player");
        }
        if (plugin.playerSkillStateService() == null || plugin.playerSkillDataStore() == null) {
            return SkillsStageSupport.serviceUnavailable();
        }
        // -1 matches v1's fallback for an unreadable slot: the state service rejects it, so a bad value
        // fails loudly instead of hitting slot 0.
        int slot = arguments.getInt("slot", -1);
        boolean applied = switch (operation) {
            case EQUIP -> plugin.playerSkillStateService().equipSkill(target, slot,
                    Texts.normalizeId(arguments.getString("skill")));
            case BIND -> plugin.playerSkillStateService().bindTrigger(target, slot,
                    Texts.normalizeId(arguments.getString("trigger")));
            case UNEQUIP -> plugin.playerSkillStateService().unequipSkill(target, slot);
        };
        if (!applied) {
            // REJECTED: the state service returns a bare boolean covering an out-of-range slot, a locked
            // skill, a trigger conflict and a cancelled event alike, so the reason cannot be narrowed here.
            return CoreActionOutcome.failure(CoreActionFailureKind.REJECTED,
                    "action.v2.stage.skills.slot_refused", Map.of("slot", slot));
        }
        plugin.playerSkillDataStore().save(target);
        return CoreActionOutcome.success(Map.of("slot", slot));
    }
}
