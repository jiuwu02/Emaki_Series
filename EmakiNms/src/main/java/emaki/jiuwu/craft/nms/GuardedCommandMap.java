package emaki.jiuwu.craft.nms;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.craftbukkit.command.CraftCommandMap;

public final class GuardedCommandMap extends CraftCommandMap {

    private final SimpleCommandMap delegate;
    private final BiPredicate<CommandSender, String> interceptor;

    public GuardedCommandMap(Server server, SimpleCommandMap delegate, BiPredicate<CommandSender, String> interceptor) {
        super(server);
        this.delegate = delegate;
        this.interceptor = interceptor;
    }

    @Override
    public void setFallbackCommands() {
        delegate.setFallbackCommands();
    }

    @Override
    public void registerAll(String fallbackPrefix, List<Command> commands) {
        delegate.registerAll(fallbackPrefix, commands);
    }

    @Override
    public boolean register(String label, Command command) {
        return delegate.register(label, command);
    }

    @Override
    public boolean register(String fallbackPrefix, String label, Command command) {
        return delegate.register(fallbackPrefix, label, command);
    }

    @Override
    public boolean dispatch(CommandSender sender, String commandLine) {
        if (interceptor.test(sender, commandLine)) {
            return true;
        }
        return delegate.dispatch(sender, commandLine);
    }

    @Override
    public synchronized void clearCommands() {
        delegate.clearCommands();
    }

    @Override
    public Command getCommand(String name) {
        return delegate.getCommand(name);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String cmdLine) {
        return delegate.tabComplete(sender, cmdLine);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String cmdLine, Location location) {
        return delegate.tabComplete(sender, cmdLine, location);
    }

    @Override
    public Collection<Command> getCommands() {
        return delegate.getCommands();
    }

    @Override
    public void registerServerAliases() {
        delegate.registerServerAliases();
    }

    @Override
    public Map<String, Command> getKnownCommands() {
        return delegate.getKnownCommands();
    }
}
