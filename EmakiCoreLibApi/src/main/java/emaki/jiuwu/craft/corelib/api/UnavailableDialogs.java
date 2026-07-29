package emaki.jiuwu.craft.corelib.api;

import java.util.Collection;
import java.util.List;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.Unit;
import emaki.jiuwu.craft.corelib.api.dialog.CoreLibDialogs;

/**
 * Dialog layer returned when EmakiCoreLib is not installed. Every operation reports
 * {@link emaki.jiuwu.craft.corelib.api.contract.FailureKind#UNAVAILABLE}.
 */
final class UnavailableDialogs implements CoreLibDialogs {

    static final UnavailableDialogs INSTANCE = new UnavailableDialogs();

    private UnavailableDialogs() {
    }

    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public Collection<String> dialogIds() {
        return List.of();
    }

    @Override
    public boolean contains(String dialogId) {
        return false;
    }

    @Override
    public EmakiResult<Unit> show(Player player, String dialogId) {
        return EmakiResult.unavailable();
    }

    @Override
    public EmakiResult<Unit> close(Player player) {
        return EmakiResult.unavailable();
    }
}
