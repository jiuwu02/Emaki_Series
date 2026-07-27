package emaki.jiuwu.craft.corelib.dialog;

import java.util.Collection;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.dialog.DialogApi;

/** {@link DialogApi.Bridge} 的运行时实现，把公开门面接到 {@link DialogService}。 */
public final class DialogApiBridge implements DialogApi.Bridge {

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
        return service == null ? java.util.List.of() : service.dialogIds();
    }

    @Override
    public boolean contains(@Nullable String dialogId) {
        return service != null && service.contains(dialogId);
    }

    @Override
    public boolean show(@Nullable Player player, @Nullable String dialogId) {
        return service != null && service.show(player, dialogId);
    }

    @Override
    public boolean close(@Nullable Player player) {
        return service != null && service.close(player);
    }
}
