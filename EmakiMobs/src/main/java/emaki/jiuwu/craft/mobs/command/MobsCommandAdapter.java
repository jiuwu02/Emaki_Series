package emaki.jiuwu.craft.mobs.command;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public final class MobsCommandAdapter implements BasicCommand {

    private final MobsCommandRouter router;
    private final String permission;

    public MobsCommandAdapter(MobsCommandRouter router, String permission) {
        this.router = router;
        this.permission = permission;
    }

    @Override
    public void execute(@NotNull CommandSourceStack source, @NotNull String[] args) {
        router.route(source.getSender(), args);
    }

    @Override
    public boolean canUse(@NotNull CommandSender sender) {
        return sender.hasPermission(permission);
    }
}
