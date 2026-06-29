package emaki.jiuwu.craft.corelib.api.script;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.graalvm.polyglot.HostAccess;

import emaki.jiuwu.craft.corelib.text.AdventureSupport;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class ScriptTextApi {

    private final org.bukkit.plugin.Plugin sourcePlugin;

    public ScriptTextApi() {
        this(null);
    }

    public ScriptTextApi(org.bukkit.plugin.Plugin sourcePlugin) {
        this.sourcePlugin = sourcePlugin;
    }

    @HostAccess.Export
    public String string(Object value) {
        return Texts.toStringSafe(value);
    }

    @HostAccess.Export
    public boolean blank(String value) {
        return Texts.isBlank(value);
    }

    @HostAccess.Export
    public boolean notBlank(String value) {
        return Texts.isNotBlank(value);
    }

    @HostAccess.Export
    public String lower(String value) {
        return Texts.lower(value);
    }

    @HostAccess.Export
    public String normalizeId(String value) {
        return Texts.normalizeId(value);
    }

    @HostAccess.Export
    public Object component(String miniMessage) {
        return MiniMessages.parse(miniMessage);
    }

    @HostAccess.Export
    public String plain(String miniMessage) {
        return MiniMessages.plainText(miniMessage);
    }

    @HostAccess.Export
    public String legacy(String miniMessage) {
        return MiniMessages.legacyText(miniMessage);
    }

    @HostAccess.Export
    public void sendMini(Object target, String miniMessage) {
        CommandSender sender = commandSender(target);
        if (sourcePlugin != null && sender != null && miniMessage != null) {
            AdventureSupport.sendMiniMessage(sourcePlugin, sender, miniMessage);
        }
    }

    @HostAccess.Export
    public void broadcastMini(String miniMessage) {
        if (sourcePlugin != null && miniMessage != null) {
            AdventureSupport.broadcast(sourcePlugin, MiniMessages.parse(miniMessage));
        }
    }

    @HostAccess.Export
    public void actionBar(Object target, String miniMessage) {
        org.bukkit.entity.Player player = player(target);
        if (sourcePlugin != null && player != null && miniMessage != null) {
            AdventureSupport.sendActionBar(sourcePlugin, player, miniMessage);
        }
    }

    private CommandSender commandSender(Object target) {
        Object raw = unwrap(target);
        if (raw instanceof CommandSender sender) {
            return sender;
        }
        if (raw instanceof String name) {
            return Bukkit.getPlayerExact(name);
        }
        return null;
    }

    private org.bukkit.entity.Player player(Object target) {
        Object raw = unwrap(target);
        if (raw instanceof org.bukkit.entity.Player player) {
            return player;
        }
        if (raw instanceof String name) {
            return Bukkit.getPlayerExact(name);
        }
        return null;
    }

    private Object unwrap(Object target) {
        if (target instanceof ScriptServerApi.ScriptEntityApi entityApi) {
            return entityApi.entity();
        }
        return target;
    }
}
