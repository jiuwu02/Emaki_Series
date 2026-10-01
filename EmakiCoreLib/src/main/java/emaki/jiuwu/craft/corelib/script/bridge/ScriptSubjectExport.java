package emaki.jiuwu.craft.corelib.script.bridge;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.action.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class ScriptSubjectExport {

    private final CoreActionSubject subject;

    public ScriptSubjectExport(@NotNull CoreActionSubject subject) {
        this.subject = subject;
    }

    @NotNull
    public String getType() {
        if (subject instanceof CoreActionSubject.OfEntity) {
            return "entity";
        }
        if (subject instanceof CoreActionSubject.OfLocation) {
            return "location";
        }
        return "absent";
    }

    public boolean isValid() {
        return subject.valid();
    }

    @Nullable
    public String getName() {
        Entity entity = subject.entityOrNull();
        return entity == null ? null : entity.getName();
    }

    @Nullable
    public String getUniqueId() {
        Entity entity = subject.entityOrNull();
        return entity == null ? null : entity.getUniqueId().toString();
    }

    @Nullable
    public String getEntityType() {
        Entity entity = subject.entityOrNull();
        return entity == null ? null : entity.getType().name();
    }

    public boolean isPlayer() {
        return subject.entityOrNull() instanceof Player;
    }

    @Nullable
    public String getGameMode() {
        Entity entity = subject.entityOrNull();
        return entity instanceof Player player ? player.getGameMode().name() : null;
    }

    @Nullable
    public ScriptLocationExport getLocation() {
        Location location = subject.location();
        return location == null ? null : new ScriptLocationExport(location);
    }

    @Nullable
    public String getWorldName() {
        Location location = subject.location();
        if (location == null || location.getWorld() == null) {
            return null;
        }
        return location.getWorld().getName();
    }

    @Nullable
    public Double getX() {
        Location location = subject.location();
        return location == null ? null : location.getX();
    }

    @Nullable
    public Double getY() {
        Location location = subject.location();
        return location == null ? null : location.getY();
    }

    @Nullable
    public Double getZ() {
        Location location = subject.location();
        return location == null ? null : location.getZ();
    }

    @Override
    public String toString() {
        return "subject[" + getType() + " " + Texts.toStringSafe(getName()) + "]";
    }
}
