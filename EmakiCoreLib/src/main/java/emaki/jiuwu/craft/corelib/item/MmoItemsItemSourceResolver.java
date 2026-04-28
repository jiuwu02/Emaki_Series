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
        return supports(source) && mmoItemsReady();
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
            ItemStack itemStack = MMOItems.plugin.getItem(type, key.itemId());
            if (itemStack == null) {
                String resolvedItemId = resolveTemplateId(type, key.itemId());
                itemStack = Texts.isBlank(resolvedItemId) ? null : MMOItems.plugin.getItem(type, resolvedItemId);
            }
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
