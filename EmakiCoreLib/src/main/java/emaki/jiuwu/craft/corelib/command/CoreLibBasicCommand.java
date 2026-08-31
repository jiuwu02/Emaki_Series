package emaki.jiuwu.craft.corelib.command;

import java.util.Collection;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class CoreLibBasicCommand implements BasicCommand {

    private static final String ROOT_LABEL = "emakicorelib";

    private final CoreLibCommandRouter router;

    public CoreLibBasicCommand(CoreLibCommandRouter router) {
        this.router = router;
    }

    @Override
    public void execute(@NotNull CommandSourceStack source, @NotNull String[] args) {
        CommandSender sender = source.getSender();
        router.onCommand(sender, null, ROOT_LABEL, args);
    }

    @Override
    public @NotNull Collection<String> suggest(@NotNull CommandSourceStack source, @NotNull String[] args) {
        CommandSender sender = source.getSender();
        String[] completionArgs = args.length == 0 ? new String[] { "" } : args;
        return router.onTabComplete(sender, null, ROOT_LABEL, completionArgs);
    }

    @Override
    public @Nullable String permission() {

        return null;
    }
}
