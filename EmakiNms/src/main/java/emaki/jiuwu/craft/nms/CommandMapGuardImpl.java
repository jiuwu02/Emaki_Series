package emaki.jiuwu.craft.nms;

import java.util.function.BiPredicate;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.SimpleCommandMap;

import emaki.jiuwu.craft.corelib.api.command.CommandMapGuard;

public final class CommandMapGuardImpl implements CommandMapGuard {

    private static final String COMMAND_MAP_FIELD = "commandMap";

    @Override
    public Handle install(BiPredicate<CommandSender, String> interceptor) {
        Object server = Bukkit.getServer();
        Object current = NmsAccess.readField(server, COMMAND_MAP_FIELD);
        if (!(current instanceof SimpleCommandMap commandMap)) {
            throw new IllegalStateException("平台命令表类型不受支持: " + current.getClass().getName());
        }
        GuardedCommandMap guarded = new GuardedCommandMap(Bukkit.getServer(), commandMap, interceptor);
        NmsAccess.writeField(server, COMMAND_MAP_FIELD, guarded);
        return () -> NmsAccess.writeField(server, COMMAND_MAP_FIELD, current);
    }
}
