package emaki.jiuwu.craft.corelib.item;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceKind;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceProbeResult;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceProbeState;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceProvider;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.text.Texts;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;

/**
 * MMOItems bridge.
 *
 * <p>Implements {@link ItemSourceProvider} directly rather than extending
 * {@link AbstractManagedItemSourceProvider}: MMOItems has no "items loaded" event to hook, its readiness
 * is a plain {@code MMOItems.plugin != null} check, and an identifier here is a composite
 * {@code <type>:<item>} that the shared accessor shape cannot express.
 */
final class MmoItemsItemSourceResolver implements ItemSourceProvider {

    private static final String PLUGIN_NAME = "MMOItems";

    @Override
    public ItemSourceKind kind() {
        return ItemSourceKind.MMOITEMS;
    }

    @Override
    public Set<String> shorthandPrefixes() {
        return Set.of("mmoitems-", "mi-");
    }

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public String providerPluginName() {
        return PLUGIN_NAME;
    }

    @Override
    public boolean supports(ItemSourceRef ref) {
        return ref != null && ItemSourceKind.MMOITEMS.equals(ref.kind());
    }

    @Override
    public ItemSourceProbeResult probe(ItemSourceRef ref) {
        String providerId = kind().key();
        if (!supports(ref)) {
            return ItemSourceProbeResult.of(ItemSourceProbeState.INVALID_SOURCE, ref, providerId,
                    "The item source is invalid or unsupported by the MMOItems provider.");
        }
        MmoItemsKey key = MmoItemsKey.parse(ref.identifier());
        if (key == null) {
            return ItemSourceProbeResult.of(ItemSourceProbeState.INVALID_SOURCE, ref, providerId,
                    "MMOItems sources require a '<type>:<item>' identifier.");
        }
        try {
            if (!mmoItemsReady()) {
                return ItemSourceProbeResult.of(ItemSourceProbeState.PROVIDER_NOT_READY, ref, providerId,
                        "MMOItems is not enabled or its API instance is not ready.");
            }
            Type type = resolveType(key.typeId());
            if (type == null) {
                return ItemSourceProbeResult.of(ItemSourceProbeState.SOURCE_NOT_FOUND, ref, providerId,
                        "MMOItems does not contain the requested item type.");
            }
            ItemStack itemStack = createItem(type, key.itemId(), 1);
            return itemStack == null || itemStack.getType().isAir()
                    ? ItemSourceProbeResult.of(ItemSourceProbeState.SOURCE_NOT_FOUND, ref, providerId,
                            "MMOItems does not contain the requested item.")
                    : ItemSourceProbeResult.ready(ref, providerId);
        } catch (LinkageError exception) {
            return ItemSourceProbeResult.of(ItemSourceProbeState.INCOMPATIBLE, ref, providerId, detail(exception));
        } catch (RuntimeException exception) {
            return ItemSourceProbeResult.of(ItemSourceProbeState.RESOLUTION_ERROR, ref, providerId, detail(exception));
        }
    }

    @Override
    public ItemSourceRef identify(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir() || !mmoItemsReady()) {
            return null;
        }
        try {
            Type type = MMOItems.getType(itemStack);
            String itemId = Texts.toStringSafe(MMOItems.getID(itemStack)).trim();
            if (type == null || Texts.isBlank(type.getId()) || Texts.isBlank(itemId)) {
                return null;
            }
            return ItemSourceRef.orNull(ItemSourceKind.MMOITEMS, type.getId() + ":" + itemId);
        } catch (RuntimeException | LinkageError exception) {
            return null;
        }
    }

    @Override
    public ItemStack create(ItemSourceRef ref, int amount) {
        if (!supports(ref) || !mmoItemsReady()) {
            return null;
        }
        MmoItemsKey key = MmoItemsKey.parse(ref.identifier());
        if (key == null) {
            return null;
        }
        try {
            Type type = resolveType(key.typeId());
            if (type == null) {
                return null;
            }
            ItemStack itemStack = createItem(type, key.itemId(), amount);
            if (itemStack == null) {
                return null;
            }
            ItemStack cloned = itemStack.clone();
            cloned.setAmount(Math.max(1, amount));
            return cloned;
        } catch (RuntimeException | LinkageError exception) {
            return null;
        }
    }

    @Override
    public String displayName(ItemSourceRef ref) {
        if (!supports(ref) || !mmoItemsReady()) {
            return null;
        }
        MmoItemsKey key = MmoItemsKey.parse(ref.identifier());
        if (key == null) {
            return null;
        }
        try {
            Type type = resolveType(key.typeId());
            if (type == null) {
                return null;
            }
            ItemStack itemStack = createItem(type, key.itemId(), 1);
            return itemStack == null || itemStack.getType().isAir() ? null : ItemTextBridge.effectiveNameText(itemStack);
        } catch (RuntimeException | LinkageError exception) {
            return null;
        }
    }

    private ItemStack createItem(Type type, String itemId, int amount) {
        ItemStack itemStack = MMOItems.plugin.getItem(type, itemId);
        if (itemStack == null) {
            String resolvedItemId = resolveTemplateId(type, itemId);
            itemStack = Texts.isBlank(resolvedItemId) ? null : MMOItems.plugin.getItem(type, resolvedItemId);
        }
        if (itemStack != null) {
            itemStack.setAmount(Math.max(1, amount));
        }
        return itemStack;
    }

    private boolean mmoItemsReady() {
        return Bukkit.getPluginManager().isPluginEnabled(PLUGIN_NAME) && MMOItems.plugin != null;
    }

    private Type resolveType(String typeId) {
        if (Texts.isBlank(typeId) || MMOItems.plugin == null) {
            return null;
        }
        Type type = MMOItems.plugin.getTypes().get(typeId);
        if (type != null) {
            return type;
        }
        String uppercaseTypeId = typeId.toUpperCase(Locale.ROOT);
        if (!uppercaseTypeId.equals(typeId)) {
            type = MMOItems.plugin.getTypes().get(uppercaseTypeId);
            if (type != null) {
                return type;
            }
        }
        for (Type candidate : MMOItems.plugin.getTypes().getAll()) {
            if (candidate != null && candidate.getId().equalsIgnoreCase(typeId)) {
                return candidate;
            }
        }
        return null;
    }

    private String resolveTemplateId(Type type, String itemId) {
        if (type == null || Texts.isBlank(itemId) || MMOItems.plugin == null) {
            return "";
        }
        List<String> templateNames = MMOItems.plugin.getTemplates().getTemplateNames(type);
        if (templateNames == null || templateNames.isEmpty()) {
            return "";
        }
        for (String name : templateNames) {
            if (itemId.equals(name)) {
                return name;
            }
        }
        for (String name : templateNames) {
            if (name != null && name.equalsIgnoreCase(itemId)) {
                return name;
            }
        }
        return "";
    }

    private String detail(Throwable throwable) {
        if (throwable == null) {
            return "Unknown MMOItems resolution failure";
        }
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }

    private record MmoItemsKey(String typeId, String itemId) {

        private static MmoItemsKey parse(String raw) {
            if (Texts.isBlank(raw)) {
                return null;
            }
            String text = Texts.trim(raw);
            int separator = text.indexOf(':');
            if (separator <= 0 || separator >= text.length() - 1) {
                return null;
            }
            String typeId = text.substring(0, separator).trim();
            String itemId = text.substring(separator + 1).trim();
            if (typeId.isEmpty() || itemId.isEmpty()) {
                return null;
            }
            return new MmoItemsKey(typeId, itemId);
        }
    }
}
