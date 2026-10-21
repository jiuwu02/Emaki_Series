package emaki.jiuwu.craft.dummy.command;

import java.util.Collection;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;

public final class DummyCommandAdapter implements BasicCommand {

    private final DummyCommandRouter router;
    private final String permission;

    public DummyCommandAdapter(DummyCommandRouter router, String permission) {
        this.router = router;
        this.permission = permission;
    }

    @Override
    public void execute(@NotNull CommandSourceStack source, @NotNull String[] args) {
        router.route(source.getSender(), args);
    }

    @Override
    public @NotNull Collection<String> suggest(@NotNull CommandSourceStack source, @NotNull String[] args) {
        String[] completionArgs = args.length == 0 ? new String[] { "" } : args;
        return router.suggest(source.getSender(), completionArgs);
    }

    @Override
    public boolean canUse(@NotNull CommandSender sender) {
        return sender.hasPermission(permission);
    }
}
