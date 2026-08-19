package emaki.jiuwu.craft.item.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import emaki.jiuwu.craft.item.api.ItemState;
import emaki.jiuwu.craft.item.api.ItemStateKey;
import emaki.jiuwu.craft.item.api.ItemStateMutation;
import emaki.jiuwu.craft.item.api.ItemStateSchema;
import emaki.jiuwu.craft.item.api.ItemStateSnapshot;
import emaki.jiuwu.craft.item.api.ItemStateType;
import emaki.jiuwu.craft.item.api.event.ItemStateChangeEvent;

/** PDC-backed implementation of the public item-state API. */
public final class EmakiItemStateService implements ItemState {

    /** Restores missing or mismatched state values after an item rebuild and verifies PDC readback. */
    public boolean restoreSnapshot(ItemStack item, ItemStateSnapshot snapshot) {
        if (item == null || snapshot == null) {
            return false;
        }
        PersistentDataContainer container = pdc(item);
        if (container == null) {
            return false;
        }
        boolean valid = true;
        for (Map.Entry<ItemStateKey<?>, Object> entry : snapshot.values().entrySet()) {
            ItemStateKey<?> key = entry.getKey();
            Object expected = entry.getValue();
            Object actual = read(container, key);
            if (!java.util.Objects.equals(expected, actual)) {
                @SuppressWarnings({"rawtypes", "unchecked"}) ItemStateMutation<?> mutation = set(item,
                        (ItemStateKey) key, expected);
                valid &= mutation.committed();
            }
            Object verified = read(pdc(item), key);
            valid &= java.util.Objects.equals(expected, verified);
        }
        return valid;
    }

    @Override
    public ItemStateSnapshot snapshot(ItemStack item) {
        Map<ItemStateKey<?>, Object> values = new LinkedHashMap<>();
        PersistentDataContainer pdc = pdc(item);
        if (pdc == null) {
            return new ItemStateSnapshot(item, values);
        }
        for (NamespacedKey namespacedKey : pdc.getKeys()) {
            if (!namespacedKey.getNamespace().equals(ItemStateSchema.NAMESPACE)
                    || !namespacedKey.getKey().startsWith(ItemStateSchema.PARTITION + ".")) {
                continue;
            }
            String field = namespacedKey.getKey();
            String key = field.substring(ItemStateSchema.PARTITION.length() + 1);
            Object value = readAny(pdc, namespacedKey);
            if (value == null) {
                continue;
            }
            ItemStateType type = typeOf(value);
            if (type != null) {
                values.put(new ItemStateKey<>(ItemStateSchema.NAMESPACE, ItemStateSchema.PARTITION, key, type), value);
            }
        }
        return new ItemStateSnapshot(item, values);
    }

    @Override
    public <T> Optional<T> get(ItemStack item, ItemStateKey<T> key) {
        if (key == null) {
            return Optional.empty();
        }
        PersistentDataContainer pdc = pdc(item);
        if (pdc == null) {
            return Optional.empty();
        }
        Object value = read(pdc, key);
        return value != null && key.javaType().isInstance(value)
                ? Optional.of(key.javaType().cast(value)) : Optional.empty();
    }

    @Override
    public <T> ItemStateMutation<T> set(ItemStack item, ItemStateKey<T> key, T value) {
        return setValue(item, key, value, false);
    }

    private <T> ItemStateMutation<T> setValue(ItemStack item, ItemStateKey<T> key, T value, boolean clamped) {
        Object coerced = key == null ? null : key.type().coerce(value);
        if (coerced == null) {
            return ItemStateMutation.rejected(key, "invalid_type", null);
        }
        T typedValue = key.javaType().cast(coerced);
        ItemMeta meta = itemMeta(item);
        if (meta == null) {
            return ItemStateMutation.rejected(key, "item_missing", null);
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        T old = get(item, key).orElse(null);
        if (hasWrongType(pdc, key)) {
            return ItemStateMutation.rejected(key, "wrong_type", old);
        }
        writeValue(pdc, key, typedValue);
        boolean committed = item.setItemMeta(meta);
        ItemStateMutation<T> result = ItemStateMutation.committed(key, old, typedValue,
                numericDelta(key.type(), old, typedValue), clamped);
        if (committed && result.changed()) {
            Bukkit.getPluginManager().callEvent(new ItemStateChangeEvent(item, result));
        }
        return committed ? result : ItemStateMutation.rejected(key, "commit_failed", old);
    }

    @Override
    public <T> ItemStateMutation<T> add(ItemStack item, ItemStateKey<T> key, Number amount) {
        if (key == null || amount == null || !key.type().numeric()) {
            return ItemStateMutation.rejected(key, "not_numeric", null);
        }
        T old = get(item, key).orElse(null);
        if (old == null) {
            return ItemStateMutation.rejected(key, "missing_state", null);
        }
        boolean clamped = false;
        Object next;
        try {
            next = switch (key.type()) {
                case INTEGER -> {
                    if (amount.doubleValue() != amount.intValue()) {
                        yield null;
                    }
                    yield Math.addExact((Integer) old, amount.intValue());
                }
                case LONG -> {
                    if (amount.doubleValue() != amount.longValue()) {
                        yield null;
                    }
                    yield Math.addExact((Long) old, amount.longValue());
                }
                case DOUBLE -> ((Double) old) + amount.doubleValue();
                default -> null;
            };
        } catch (ArithmeticException overflow) {
            clamped = true;
            next = amount.doubleValue() >= 0D
                    ? switch (key.type()) { case INTEGER -> Integer.MAX_VALUE; case LONG -> Long.MAX_VALUE; default -> Double.MAX_VALUE; }
                    : switch (key.type()) { case INTEGER -> Integer.MIN_VALUE; case LONG -> Long.MIN_VALUE; default -> -Double.MAX_VALUE; };
        }
        if (next == null) {
            return ItemStateMutation.rejected(key, "invalid_amount", old);
        }
        @SuppressWarnings("unchecked") T typed = (T) next;
        return setValue(item, key, typed, clamped);
    }

    @Override
    public <T> ItemStateMutation<T> remove(ItemStack item, ItemStateKey<T> key) {
        if (key == null) {
            return ItemStateMutation.rejected(key, "invalid_key", null);
        }
        ItemMeta meta = itemMeta(item);
        if (meta == null) {
            return ItemStateMutation.rejected(key, "item_missing", null);
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        T old = get(item, key).orElse(null);
        if (hasWrongType(pdc, key)) {
            return ItemStateMutation.rejected(key, "wrong_type", old);
        }
        if (old == null) {
            return ItemStateMutation.rejected(key, "missing_state", null);
        }
        pdc.remove(namespacedKey(key));
        boolean committed = item.setItemMeta(meta);
        ItemStateMutation<T> result = ItemStateMutation.committed(key, old, null, null, false);
        if (committed) {
            Bukkit.getPluginManager().callEvent(new ItemStateChangeEvent(item, result));
            return result;
        }
        return ItemStateMutation.rejected(key, "commit_failed", old);
    }

    private static PersistentDataContainer pdc(ItemStack item) {
        ItemMeta meta = itemMeta(item);
        return meta == null ? null : meta.getPersistentDataContainer();
    }

    private static ItemMeta itemMeta(ItemStack item) {
        return item == null || item.getType().isAir() ? null : item.getItemMeta();
    }

    private static NamespacedKey namespacedKey(ItemStateKey<?> key) {
        return ObjectsHolder.key(key.namespacedPath());
    }

    private static Object read(PersistentDataContainer pdc, ItemStateKey<?> key) {
        if (hasWrongType(pdc, key)) {
            return null;
        }
        return switch (key.type()) {
            case INTEGER -> pdc.get(namespacedKey(key), PersistentDataType.INTEGER);
            case LONG -> pdc.get(namespacedKey(key), PersistentDataType.LONG);
            case DOUBLE -> pdc.get(namespacedKey(key), PersistentDataType.DOUBLE);
            case BOOLEAN -> {
                Byte value = pdc.get(namespacedKey(key), PersistentDataType.BYTE);
                yield value == null || (value != 0 && value != 1) ? null : value == 1;
            }
            case STRING -> pdc.get(namespacedKey(key), PersistentDataType.STRING);
        };
    }

    private static boolean hasWrongType(PersistentDataContainer pdc, ItemStateKey<?> key) {
        NamespacedKey namespacedKey = namespacedKey(key);
        if (!pdc.getKeys().contains(namespacedKey)) {
            return false;
        }
        return switch (key.type()) {
            case INTEGER -> !pdc.has(namespacedKey, PersistentDataType.INTEGER);
            case LONG -> !pdc.has(namespacedKey, PersistentDataType.LONG);
            case DOUBLE -> !pdc.has(namespacedKey, PersistentDataType.DOUBLE);
            case BOOLEAN -> {
                Byte value = pdc.get(namespacedKey, PersistentDataType.BYTE);
                yield value == null || (value != 0 && value != 1);
            }
            case STRING -> !pdc.has(namespacedKey, PersistentDataType.STRING);
        };
    }

    private static void writeValue(PersistentDataContainer pdc, ItemStateKey<?> key, Object value) {
        NamespacedKey namespacedKey = namespacedKey(key);
        switch (key.type()) {
            case INTEGER -> pdc.set(namespacedKey, PersistentDataType.INTEGER, (Integer) value);
            case LONG -> pdc.set(namespacedKey, PersistentDataType.LONG, (Long) value);
            case DOUBLE -> pdc.set(namespacedKey, PersistentDataType.DOUBLE, (Double) value);
            case BOOLEAN -> pdc.set(namespacedKey, PersistentDataType.BYTE, (byte) ((Boolean) value ? 1 : 0));
            case STRING -> pdc.set(namespacedKey, PersistentDataType.STRING, (String) value);
        }
    }

    private static Object readAny(PersistentDataContainer pdc, NamespacedKey key) {
        if (pdc.has(key, PersistentDataType.INTEGER)) return pdc.get(key, PersistentDataType.INTEGER);
        if (pdc.has(key, PersistentDataType.LONG)) return pdc.get(key, PersistentDataType.LONG);
        if (pdc.has(key, PersistentDataType.DOUBLE)) return pdc.get(key, PersistentDataType.DOUBLE);
        if (pdc.has(key, PersistentDataType.BYTE)) {
            Byte value = pdc.get(key, PersistentDataType.BYTE);
            return value == null || (value != 0 && value != 1) ? null : value == 1;
        }
        return pdc.get(key, PersistentDataType.STRING);
    }

    private static ItemStateType typeOf(Object value) {
        if (value instanceof Integer) return ItemStateType.INTEGER;
        if (value instanceof Long) return ItemStateType.LONG;
        if (value instanceof Double) return ItemStateType.DOUBLE;
        if (value instanceof Boolean) return ItemStateType.BOOLEAN;
        if (value instanceof String) return ItemStateType.STRING;
        return null;
    }

    private static Number numericDelta(ItemStateType type, Object old, Object value) {
        if (!type.numeric() || old == null || value == null) return null;
        return switch (type) {
            case INTEGER -> ((Integer) value) - ((Integer) old);
            case LONG -> ((Long) value) - ((Long) old);
            case DOUBLE -> ((Double) value) - ((Double) old);
            default -> null;
        };
    }

    private static final class ObjectsHolder {
        private static NamespacedKey key(String value) {
            return NamespacedKey.fromString(value);
        }
    }
}
