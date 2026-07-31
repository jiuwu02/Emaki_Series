package emaki.jiuwu.craft.corelib.api.action.v2;

import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

/**
 * Context keys owned by EmakiCoreLib.
 *
 * <p>Business modules declare their own keys in their own API module. CoreLib checks at registration
 * time that no two modules declare the same name with different types.</p>
 */
public final class CoreActionKeys {

    /** The item a pipeline operates on, replacing the seven-key guessing in EmakiItem. */
    public static final CoreActionKey<ItemStack> ITEM = CoreActionKey.of("item", ItemStack.class);

    /** A generic numeric payload supplied by the trigger. */
    public static final CoreActionKey<Double> VALUE = CoreActionKey.of("value", Double.class);

    /** The block a pipeline operates on. */
    public static final CoreActionKey<Block> BLOCK = CoreActionKey.of("block", Block.class);

    /** The trigger id that started this pipeline. */
    public static final CoreActionKey<String> TRIGGER = CoreActionKey.of("trigger", String.class);

    /** Cooperative cancellation signal for the current pipeline invocation. */
    public static final CoreActionKey<CoreCancellationToken> CANCELLATION =
            CoreActionKey.of("cancellation", CoreCancellationToken.class);

    private CoreActionKeys() {
    }
}
