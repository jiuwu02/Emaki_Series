package emaki.jiuwu.craft.dungeon.api;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Facade for opening the dungeon editor.
 */
public interface EditorApi {

    /**
     * Opens the dungeon editor for {@code player} on behalf of
     * {@code dungeonId}.
     *
     * @param player the player who will operate the editor; must be online
     * @param dungeonId the dungeon to edit
     * {@return the outcome; on success the value is the open
     * {@link EditorSession}}
     */
    @NotNull Result<EditorSession> openEditor(@NotNull Player player, @NotNull String dungeonId);
}
