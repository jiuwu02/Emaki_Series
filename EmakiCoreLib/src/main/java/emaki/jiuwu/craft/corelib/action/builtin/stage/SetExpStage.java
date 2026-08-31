package emaki.jiuwu.craft.corelib.action.builtin.stage;

import org.bukkit.entity.Player;

public final class SetExpStage extends ExperienceStage {

    public SetExpStage() {
        super("set_exp", "Sets the target's experience to an absolute value.");
    }

    @Override
    void apply(Player target, int amount, boolean levels) {
        if (levels) {
            target.setLevel(amount);
            return;
        }
        setTotalExperience(target, amount);
    }
}
