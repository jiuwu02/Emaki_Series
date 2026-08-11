package emaki.jiuwu.craft.corelib.action.builtin.stage;

import org.bukkit.entity.Player;

/** Grants experience to the target. See {@link ExperienceStage} for the shared contract. */
public final class GiveExpStage extends ExperienceStage {

    public GiveExpStage() {
        super("give_exp", "Grants experience to the target.");
    }

    @Override
    void apply(Player target, int amount, boolean levels) {
        if (levels) {
            target.giveExpLevels(amount);
        } else {
            target.giveExp(amount);
        }
    }
}
