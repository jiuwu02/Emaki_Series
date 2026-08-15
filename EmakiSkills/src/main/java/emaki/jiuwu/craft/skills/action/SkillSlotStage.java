package emaki.jiuwu.craft.skills.action;

import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionTarget;
import emaki.jiuwu.craft.corelib.api.action.CoreActionFailureKind;
import emaki.jiuwu.craft.corelib.api.action.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.CoreActionStage;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.CoreStagePlanningContext;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRequirement;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.skills.EmakiSkillsPlugin;

public final class SkillSlotStage implements CoreActionStage {

    public enum Operation {

        EQUIP("skill_equip", "Equips a skill into one of the target's skill slots."),

        UNEQUIP("skill_unequip", "Clears one of the target's skill slots."),

        BIND("skill_bind", "Binds a trigger to one of the target's skill slots.");

        private final String id;
        private final String description;

        Operation(String id, String description) {
            this.id = id;
            this.description = description;
        }

        public String id() {
            return id;
        }
    }

    private final EmakiSkillsPlugin plugin;
    private final Operation operation;

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
            return CoreActionOutcome.skipped("action.stage.common.not_player");
        }
        if (plugin.playerSkillStateService() == null || plugin.playerSkillDataStore() == null) {
            return SkillsStageSupport.serviceUnavailable();
        }

        int slot = arguments.getInt("slot", -1);
        boolean applied = switch (operation) {
            case EQUIP -> plugin.playerSkillStateService().equipSkill(target, slot,
                    Texts.normalizeId(arguments.getString("skill")));
            case BIND -> plugin.playerSkillStateService().bindTrigger(target, slot,
                    Texts.normalizeId(arguments.getString("trigger")));
            case UNEQUIP -> plugin.playerSkillStateService().unequipSkill(target, slot);
        };
        if (!applied) {

            return CoreActionOutcome.failure(CoreActionFailureKind.REJECTED,
                    "action.stage.skills.slot_refused", Map.of("slot", slot));
        }
        plugin.playerSkillDataStore().save(target);
        return CoreActionOutcome.success(Map.of("slot", slot));
    }
}
