package emaki.jiuwu.craft.corelib.dialog;

import java.util.Collection;
import java.util.List;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.FailureKind;
import emaki.jiuwu.craft.corelib.api.contract.Unit;
import emaki.jiuwu.craft.corelib.api.dialog.CoreLibDialogs;

public final class DialogApiBridge implements CoreLibDialogs {

    private final DialogService service;

    public DialogApiBridge(DialogService service) {
        this.service = service;
    }

    @Override
    public boolean enabled() {
        return service != null && service.enabled();
    }

    @Override
    public @NotNull Collection<String> dialogIds() {
        return service == null ? List.of() : service.dialogIds();
    }

    @Override
    public boolean contains(@Nullable String dialogId) {
        return service != null && service.contains(dialogId);
    }

    @Override
    public @NotNull EmakiResult<Unit> show(@Nullable Player player, @Nullable String dialogId) {
        if (service == null || !service.enabled()) {
            return EmakiResult.unavailable();
        }
        if (player == null) {
            return EmakiResult.invalidInput("corelib.dialog.player_missing");
        }
        if (dialogId == null || dialogId.isBlank()) {
            return EmakiResult.invalidInput("corelib.dialog.id_missing");
        }
        if (!service.contains(dialogId)) {
            return EmakiResult.notFound("corelib.dialog.not_found");
        }
        return service.show(player, dialogId)
                ? EmakiResult.ok()
                : EmakiResult.failure(FailureKind.REJECTED, "corelib.dialog.show_rejected");
    }

    @Override
    public @NotNull EmakiResult<Unit> close(@Nullable Player player) {
        if (service == null || !service.enabled()) {
            return EmakiResult.unavailable();
        }
        if (player == null) {
            return EmakiResult.invalidInput("corelib.dialog.player_missing");
        }
        return service.close(player)
                ? EmakiResult.ok()
                : EmakiResult.failure(FailureKind.REJECTED, "corelib.dialog.close_rejected");
    }
}
