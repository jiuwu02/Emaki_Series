package emaki.jiuwu.craft.attribute.bridge;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.attribute.api.AttributeContribution;
import emaki.jiuwu.craft.attribute.api.AttributeContributionProvider;
import emaki.jiuwu.craft.attribute.model.AttributeDefinition;
import emaki.jiuwu.craft.attribute.service.AttributeService;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class MmoItemsBridge {

    private final EmakiAttributePlugin plugin;
    private final AttributeService attributeService;
    private final ReflectiveMmoItemsAccessor accessor;
    private final AttributeContributionProvider contributionProvider;

    public MmoItemsBridge(EmakiAttributePlugin plugin, AttributeService attributeService) {
        this.plugin = plugin;
        this.attributeService = attributeService;
        this.accessor = new ReflectiveMmoItemsAccessor(plugin);
        this.contributionProvider = new MmoItemsAttributeContributionProvider();
        this.attributeService.registerContributionProvider(contributionProvider);
        if (!accessor.ensureAvailable()) {
            warnBridgeUnavailable(accessor.availabilityError());
        }
    }

    private void warnBridgeUnavailable(String error) {
        if (plugin == null || plugin.messageService() == null) {
            return;
        }
        plugin.messageService().warning("console.mmoitems_bridge_unavailable", Map.of(
            "error", Texts.toStringSafe(error)
        ));
    }

    private final class MmoItemsAttributeContributionProvider implements AttributeContributionProvider {

        @Override
        public String id() {
            return "mmoitems_attribute_mapping";
        }

        @Override
        public int priority() {
            return 220;
        }

        @Override
        public List<AttributeContribution> collect(LivingEntity entity) {
            if (entity == null) {
                return List.of();
            }
            List<AttributeDefinition> mappings = attributeService.mmoItemsMappedDefinitions();
            if (mappings.isEmpty() || !accessor.ensureAvailable()) {
                return List.of();
            }
            EntityEquipment equipment = entity.getEquipment();
            if (equipment == null) {
                return List.of();
            }
            List<AttributeContribution> contributions = new ArrayList<>();
            for (ItemSlot slot : equippedSlots(equipment)) {
                if (slot.item() == null || slot.item().getType().isAir() || !accessor.isMmoItemsItem(slot.item())) {
                    continue;
                }
                Object liveItem = accessor.createLiveItem(slot.item());
                if (liveItem == null) {
                    continue;
                }
                String sourceId = buildSourceId(slot, slot.item());
                Map<String, Double> statCache = new LinkedHashMap<>();
                for (AttributeDefinition definition : mappings) {
                    if (definition == null || Texts.isBlank(definition.mmoItemsStatId())) {
                        continue;
                    }
                    double value = statCache.computeIfAbsent(
                        normalizeId(definition.mmoItemsStatId()),
                        key -> accessor.readDoubleStat(liveItem, definition.mmoItemsStatId(), definition.id())
                    );
                    if (Double.compare(value, 0D) == 0) {
                        continue;
                    }
                    contributions.add(new AttributeContribution(definition.id(), value, sourceId));
                }
            }
            return contributions.isEmpty() ? List.of() : List.copyOf(contributions);
        }

        private List<ItemSlot> equippedSlots(EntityEquipment equipment) {
            return List.of(
                new ItemSlot("main_hand", equipment.getItemInMainHand()),
                new ItemSlot("off_hand", equipment.getItemInOffHand()),
                new ItemSlot("helmet", equipment.getHelmet()),
                new ItemSlot("chestplate", equipment.getChestplate()),
                new ItemSlot("leggings", equipment.getLeggings()),
                new ItemSlot("boots", equipment.getBoots())
            );
        }

        private String buildSourceId(ItemSlot slot, ItemStack itemStack) {
            String typeId = accessor.resolveTypeId(itemStack);
            String itemId = accessor.resolveItemId(itemStack);
            StringBuilder builder = new StringBuilder("mmoitems:");
            builder.append(slot == null ? "item" : slot.name());
            if (Texts.isNotBlank(typeId)) {
                builder.append(':').append(typeId);
            }
            if (Texts.isNotBlank(itemId)) {
                builder.append(':').append(itemId);
            }
            return builder.toString();
        }
    }

    private record ItemSlot(String name, ItemStack item) {
    }

    private static final class ReflectiveMmoItemsAccessor {

        private final EmakiAttributePlugin plugin;
        private final Set<String> warnings = new LinkedHashSet<>();
        private boolean initialized;
        private boolean available;
        private String availabilityError = "";
        private Method mmoItemsGetTypeMethod;
        private Method mmoItemsGetIdMethod;
        private Constructor<?> liveMmoItemConstructor;
        private Method mmoItemGetDataMethod;
        private Method itemStatGetIdMethod;
        private final Map<String, Object> statObjects = new LinkedHashMap<>();

        private ReflectiveMmoItemsAccessor(EmakiAttributePlugin plugin) {
            this.plugin = plugin;
        }

        private synchronized boolean ensureAvailable() {
            if (initialized) {
                return available;
            }
            initialized = true;
            try {
                Class<?> mmoItemsClass = Class.forName("net.Indyuce.mmoitems.MMOItems");
                Class<?> itemStatsClass = Class.forName("net.Indyuce.mmoitems.ItemStats");
                Class<?> itemStatClass = Class.forName("net.Indyuce.mmoitems.stat.type.ItemStat");
                Class<?> liveMmoItemClass = Class.forName("net.Indyuce.mmoitems.api.item.mmoitem.LiveMMOItem");
                Class<?> mmoItemClass = Class.forName("net.Indyuce.mmoitems.api.item.mmoitem.MMOItem");
                mmoItemsGetTypeMethod = mmoItemsClass.getMethod("getType", ItemStack.class);
                mmoItemsGetIdMethod = mmoItemsClass.getMethod("getID", ItemStack.class);
                liveMmoItemConstructor = liveMmoItemClass.getConstructor(ItemStack.class);
                mmoItemGetDataMethod = mmoItemClass.getMethod("getData", itemStatClass);
                itemStatGetIdMethod = itemStatClass.getMethod("getId");
                loadStatObjects(itemStatsClass, itemStatClass);
                available = true;
            } catch (Throwable throwable) {
                available = false;
                availabilityError = throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage();
            }
            return available;
        }

        private String availabilityError() {
            return availabilityError;
        }

        private boolean isMmoItemsItem(ItemStack itemStack) {
            return Texts.isNotBlank(resolveTypeId(itemStack)) && Texts.isNotBlank(resolveItemId(itemStack));
        }

        private String resolveTypeId(ItemStack itemStack) {
            Object rawType = invokeStatic(mmoItemsGetTypeMethod, itemStack);
            return readableId(rawType);
        }

        private String resolveItemId(ItemStack itemStack) {
            Object rawId = invokeStatic(mmoItemsGetIdMethod, itemStack);
            return Texts.toStringSafe(rawId).trim();
        }

        private Object createLiveItem(ItemStack itemStack) {
            if (!ensureAvailable() || liveMmoItemConstructor == null || itemStack == null || itemStack.getType().isAir()) {
                return null;
            }
            try {
                return liveMmoItemConstructor.newInstance(itemStack);
            } catch (Throwable throwable) {
                warnOnce(
                    "console.mmoitems_stat_read_failed",
                    "live_item|" + throwable.getClass().getName(),
                    Map.of(
                        "attribute", "-",
                        "stat", "-",
                        "error", Texts.toStringSafe(throwable.getMessage())
                    )
                );
                return null;
            }
        }

        private double readDoubleStat(Object liveItem, String configuredStatId, String attributeId) {
            if (!ensureAvailable() || liveItem == null || Texts.isBlank(configuredStatId)) {
                return 0D;
            }
            Object statObject = statObjects.get(normalizeId(configuredStatId));
            if (statObject == null) {
                warnOnce(
                    "console.mmoitems_stat_resolve_failed",
                    "missing_stat|" + normalizeId(configuredStatId),
                    Map.of(
                        "attribute", safeText(attributeId, "-"),
                        "stat", configuredStatId
                    )
                );
                return 0D;
            }
            try {
                Object data = mmoItemGetDataMethod.invoke(liveItem, statObject);
                Double numeric = toDouble(data);
                return numeric == null ? 0D : numeric;
            } catch (Throwable throwable) {
                warnOnce(
                    "console.mmoitems_stat_read_failed",
                    "read_stat|" + safeText(attributeId, "-") + "|" + normalizeId(configuredStatId),
                    Map.of(
                        "attribute", safeText(attributeId, "-"),
                        "stat", configuredStatId,
                        "error", Texts.toStringSafe(throwable.getMessage())
                    )
                );
                return 0D;
            }
        }

        private void loadStatObjects(Class<?> itemStatsClass, Class<?> itemStatClass) throws IllegalAccessException {
            statObjects.clear();
            for (Field field : itemStatsClass.getFields()) {
                if (!Modifier.isStatic(field.getModifiers()) || !itemStatClass.isAssignableFrom(field.getType())) {
                    continue;
                }
                Object statObject = field.get(null);
                if (statObject == null) {
                    continue;
                }
                statObjects.putIfAbsent(normalizeId(field.getName()), statObject);
                Object rawId = invoke(itemStatGetIdMethod, statObject);
                String statId = readableId(rawId);
                if (Texts.isNotBlank(statId)) {
                    statObjects.putIfAbsent(normalizeId(statId), statObject);
                }
            }
        }

        private Object invokeStatic(Method method, Object argument) {
            return invoke(method, null, argument);
        }

        private Object invoke(Method method, Object target, Object... arguments) {
            if (method == null) {
                return null;
            }
            try {
                return method.invoke(target, arguments);
            } catch (Throwable ignored) {
                return null;
            }
        }

        private Double toDouble(Object value) {
            if (value == null) {
                return null;
            }
            if (value instanceof Number number) {
                return number.doubleValue();
            }
            try {
                Method getValueMethod = value.getClass().getMethod("getValue");
                Object raw = getValueMethod.invoke(value);
                if (raw instanceof Number number) {
                    return number.doubleValue();
                }
            } catch (Throwable ignored) {
            }
            return emaki.jiuwu.craft.corelib.math.Numbers.tryParseDouble(String.valueOf(value), null);
        }

        private String readableId(Object value) {
            if (value == null) {
                return "";
            }
            if (value instanceof String text) {
                return text.trim();
            }
            try {
                Method getIdMethod = value.getClass().getMethod("getId");
                Object raw = getIdMethod.invoke(value);
                if (raw != null) {
                    return String.valueOf(raw).trim();
                }
            } catch (Throwable ignored) {
            }
            return String.valueOf(value).trim();
        }

        private void warnOnce(String key, String uniqueId, Map<String, ?> replacements) {
            if (plugin == null || plugin.messageService() == null || !warnings.add(uniqueId)) {
                return;
            }
            plugin.messageService().warning(key, replacements);
        }

        private String normalizeId(String value) {
            return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        }

        private String safeText(String value, String fallback) {
            return Texts.isBlank(value) ? fallback : value;
        }
    }

    private static String normalizeId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }
}
