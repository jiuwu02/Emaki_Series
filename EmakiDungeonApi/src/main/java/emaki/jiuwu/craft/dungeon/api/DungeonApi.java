package emaki.jiuwu.craft.dungeon.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public interface DungeonApi {

    @NotNull Result<Object> enter(@NotNull Player player, @NotNull String dungeonId);

    @NotNull List<InstanceView> listInstances();

    @NotNull Optional<InstanceView> getInstance(@NotNull UUID instanceId);
}
