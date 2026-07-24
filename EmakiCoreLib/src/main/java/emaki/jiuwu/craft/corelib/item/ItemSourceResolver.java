package emaki.jiuwu.craft.corelib.item;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.corelib.text.Texts;

public interface ItemSourceResolver {

    String id();

    int priority();

    boolean supports(ItemSource source);

    default boolean isAvailable(ItemSource source) {
        return supports(source);
    }

    default ItemSourceProbe probe(ItemSource source) {
        if (source == null || source.getType() == null || !supports(source)) {
            return ItemSourceProbe.of(
                    ItemSourceProbeStatus.INVALID_SOURCE,
                    source,
                    id(),
                    "The item source is invalid or unsupported by this resolver."
            );
        }
        try {
            if (!isAvailable(source)) {
                return ItemSourceProbe.of(
                        ItemSourceProbeStatus.SOURCE_NOT_FOUND,
                        source,
                        id(),
                        "The provider does not contain the requested item source."
                );
            }
            return ItemSourceProbe.ready(source, id());
        } catch (LinkageError exception) {
            return ItemSourceProbe.of(ItemSourceProbeStatus.INCOMPATIBLE, source, id(), detail(exception));
        } catch (RuntimeException exception) {
            return ItemSourceProbe.of(ItemSourceProbeStatus.RESOLUTION_ERROR, source, id(), detail(exception));
        }
    }

    ItemSource identify(ItemStack itemStack);

    ItemStack create(ItemSource source, int amount);

    default String displayName(ItemSource source) {
        if (!supports(source)) {
            return null;
        }
        ItemStack itemStack = create(source, 1);
        if (itemStack == null || itemStack.getType().isAir()) {
            return null;
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (!ItemTextBridge.hasCustomName(itemMeta)) {
            return null;
        }
        String displayName = MiniMessages.serialize(ItemTextBridge.customName(itemMeta));
        return Texts.isBlank(displayName) ? null : displayName;
    }

    private static String detail(Throwable throwable) {
        if (throwable == null) {
            return "Unknown resolution failure";
        }
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }
}
