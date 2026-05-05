package emaki.jiuwu.craft.corelib.script.api;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.action.ActionContext;

public final class ScriptPlayerApi {

    private final ActionContext context;

    public ScriptPlayerApi(ActionContext context) {
        this.context = context;
    }

    public boolean exists() {
        return player() != null;
    }

    public String name() {
        Player player = player();
        return player == null ? "" : player.getName();
    }

    public String uuid() {
        Player player = player();
        return player == null ? "" : player.getUniqueId().toString();
    }

    public String world() {
        Player player = player();
        return player == null || player.getWorld() == null ? "" : player.getWorld().getName();
    }

    public boolean hasPermission(String permission) {
        Player player = player();
        return player != null && player.hasPermission(permission);
    }

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
