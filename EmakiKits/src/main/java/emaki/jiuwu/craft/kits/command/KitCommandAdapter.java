package emaki.jiuwu.craft.kits.command;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public final class KitCommandAdapter implements BasicCommand {

    public static final String ROOT_PERMISSION = "emakikits.use";

    private final KitCommandRouter router;

    public KitCommandAdapter(KitCommandRouter router) {
        this.router = router;
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
        return sender.hasPermission(ROOT_PERMISSION);
    }
}
