package emaki.jiuwu.craft.corelib.command;

import java.util.function.BooleanSupplier;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class EditorCommandSupport {

    public enum Guard {
        DENIED,
        NON_PLAYER,
        UNAVAILABLE,
        ALLOWED
    }

    private EditorCommandSupport() {
    }

    public static Guard check(CommandSender sender, BooleanSupplier servicesReady, String... permissions) {
        boolean permitted = false;
        for (String permission : permissions) {
            if (sender.hasPermission(permission)) {
                permitted = true;
                break;
            }
        }
        if (!permitted) {
            return Guard.DENIED;
        }
        if (!(sender instanceof Player)) {
            return Guard.NON_PLAYER;
        }
        if (!servicesReady.getAsBoolean()) {
            return Guard.UNAVAILABLE;
        }
        return Guard.ALLOWED;
    }
}
