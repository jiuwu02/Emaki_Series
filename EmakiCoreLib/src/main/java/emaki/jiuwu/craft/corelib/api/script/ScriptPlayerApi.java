package emaki.jiuwu.craft.corelib.api.script;

import org.bukkit.entity.Player;
import org.graalvm.polyglot.HostAccess;

import emaki.jiuwu.craft.corelib.action.ActionContext;

public final class ScriptPlayerApi {

    private final ActionContext context;

    public ScriptPlayerApi(ActionContext context) {
        this.context = context;
    }

    @HostAccess.Export
    public boolean exists() {
        return player() != null;
    }

    @HostAccess.Export
    public String name() {
        Player player = player();
        return player == null ? "" : player.getName();
    }

    @HostAccess.Export
    public String uuid() {
        Player player = player();
        return player == null ? "" : player.getUniqueId().toString();
    }

    @HostAccess.Export
    public String world() {
        Player player = player();
        return player == null || player.getWorld() == null ? "" : player.getWorld().getName();
    }

    @HostAccess.Export
    public boolean hasPermission(String permission) {
        Player player = player();
        return player != null && player.hasPermission(permission);
    }

    @HostAccess.Export
    public void sendMessage(String message) {
        Player player = player();
        if (player != null && message != null) {
            player.sendMessage(message);
        }
    }

    private Player player() {
        return context == null ? null : context.player();
    }
}
