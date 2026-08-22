package emaki.jiuwu.craft.corelib.command;

import java.util.Collection;
import java.util.List;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PaperCommandAdapter implements BasicCommand {

    private final String rootLabel;
    private final String permission;
    private final CommandExecutor executor;
    private final TabCompleter tabCompleter;

    public PaperCommandAdapter(String rootLabel,
            String permission,
            CommandExecutor executor,
            TabCompleter tabCompleter) {
        this.rootLabel = rootLabel;
        this.permission = permission;
        this.executor = executor;
        this.tabCompleter = tabCompleter;
    }

    @Override
    public void execute(@NotNull CommandSourceStack source, @NotNull String[] args) {
        executor.onCommand(source.getSender(), null, rootLabel, args);
    }

    @Override
    public @NotNull Collection<String> suggest(@NotNull CommandSourceStack source, @NotNull String[] args) {
        String[] completionArgs = args.length == 0 ? new String[] { "" } : args;
        List<String> suggestions = tabCompleter.onTabComplete(source.getSender(), null, rootLabel, completionArgs);
        return suggestions == null ? List.of() : suggestions;
    }

    @Override
    public @Nullable String permission() {
        return permission;
    }
}
