package emaki.jiuwu.craft.accessory;

import java.util.Collection;
import java.util.List;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import emaki.jiuwu.craft.accessory.command.AccessoryCommandRouter;

/**
 * Bridges Paper's {@link BasicCommand} to the module's own router.
 *
 * <p>Keeps the router free of Paper command types so the dispatch logic stays plain Java, matching how
 * EmakiStation separates the two.
 */
final class AccessoryCommandAdapter implements BasicCommand {

    private final String rootLabel;
    private final String permission;
    private final AccessoryCommandRouter router;

    AccessoryCommandAdapter(String rootLabel, String permission, AccessoryCommandRouter router) {
        this.rootLabel = rootLabel;
        this.permission = permission;
        this.router = router;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        router.onCommand(source.getSender(), rootLabel, args);
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        // Paper hands an empty array for the first segment; without the padding the router would see no
        // argument at all and could not suggest subcommands.
        String[] completionArgs = args.length == 0 ? new String[] { "" } : args;
        List<String> suggestions = router.onTabComplete(source.getSender(), completionArgs);
        return suggestions == null ? List.of() : suggestions;
    }

    @Override
    public String permission() {
        return permission;
    }
}
