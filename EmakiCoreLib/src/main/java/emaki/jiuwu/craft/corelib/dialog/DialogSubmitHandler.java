package emaki.jiuwu.craft.corelib.dialog;

import org.bukkit.entity.Player;

public interface DialogSubmitHandler {

    void onSubmit(Player player, DialogSubmission submission);
}
