package emaki.jiuwu.craft.corelib.item;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.text.Texts;

final class ReflectiveMmoItemsItemSourceResolver implements ItemSourceResolver {

    private final Accessor accessor = new Accessor();

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
        return supports(source) && accessor.ensureAvailable();
    }

    @Override
    public ItemSource identify(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir() || !accessor.ensureAvailable()) {
            return null;
        }
        String typeId = accessor.resolveTypeId(itemStack);
        String itemId = accessor.resolveItemId(itemStack);
        if (Texts.isBlank(typeId) || Texts.isBlank(itemId)) {
            return null;
        }
        return new ItemSource(ItemSourceType.MMOITEMS, typeId + ":" + itemId);
    }

    @Override
    public ItemStack create(ItemSource source, int amount) {
        if (!supports(source) || !accessor.ensureAvailable()) {
            return null;
        }
        MmoItemsKey key = MmoItemsKey.parse(source.getIdentifier());
        if (key == null) {
            return null;
        }
        ItemStack itemStack = accessor.createItem(key);
        if (itemStack == null) {
            return null;
        }
        ItemStack cloned = itemStack.clone();
        cloned.setAmount(Math.max(1, amount));
        return cloned;
    }

    private static final class Accessor {

        private boolean initialized;
        private boolean available;
        private Field pluginField;
        private Method getItemMethod;
        private Method getTypeMethod;
        private Method getIdMethod;

        private synchronized boolean ensureAvailable() {
            if (!Bukkit.getPluginManager().isPluginEnabled("MMOItems")) {
                return false;
            }
            if (initialized) {
                return available;
            }
            initialized = true;
            try {
                Class<?> mmoItemsClass = Class.forName("net.Indyuce.mmoitems.MMOItems");
                pluginField = mmoItemsClass.getField("plugin");
                getItemMethod = mmoItemsClass.getMethod("getItem", String.class, String.class);
                getTypeMethod = mmoItemsClass.getMethod("getType", ItemStack.class);
                getIdMethod = mmoItemsClass.getMethod("getID", ItemStack.class);
                available = true;
            } catch (Throwable throwable) {
                available = false;
            }
            return available;
        }

        private String resolveTypeId(ItemStack itemStack) {
            Object rawType = invoke(getTypeMethod, null, itemStack);
            if (rawType == null) {
                return "";
            }
            try {
                Method getId = rawType.getClass().getMethod("getId");
                Object value = getId.invoke(rawType);
                return Texts.toStringSafe(value).trim();
            } catch (Throwable throwable) {
                return Texts.toStringSafe(rawType).trim();
            }
        }

        private String resolveItemId(ItemStack itemStack) {
            return Texts.toStringSafe(invoke(getIdMethod, null, itemStack)).trim();
        }

        private ItemStack createItem(MmoItemsKey key) {
            Object plugin = invokeField(pluginField);
            if (plugin == null || key == null) {
                return null;
            }
            ItemStack created = asItemStack(invoke(getItemMethod, plugin, key.typeId(), key.itemId()));
            if (created != null) {
                return created;
            }
            return asItemStack(invoke(getItemMethod, plugin, key.typeId().toUpperCase(Locale.ROOT), key.itemId()));
        }

        private Object invokeField(Field field) {
            if (field == null) {
                return null;
            }
            try {
                return field.get(null);
            } catch (Throwable throwable) {
                return null;
            }
        }

        private Object invoke(Method method, Object target, Object... arguments) {
            if (method == null) {
                return null;
            }
            try {
                return method.invoke(target, arguments);
            } catch (Throwable throwable) {
                return null;
            }
        }

        private ItemStack asItemStack(Object value) {
            return value instanceof ItemStack itemStack ? itemStack : null;
        }
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
