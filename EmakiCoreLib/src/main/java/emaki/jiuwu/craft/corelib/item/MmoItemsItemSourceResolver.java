package emaki.jiuwu.craft.corelib.item;

import java.util.List;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.text.Texts;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;

final class MmoItemsItemSourceResolver implements ItemSourceResolver {

    private static final String PLUGIN_NAME = "MMOItems";

    @Override
    public String id() {
        return "corelib_mmoitems";
    }

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public boolean supports(ItemSource source) {
        return source != null && source.getType() == ItemSourceType.MMOITEMS;
    }

    @Override
    public boolean isAvailable(ItemSource source) {
        return probe(source).ready();
    }

    @Override
    public ItemSourceProbe probe(ItemSource source) {
        if (source == null || source.getType() == null || !supports(source)) {
            return ItemSourceProbe.of(
                    ItemSourceProbeStatus.INVALID_SOURCE,
                    source,
                    id(),
                    "The item source is invalid or unsupported by the MMOItems resolver."
            );
        }
        MmoItemsKey key = MmoItemsKey.parse(source.getIdentifier());
        if (key == null) {
            return ItemSourceProbe.of(
                    ItemSourceProbeStatus.INVALID_SOURCE,
                    source,
                    id(),
                    "MMOItems sources require a '<type>:<item>' identifier."
            );
        }
        try {
            if (!mmoItemsReady()) {
                return ItemSourceProbe.of(
                        ItemSourceProbeStatus.PROVIDER_NOT_READY,
                        source,
                        id(),
                        "MMOItems is not enabled or its API instance is not ready."
                );
            }
            Type type = resolveType(key.typeId());
            if (type == null) {
                return ItemSourceProbe.of(
                        ItemSourceProbeStatus.SOURCE_NOT_FOUND,
                        source,
                        id(),
                        "MMOItems does not contain the requested item type."
                );
            }
            ItemStack itemStack = createItem(type, key.itemId(), 1);
            return itemStack == null || itemStack.getType().isAir()
                    ? ItemSourceProbe.of(
                    ItemSourceProbeStatus.SOURCE_NOT_FOUND,
                    source,
                    id(),
                    "MMOItems does not contain the requested item."
            )
                    : ItemSourceProbe.ready(source, id());
        } catch (LinkageError exception) {
            return ItemSourceProbe.of(ItemSourceProbeStatus.INCOMPATIBLE, source, id(), detail(exception));
        } catch (RuntimeException exception) {
            return ItemSourceProbe.of(ItemSourceProbeStatus.RESOLUTION_ERROR, source, id(), detail(exception));
        }
    }

    @Override
    public ItemSource identify(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir() || !mmoItemsReady()) {
            return null;
        }
        try {
            Type type = MMOItems.getType(itemStack);
            String itemId = Texts.toStringSafe(MMOItems.getID(itemStack)).trim();
            if (type == null || Texts.isBlank(type.getId()) || Texts.isBlank(itemId)) {
                return null;
            }
            return new ItemSource(ItemSourceType.MMOITEMS, type.getId() + ":" + itemId);
        } catch (RuntimeException | LinkageError exception) {
            return null;
        }
    }

    @Override
    public ItemStack create(ItemSource source, int amount) {
        if (!supports(source) || !mmoItemsReady()) {
            return null;
        }
        MmoItemsKey key = MmoItemsKey.parse(source.getIdentifier());
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
    public String displayName(ItemSource source) {
        if (!supports(source) || !mmoItemsReady()) {
            return null;
        }
        MmoItemsKey key = MmoItemsKey.parse(source.getIdentifier());
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
