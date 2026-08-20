package emaki.jiuwu.craft.item.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.item.api.ItemState;
import emaki.jiuwu.craft.item.api.ItemStateKey;
import emaki.jiuwu.craft.item.api.ItemStateMetadata;
import emaki.jiuwu.craft.item.api.ItemStateMutation;
import emaki.jiuwu.craft.item.api.ItemStateSchema;
import emaki.jiuwu.craft.item.api.ItemStateSnapshot;
import emaki.jiuwu.craft.item.api.ItemStateType;
import emaki.jiuwu.craft.item.api.event.ItemStateChangeEvent;
import emaki.jiuwu.craft.item.api.event.ItemStateThresholdEvent;
import emaki.jiuwu.craft.item.model.ItemStateConfig;

public final class EmakiItemStateService implements ItemState {

    private static final String THRESHOLD_MASK_PREFIX = ItemStateSchema.METADATA_PREFIX + "threshold_mask.";

    private final Supplier<ItemStateConfig> configSupplier;
    private final Supplier<DebugLogger> debugLoggerSupplier;

    public EmakiItemStateService() {
        this(null, null);
    }

    public EmakiItemStateService(Supplier<ItemStateConfig> configSupplier,
            Supplier<DebugLogger> debugLoggerSupplier) {
        this.configSupplier = configSupplier;
        this.debugLoggerSupplier = debugLoggerSupplier;
    }

    private DebugLogger debugLogger() {
        return debugLoggerSupplier == null ? null : debugLoggerSupplier.get();
    }

    public ItemStateConfig config() {
        if (configSupplier == null) {
            return ItemStateConfig.defaults();
        }
        ItemStateConfig current = configSupplier.get();
        return current == null ? ItemStateConfig.defaults() : current;
    }

    public boolean restoreSnapshot(ItemStack item, ItemStateSnapshot snapshot) {
        if (item == null || item.getType().isAir() || snapshot == null) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        ItemStateMetadata current = readMetadata(pdc);
        String instanceId = snapshot.metadata().instanceId().isBlank()
                ? current.instanceId()
                : snapshot.metadata().instanceId();
        if (instanceId.isBlank()) {
            instanceId = UUID.randomUUID().toString();
        }
        long baseRevision = Math.max(snapshot.metadata().revision(), current.revision());
        long revision = increment(baseRevision);
        boolean changed = false;
        for (Map.Entry<ItemStateKey<?>, Object> entry : snapshot.values().entrySet()) {
            ItemStateKey<?> key = entry.getKey();
            if (key == null || ItemStateSchema.metadataKey(key)) {
                continue;
            }
            Object expected = key.type().coerce(entry.getValue());
            if (expected == null) {
                return false;
            }
            Object actual = read(pdc, key);
            if (!Objects.equals(expected, actual)) {
                writeValue(pdc, key, expected);
                changed = true;
            }
        }
        writeMetadata(pdc, new ItemStateMetadata(
                ItemStateSchema.CURRENT_SCHEMA_VERSION,
                revision,
                instanceId,
                false));
        if (!item.setItemMeta(meta)) {
            return false;
        }
        ItemStateSnapshot verified = snapshot(item);
        for (Map.Entry<ItemStateKey<?>, Object> entry : snapshot.values().entrySet()) {
            if (ItemStateSchema.metadataKey(entry.getKey())) {
                continue;
            }
            if (!Objects.equals(entry.getValue(), verified.values().get(entry.getKey()))) {
                return false;
            }
        }
        ItemStateMetadata verifiedMetadata = verified.metadata();
        return verifiedMetadata.valid()
                && Objects.equals(instanceId, verifiedMetadata.instanceId())
                && verifiedMetadata.revision() == revision
                && (changed || revision >= baseRevision);
    }

    @Override
    public ItemStateSnapshot snapshot(ItemStack item) {
        return readSnapshot(item, null);
    }

    @Override
    public ItemStateSnapshot repair(ItemStack item) {
        ItemMeta meta = itemMeta(item);
        if (meta == null) {
            return new ItemStateSnapshot(item, Map.of(), ItemStateMetadata.empty());
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        ItemStateMetadata current = readMetadata(pdc);
        if (current.valid()) {
            return readSnapshot(item, current);
        }
        applyMigrations(pdc, current.schemaVersion());
        String instanceId = current.instanceId().isBlank() ? UUID.randomUUID().toString() : current.instanceId();
        ItemStateMetadata repaired = new ItemStateMetadata(
                ItemStateSchema.CURRENT_SCHEMA_VERSION,
                Math.max(0L, current.revision()),
                instanceId,
                false);
        writeMetadata(pdc, repaired);
        if (!item.setItemMeta(meta)) {
            return readSnapshot(item, new ItemStateMetadata(
                    repaired.schemaVersion(), repaired.revision(), repaired.instanceId(), true));
        }
        ItemStateSnapshot verified = readSnapshot(item, null);
        return verified.metadata().valid()
                ? verified
                : new ItemStateSnapshot(item, verified.values(), new ItemStateMetadata(
                        repaired.schemaVersion(), repaired.revision(), repaired.instanceId(), true));
    }

    @Override
    public <T> Optional<T> get(ItemStack item, ItemStateKey<T> key) {
        if (key == null || ItemStateSchema.metadataKey(key)) {
            return Optional.empty();
        }
        PersistentDataContainer pdc = pdc(item);
        if (pdc == null) {
            return Optional.empty();
        }
        Object value = read(pdc, key);
        return value != null && key.javaType().isInstance(value)
                ? Optional.of(key.javaType().cast(value))
                : Optional.empty();
    }

    @Override
    public <T> ItemStateMutation<T> set(ItemStack item, ItemStateKey<T> key, T value) {
        return setValue(item, key, value, false, null);
    }

    public <T> ItemStateMutation<T> set(ItemStack item, ItemStateKey<T> key, T value, Player holder) {
        return setValue(item, key, value, false, holder);
    }

    private <T> ItemStateMutation<T> setValue(ItemStack item,
            ItemStateKey<T> key,
            T value,
            boolean clamped,
            Player holder) {
        if (key == null) {
            return ItemStateMutation.rejected(null, "invalid_key", null);
        }
        if (ItemStateSchema.metadataKey(key)) {
            return ItemStateMutation.rejected(key, "metadata_reserved", null);
        }
        Object coerced = key.type().coerce(value);
        if (coerced == null) {
            return ItemStateMutation.rejected(key, "invalid_type", null);
        }
        ItemStateConfig config = config();
        ItemStateConfig.Field field = config.field(key.key());
        boolean boundsClamped = false;
        if (config.clampEnabled() && field != null && field.bounded() && key.type().numeric()) {
            Object bounded = clampToBounds(key.type(), coerced, field);
            boundsClamped = bounded != null && !Objects.equals(bounded, coerced);
            if (bounded != null) {
                coerced = bounded;
            }
        }
        T typedValue = key.javaType().cast(coerced);
        ItemMeta meta = itemMeta(item);
        if (meta == null) {
            return ItemStateMutation.rejected(key, "item_missing", null);
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        T old = readTyped(pdc, key);
        if (hasWrongType(pdc, key)) {
            return ItemStateMutation.rejected(key, "wrong_type", old);
        }
        boolean effectiveClamped = clamped || boundsClamped;
        ItemStateMetadata current = readMetadata(pdc);
        if (Objects.equals(old, typedValue) && current.valid()) {
            return ItemStateMutation.committed(key, old, typedValue,
                    numericDelta(key.type(), old, typedValue), effectiveClamped);
        }
        ItemStateThresholdEvaluator.Outcome thresholds = field == null || !key.type().numeric()
                ? null
                : ItemStateThresholdEvaluator.evaluate(field,
                        old instanceof Number number ? number : null,
                        typedValue instanceof Number number ? number : null,
                        readThresholdMask(pdc, key));
        writeValue(pdc, key, typedValue);
        if (thresholds != null && thresholds.maskChanged()) {
            writeThresholdMask(pdc, key, thresholds.mask());
        }
        writeMetadata(pdc, nextMetadata(current));
        boolean committed = item.setItemMeta(meta);
        ItemStateMutation<T> result = ItemStateMutation.committed(key, old, typedValue,
                numericDelta(key.type(), old, typedValue), effectiveClamped);
        if (committed && result.changed()) {
            Bukkit.getPluginManager().callEvent(new ItemStateChangeEvent(item, result, holder));
            dispatchThresholdEvents(item, holder, key, old, typedValue, thresholds);
        }
        return committed ? result : ItemStateMutation.rejected(key, "commit_failed", old);
    }

    private <T> void dispatchThresholdEvents(ItemStack item,
            Player holder,
            ItemStateKey<T> key,
            T old,
            T current,
            ItemStateThresholdEvaluator.Outcome outcome) {
        if (outcome == null || outcome.empty()) {
            return;
        }
        Number before = old instanceof Number number ? number : null;
        Number after = current instanceof Number number ? number : null;
        for (ItemStateThresholdEvaluator.Crossing crossing : outcome.crossings()) {
            Bukkit.getPluginManager().callEvent(new ItemStateThresholdEvent(
                    item,
                    holder,
                    key,
                    before,
                    after,
                    crossing.threshold().value(),
                    crossing.threshold().id(),
                    crossing.direction(),
                    crossing.threshold().once(),
                    crossing.rearmed()));
        }
    }

    private static Object clampToBounds(ItemStateType type, Object value, ItemStateConfig.Field field) {
        BigDecimal candidate = decimalOf(value);
        if (candidate == null) {
            return value;
        }
        BigDecimal bounded = candidate;
        if (field.minimum() != null && bounded.compareTo(field.minimum()) < 0) {
            bounded = field.minimum();
        }
        if (field.maximum() != null && bounded.compareTo(field.maximum()) > 0) {
            bounded = field.maximum();
        }
        if (bounded.compareTo(candidate) == 0) {
            return value;
        }
        return switch (type) {
            case INTEGER -> clampInteger(bounded);
            case LONG -> clampLong(bounded);
            case DOUBLE -> bounded.doubleValue();
            default -> value;
        };
    }

    private static Integer clampInteger(BigDecimal value) {
        BigDecimal floor = value.max(BigDecimal.valueOf(Integer.MIN_VALUE));
        BigDecimal ceiling = floor.min(BigDecimal.valueOf(Integer.MAX_VALUE));
        return ceiling.setScale(0, RoundingMode.DOWN).intValue();
    }

    private static Long clampLong(BigDecimal value) {
        BigDecimal floor = value.max(BigDecimal.valueOf(Long.MIN_VALUE));
        BigDecimal ceiling = floor.min(BigDecimal.valueOf(Long.MAX_VALUE));
        return ceiling.setScale(0, RoundingMode.DOWN).longValue();
    }

    private static BigDecimal decimalOf(Object value) {
        if (value instanceof Integer || value instanceof Long) {
            return BigDecimal.valueOf(((Number) value).longValue());
        }
        if (value instanceof Double doubleValue) {
            return Double.isFinite(doubleValue) ? BigDecimal.valueOf(doubleValue) : null;
        }
        return null;
    }

    private static long readThresholdMask(PersistentDataContainer pdc, ItemStateKey<?> key) {
        Long mask = pdc.get(thresholdMaskKey(key), PersistentDataType.LONG);
        return mask == null ? 0L : mask;
    }

    private static void writeThresholdMask(PersistentDataContainer pdc, ItemStateKey<?> key, long mask) {
        if (mask == 0L) {
            pdc.remove(thresholdMaskKey(key));
            return;
        }
        pdc.set(thresholdMaskKey(key), PersistentDataType.LONG, mask);
    }

    private static NamespacedKey thresholdMaskKey(ItemStateKey<?> key) {
        return ObjectsHolder.key(ItemStateSchema.NAMESPACE + ":" + ItemStateSchema.PARTITION
                + "." + THRESHOLD_MASK_PREFIX + key.key());
    }

    @Override
    public <T> ItemStateMutation<T> add(ItemStack item, ItemStateKey<T> key, Number amount) {
        return add(item, key, amount, null);
    }

    public <T> ItemStateMutation<T> add(ItemStack item, ItemStateKey<T> key, Number amount, Player holder) {
        if (key == null || amount == null || !key.type().numeric()) {
            return ItemStateMutation.rejected(key, "not_numeric", null);
        }
        T old = get(item, key).orElse(null);
        if (old == null) {
            old = configuredDefault(key);
        }
        if (old == null) {
            return ItemStateMutation.rejected(key, "missing_state", null);
        }
        boolean clamped = false;
        Object next;
        try {
            next = switch (key.type()) {
                case INTEGER -> amount.doubleValue() == amount.intValue()
                        ? Math.addExact((Integer) old, amount.intValue()) : null;
                case LONG -> amount.doubleValue() == amount.longValue()
                        ? Math.addExact((Long) old, amount.longValue()) : null;
                case DOUBLE -> ((Double) old) + amount.doubleValue();
                default -> null;
            };
        } catch (ArithmeticException overflow) {
            clamped = true;
            next = amount.doubleValue() >= 0D
                    ? switch (key.type()) {
                        case INTEGER -> Integer.MAX_VALUE;
                        case LONG -> Long.MAX_VALUE;
                        default -> Double.MAX_VALUE;
                    }
                    : switch (key.type()) {
                        case INTEGER -> Integer.MIN_VALUE;
                        case LONG -> Long.MIN_VALUE;
                        default -> -Double.MAX_VALUE;
                    };
        }
        if (next instanceof Double doubleValue && !Double.isFinite(doubleValue)) {
            clamped = true;
            next = amount.doubleValue() >= 0D ? Double.MAX_VALUE : -Double.MAX_VALUE;
        }
        if (next == null) {
            return ItemStateMutation.rejected(key, "invalid_amount", old);
        }
        @SuppressWarnings("unchecked") T typed = (T) next;
        return setValue(item, key, typed, clamped, holder);
    }

    private <T> T configuredDefault(ItemStateKey<T> key) {
        ItemStateConfig config = config();
        if (!config.fillDefaults()) {
            return null;
        }
        ItemStateConfig.Field field = config.field(key.key());
        if (field == null || field.type() != key.type() || field.defaultValue() == null) {
            return null;
        }
        Object coerced = key.type().coerce(field.defaultValue());
        return coerced == null ? null : key.javaType().cast(coerced);
    }

    @Override
    public <T> ItemStateMutation<T> remove(ItemStack item, ItemStateKey<T> key) {
        return remove(item, key, null);
    }

    public <T> ItemStateMutation<T> remove(ItemStack item, ItemStateKey<T> key, Player holder) {
        if (key == null) {
            return ItemStateMutation.rejected(null, "invalid_key", null);
        }
        if (ItemStateSchema.metadataKey(key)) {
            return ItemStateMutation.rejected(key, "metadata_reserved", null);
        }
        ItemMeta meta = itemMeta(item);
        if (meta == null) {
            return ItemStateMutation.rejected(key, "item_missing", null);
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        T old = readTyped(pdc, key);
        if (hasWrongType(pdc, key)) {
            return ItemStateMutation.rejected(key, "wrong_type", old);
        }
        if (old == null) {
            return ItemStateMutation.rejected(key, "missing_state", null);
        }
        pdc.remove(namespacedKey(key));
        pdc.remove(thresholdMaskKey(key));
        writeMetadata(pdc, nextMetadata(readMetadata(pdc)));
        boolean committed = item.setItemMeta(meta);
        ItemStateMutation<T> result = ItemStateMutation.committed(key, old, null, null, false);
        if (committed) {
            Bukkit.getPluginManager().callEvent(new ItemStateChangeEvent(item, result, holder));
            return result;
        }
        return ItemStateMutation.rejected(key, "commit_failed", old);
    }

    private void applyMigrations(PersistentDataContainer pdc, int storedVersion) {
        if (storedVersion >= ItemStateSchema.CURRENT_SCHEMA_VERSION) {
            return;
        }
        List<ItemStateConfig.Migration> path = config().migrationPath(storedVersion);
        if (path.isEmpty()) {
            return;
        }
        for (ItemStateConfig.Migration step : path) {
            step.droppedFields().forEach(dropped -> dropField(pdc, dropped));
            step.renamedFields().forEach((from, to) -> renameField(pdc, from, to));
            step.retypedFields().forEach((name, type) -> retypeField(pdc, name, type));
            logMigration(step);
        }
    }

    private void dropField(PersistentDataContainer pdc, String name) {
        NamespacedKey key = fieldKey(name);
        if (key != null) {
            pdc.remove(key);
        }
    }

    private void renameField(PersistentDataContainer pdc, String from, String to) {
        NamespacedKey source = fieldKey(from);
        NamespacedKey target = fieldKey(to);
        if (source == null || target == null || !pdc.getKeys().contains(source)) {
            return;
        }
        Object value = readAny(pdc, source);
        if (value == null) {
            pdc.remove(source);
            return;
        }
        ItemStateType type = typeOf(value);
        if (type == null) {
            pdc.remove(source);
            return;
        }
        writeValue(pdc, ItemStateSchema.key(to, type), value);
        pdc.remove(source);
    }

    private void retypeField(PersistentDataContainer pdc, String name, ItemStateType target) {
        NamespacedKey source = fieldKey(name);
        if (source == null || !pdc.getKeys().contains(source)) {
            return;
        }
        Object value = readAny(pdc, source);
        Object converted = value == null ? null : target.coerce(value);
        if (converted == null) {
            pdc.remove(source);
            return;
        }
        pdc.remove(source);
        writeValue(pdc, ItemStateSchema.key(name, target), converted);
    }

    private void logMigration(ItemStateConfig.Migration step) {
        DebugLogger debugLogger = debugLogger();
        if (debugLogger == null || !debugLogger.shouldLog("item_state", (Player) null)) {
            return;
        }
        debugLogger.log("item_state", (Player) null, "item_state.migrated", Map.of(
                "from", step.fromVersion(),
                "to", step.toVersion(),
                "renamed", step.renamedFields().keySet(),
                "retyped", step.retypedFields().keySet(),
                "dropped", step.droppedFields()));
    }

    private static NamespacedKey fieldKey(String name) {
        String normalized = name == null ? "" : name.trim();
        if (normalized.isBlank() || normalized.startsWith(ItemStateSchema.METADATA_PREFIX)) {
            return null;
        }
        try {
            return namespacedKey(ItemStateSchema.key(normalized, ItemStateType.STRING));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private ItemStateSnapshot readSnapshot(ItemStack item, ItemStateMetadata metadataOverride) {
        Map<ItemStateKey<?>, Object> values = new LinkedHashMap<>();
        PersistentDataContainer pdc = pdc(item);
        if (pdc == null) {
            return new ItemStateSnapshot(item, values, metadataOverride == null ? ItemStateMetadata.empty() : metadataOverride);
        }
        for (NamespacedKey namespacedKey : pdc.getKeys()) {
            if (!ItemStateSchema.NAMESPACE.equals(namespacedKey.getNamespace())
                    || !namespacedKey.getKey().startsWith(ItemStateSchema.PARTITION + ".")) {
                continue;
            }
            String keyName = namespacedKey.getKey().substring(ItemStateSchema.PARTITION.length() + 1);
            if (keyName.startsWith(ItemStateSchema.METADATA_PREFIX)) {
                continue;
            }
            Object value = readAny(pdc, namespacedKey);
            if (value == null) {
                continue;
            }
            try {
                ItemStateType type = typeOf(value);
                if (type != null) {
                    values.put(ItemStateSchema.key(keyName, type), value);
                }
            } catch (RuntimeException ignored) {
            }
        }
        ItemStateMetadata metadata = metadataOverride == null ? readMetadata(pdc) : metadataOverride;
        return new ItemStateSnapshot(item, values, metadata);
    }

    private static ItemStateMetadata readMetadata(PersistentDataContainer pdc) {
        if (pdc == null) {
            return ItemStateMetadata.empty();
        }
        Integer schema = pdc.get(namespacedKey(ItemStateSchema.SCHEMA_VERSION), PersistentDataType.INTEGER);
        Long revision = pdc.get(namespacedKey(ItemStateSchema.REVISION), PersistentDataType.LONG);
        String instanceId = pdc.get(namespacedKey(ItemStateSchema.INSTANCE_ID), PersistentDataType.STRING);
        int resolvedSchema = schema == null ? ItemStateSchema.CURRENT_SCHEMA_VERSION : schema;
        long resolvedRevision = revision == null ? 0L : Math.max(0L, revision);
        boolean valid = schema != null && schema == ItemStateSchema.CURRENT_SCHEMA_VERSION
                && revision != null && revision >= 0L && instanceId != null && !instanceId.isBlank();
        return new ItemStateMetadata(resolvedSchema, resolvedRevision, instanceId == null ? "" : instanceId, !valid);
    }

    private static ItemStateMetadata nextMetadata(ItemStateMetadata current) {
        String instanceId = current.instanceId().isBlank() ? UUID.randomUUID().toString() : current.instanceId();
        return new ItemStateMetadata(ItemStateSchema.CURRENT_SCHEMA_VERSION, increment(current.revision()), instanceId, false);
    }

    private static void writeMetadata(PersistentDataContainer pdc, ItemStateMetadata metadata) {
        pdc.set(namespacedKey(ItemStateSchema.SCHEMA_VERSION), PersistentDataType.INTEGER, ItemStateSchema.CURRENT_SCHEMA_VERSION);
        pdc.set(namespacedKey(ItemStateSchema.REVISION), PersistentDataType.LONG, Math.max(0L, metadata.revision()));
        pdc.set(namespacedKey(ItemStateSchema.INSTANCE_ID), PersistentDataType.STRING, metadata.instanceId());
    }

    private static long increment(long value) {
        return value == Long.MAX_VALUE ? Long.MAX_VALUE : Math.max(0L, value) + 1L;
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

    private static <T> T readTyped(PersistentDataContainer pdc, ItemStateKey<T> key) {
        Object value = read(pdc, key);
        return value == null || !key.javaType().isInstance(value) ? null : key.javaType().cast(value);
    }

    private static Object read(PersistentDataContainer pdc, ItemStateKey<?> key) {
        if (pdc == null || key == null || hasWrongType(pdc, key)) {
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
        if (pdc == null || key == null) {
            return true;
        }
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
            case INTEGER -> subtractInteger((Integer) value, (Integer) old);
            case LONG -> subtractLong((Long) value, (Long) old);
            case DOUBLE -> ((Double) value) - ((Double) old);
            default -> null;
        };
    }

    private static Number subtractInteger(int value, int old) {
        try {
            return Math.subtractExact(value, old);
        } catch (ArithmeticException exception) {
            return BigDecimal.valueOf(value).subtract(BigDecimal.valueOf(old));
        }
    }

    private static Number subtractLong(long value, long old) {
        try {
            return Math.subtractExact(value, old);
        } catch (ArithmeticException exception) {
            return BigDecimal.valueOf(value).subtract(BigDecimal.valueOf(old));
        }
    }

    private static final class ObjectsHolder {
        private static NamespacedKey key(String value) {
            return NamespacedKey.fromString(value);
        }
    }
}
