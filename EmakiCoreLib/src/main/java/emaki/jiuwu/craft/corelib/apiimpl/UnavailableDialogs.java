package emaki.jiuwu.craft.corelib.apiimpl;

import java.util.Collection;
import java.util.List;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.Unit;
import emaki.jiuwu.craft.corelib.api.dialog.CoreLibDialogs;

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
