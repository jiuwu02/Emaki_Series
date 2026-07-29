package emaki.jiuwu.craft.cooking.apiimpl;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.FailureKind;
import emaki.jiuwu.craft.corelib.api.contract.Unit;
import emaki.jiuwu.craft.cooking.EmakiCookingPlugin;
import emaki.jiuwu.craft.cooking.api.CookingOperations;
import emaki.jiuwu.craft.cooking.service.CookingInspectService;

/**
 * {@link CookingOperations} 的运行时实现。
 *
 * <p>只包装两个 inspect 能力。runtime 的奖励投递是 void + 异步吞异常，无法诚实报告成败，
 * 因此不在此层暴露。
 */
public final class DefaultCookingOperations implements CookingOperations {

    private final EmakiCookingPlugin plugin;

    public DefaultCookingOperations(EmakiCookingPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull EmakiResult<Unit> inspectHeldItem(@Nullable CommandSender recipient, @Nullable Player player) {
        if (player == null) {
            return EmakiResult.invalidInput("cooking.error.no_player");
        }
        CookingInspectService inspectService = plugin.inspectService();
        if (inspectService == null) {
            return EmakiResult.unavailable();
        }
        if (!plugin.threadOwnership().isEntityOwned(player)) {
            return EmakiResult.wrongThread();
        }
        CommandSender target = recipient == null ? player : recipient;
        return inspectService.inspectHand(target, player)
                ? EmakiResult.ok()
                : EmakiResult.failure(FailureKind.REJECTED, "cooking.error.inspect_failed");
    }

    @Override
    public @NotNull EmakiResult<Unit> inspectTargetStation(@Nullable CommandSender recipient,
            @Nullable Player player) {
        if (player == null) {
            return EmakiResult.invalidInput("cooking.error.no_player");
        }
        CookingInspectService inspectService = plugin.inspectService();
        if (inspectService == null) {
            return EmakiResult.unavailable();
        }
        if (!plugin.threadOwnership().isEntityOwned(player)) {
            return EmakiResult.wrongThread();
        }
        CommandSender target = recipient == null ? player : recipient;
        return inspectService.inspectBlock(target, player)
                ? EmakiResult.ok()
                : EmakiResult.failure(FailureKind.REJECTED, "cooking.error.inspect_failed");
    }
}
