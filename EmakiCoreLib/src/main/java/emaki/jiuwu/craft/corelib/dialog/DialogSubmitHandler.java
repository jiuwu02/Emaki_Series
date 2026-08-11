package emaki.jiuwu.craft.corelib.dialog;

import org.bukkit.entity.Player;

/**
 * 程序化对话框的提交回调。
 *
 * <p>{@link #onSubmit} 保证最多被调用一次，且在目标玩家的所有者线程执行，
 * 因此回调内可以直接读写 Bukkit 状态。
 */
public interface DialogSubmitHandler {

    /**
     * 玩家点击提交按钮时调用。
     *
     * @param player     提交者
     * @param submission 输入取值视图
     */
    void onSubmit(Player player, DialogSubmission submission);
}
