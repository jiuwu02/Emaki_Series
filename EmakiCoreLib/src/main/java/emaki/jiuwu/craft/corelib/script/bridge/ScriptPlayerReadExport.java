package emaki.jiuwu.craft.corelib.script.bridge;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ScriptPlayerReadExport {

    private final Player player;

    public ScriptPlayerReadExport(@NotNull Player player) {
        this.player = player;
    }

    @NotNull
    public String getName() {
        return player.getName();
    }

    @NotNull
    public String getUniqueId() {
        return player.getUniqueId().toString();
    }

    @NotNull
    public String getGameMode() {
        return player.getGameMode().name();
    }

    public boolean isOnline() {
        return player.isOnline();
    }

    public boolean isSneaking() {
        return player.isSneaking();
    }

    public double getHealth() {
        return player.getHealth();
    }

    public int getFoodLevel() {
        return player.getFoodLevel();
    }

    public int getLevel() {
        return player.getLevel();
    }

    @NotNull
    public String getWorldName() {
        return player.getWorld().getName();
    }

    @NotNull
    public ScriptLocationExport getLocation() {
        Location location = player.getLocation();
        return new ScriptLocationExport(location);
    }

    @Override
    public String toString() {
        return "player[" + player.getName() + "]";
    }
}
