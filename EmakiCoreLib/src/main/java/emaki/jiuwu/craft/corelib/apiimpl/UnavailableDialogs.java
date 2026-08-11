package emaki.jiuwu.craft.corelib.apiimpl;

import java.util.Collection;
import java.util.List;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.Unit;
import emaki.jiuwu.craft.corelib.api.dialog.CoreLibDialogs;

/**
 * 对话框子系统未启用时返回的降级实现。
 *
 * <p>CoreLib 本体可用但对话框功能被关闭或加载失败时使用；调用方仍拿到统一的
 * {@link EmakiResult}，无需判空。
 */
final class UnavailableDialogs implements CoreLibDialogs {

    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public @NotNull Collection<String> dialogIds() {
        return List.of();
    }

    @Override
    public boolean contains(@Nullable String dialogId) {
        return false;
    }

    @Override
    public @NotNull EmakiResult<Unit> show(@Nullable Player player, @Nullable String dialogId) {
        return EmakiResult.unavailable();
    }

    @Override
    public @NotNull EmakiResult<Unit> close(@Nullable Player player) {
        return EmakiResult.unavailable();
    }
}
