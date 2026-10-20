package emaki.jiuwu.craft.dungeon.api;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public interface EditorApi {

    @NotNull Result<EditorSession> openEditor(@NotNull Player player, @NotNull String dungeonId);
}
