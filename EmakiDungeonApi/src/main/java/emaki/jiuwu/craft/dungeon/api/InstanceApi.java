package emaki.jiuwu.craft.dungeon.api;

import java.util.UUID;

import org.jetbrains.annotations.NotNull;

public interface InstanceApi {

    @NotNull Result<Void> forceStop(@NotNull UUID instanceId);
}
