package emaki.jiuwu.craft.corelib.api.script;

import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.graalvm.polyglot.HostAccess;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class ScriptPlayerApi {

    private final boolean exists;
    private final String name;
    private final String uuid;
    private final String world;
    private final double x;
    private final double y;
    private final double z;
    private final Set<String> permissions;

    public ScriptPlayerApi(ActionContext context) {
        Player player = context == null ? null : context.player();
        this.exists = player != null;
        this.name = player == null ? "" : Texts.toStringSafe(player.getName());
        UUID uniqueId = player == null ? null : player.getUniqueId();
        this.uuid = uniqueId == null ? "" : uniqueId.toString();
        Location currentLocation = player == null ? null : player.getLocation();
        Location location = currentLocation == null ? null : currentLocation.clone();
        this.world = location == null || location.getWorld() == null ? "" : location.getWorld().getName();
        this.x = location == null ? 0D : location.getX();
        this.y = location == null ? 0D : location.getY();
        this.z = location == null ? 0D : location.getZ();
        Set<String> capturedPermissions = new TreeSet<>();
        if (player != null) {
            for (PermissionAttachmentInfo permission : player.getEffectivePermissions()) {
                if (permission != null && permission.getValue() && permission.getPermission() != null) {
                    capturedPermissions.add(permission.getPermission());
                }
            }
        }
        this.permissions = Set.copyOf(capturedPermissions);
    }

    @HostAccess.Export
    public boolean exists() {
        return exists;
    }

    @HostAccess.Export
    public String name() {
        return name;
    }

    @HostAccess.Export
    public String uuid() {
        return uuid;
    }

    @HostAccess.Export
    public String world() {
        return world;
    }

    @HostAccess.Export
    public double x() {
        return x;
    }

    @HostAccess.Export
    public double y() {
        return y;
    }

    @HostAccess.Export
    public double z() {
        return z;
    }

    @HostAccess.Export
    public boolean hasPermission(String permission) {
        return permission != null && permissions.contains(permission);
    }
}
