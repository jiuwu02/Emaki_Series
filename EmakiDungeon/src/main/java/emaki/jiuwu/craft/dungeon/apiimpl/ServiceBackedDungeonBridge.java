package emaki.jiuwu.craft.dungeon.apiimpl;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.api.contract.ApiStatus;
import emaki.jiuwu.craft.dungeon.api.EmakiDungeonApi;

public final class ServiceBackedDungeonBridge implements EmakiDungeonApi.Bridge {

    private final ApiStatus status;

    public ServiceBackedDungeonBridge(ApiStatus status) {
        this.status = status;
    }

    @Override
    public @NotNull ApiStatus status() {
        return status;
    }
}
