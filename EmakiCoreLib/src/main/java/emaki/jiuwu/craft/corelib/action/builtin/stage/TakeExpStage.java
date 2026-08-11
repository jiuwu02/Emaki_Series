package emaki.jiuwu.craft.corelib.action.builtin.stage;

import org.bukkit.entity.Player;

/** Removes experience from the target. See {@link ExperienceStage} for the shared contract. */
public final class TakeExpStage extends ExperienceStage {

    public TakeExpStage() {
        super("take_exp", "Removes experience from the target.");
    }

    @Override
    void apply(Player target, int amount, boolean levels) {
        if (levels) {
            target.setLevel(Math.max(0, target.getLevel() - amount));
            return;
        }
        setTotalExperience(target, Math.max(0, target.getTotalExperience() - amount));
    }
}
