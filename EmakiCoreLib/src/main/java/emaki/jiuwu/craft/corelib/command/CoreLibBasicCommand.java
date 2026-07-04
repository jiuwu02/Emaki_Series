package emaki.jiuwu.craft.corelib.command;

import java.util.Collection;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Paper {@link BasicCommand} adapter that bridges the modern
 * {@code JavaPlugin#registerCommand(String, BasicCommand)} entry point to the
 * existing {@link CoreLibCommandRouter} logic.
 *
 * <p>Under {@code paper-plugin.yml} the {@code commands:} block is not
 * supported and {@code getCommand(String)} returns {@code null}, so commands
 * must be registered programmatically. This adapter keeps every routing,
 * permission and tab-completion behaviour in {@link CoreLibCommandRouter}
 * unchanged; it only translates between the Paper command surface and the
 * Bukkit-style {@code (CommandSender, args)} calls the router already handles.
 * The router never uses its {@code Command} parameter, and only uses the
 * {@code label} for the help header, so a fixed root label is passed through.</p>
 */
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
        return router.onTabComplete(sender, null, ROOT_LABEL, args);
    }

    @Override
    public @Nullable String permission() {
        // Base command is usable by anyone; sub-commands enforce their own
        // permissions inside the router (web/reload/admin).
        return null;
    }
}
