package emaki.jiuwu.craft.corelib.item;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.item.ItemBuildIssue;
import emaki.jiuwu.craft.corelib.api.item.ItemComponentCapability;
import emaki.jiuwu.craft.corelib.api.item.ItemComponentPatch;
import io.papermc.paper.datacomponent.DataComponentType;


final class PaperItemComponentBridge {

    private static final Logger LOGGER = Logger.getLogger(PaperItemComponentBridge.class.getName());

    private static final String DATA_COMPONENT_TYPES_CLASS = "io.papermc.paper.datacomponent.DataComponentTypes";
    private static final String DATA_COMPONENT_REGISTRY_FIELD = "DATA_COMPONENT_TYPE";

    private final Map<String, DataComponentType> runtimeTypes = discoverRuntimeTypes();

    DataComponentType componentType(String componentId) {
        return runtimeTypes.get(componentId);
    }

    boolean supports(String componentId) {
        return runtimeTypes.containsKey(componentId);
    }

    boolean isNonValued(String componentId) {
        return componentType(componentId) instanceof DataComponentType.NonValued;
    }

    ItemStack parseItemStack(String itemSyntax) {
        return Bukkit.getItemFactory().createItemStack(itemSyntax);
    }

    boolean apply(ItemStack target,
            String componentId,
            ItemComponentPatch patch,
            MinecraftComponentValueCodec codec,
            List<ItemBuildIssue> issues) {
        DataComponentType type = componentType(componentId);
        if (target == null || type == null || patch == null) {
            return false;
        }
        try {
            switch (patch.operation()) {
                case UNSET -> target.unsetData(type);
                case RESET -> target.resetData(type);
                case SET -> applySet(target, componentId, type, patch.value(), codec);
            }
            return true;
        } catch (IllegalArgumentException exception) {
            issues.add(ItemBuildIssue.error(componentId, "Invalid component value: " + message(exception)));
        } catch (RuntimeException | LinkageError exception) {
            issues.add(ItemBuildIssue.error(componentId, "Paper component bridge failed: " + message(exception)));
        }
        return false;
    }

    List<ItemComponentCapability> capabilities(MinecraftItemComponentCatalog catalog) {
        Set<String> ids = new LinkedHashSet<>(catalog.entries().keySet());
        ids.addAll(runtimeTypes.keySet());
        List<ItemComponentCapability> result = new ArrayList<>(ids.size());
        for (String id : ids) {
            MinecraftItemComponentCatalog.Entry entry = catalog.entry(id);
            boolean runtimeSupported = supports(id);
            result.add(new ItemComponentCapability(
                    id,
                    runtimeSupported,
                    runtimeSupported,
                    entry == null ? "Vanilla component syntax" : entry.valueFormat()
            ));
        }
        result.sort(java.util.Comparator.comparing(ItemComponentCapability::componentId));
        return List.copyOf(result);
    }

    private void applySet(ItemStack target,
            String componentId,
            DataComponentType type,
            Object value,
            MinecraftComponentValueCodec codec) {
        String materialId = target.getType().getKey().toString();
        String encoded = codec.encode(componentId, value, type instanceof DataComponentType.NonValued);
        ItemStack decoded = parseItemStack(materialId + "[" + componentId + "=" + encoded + "]");
        if (decoded == null || !decoded.isDataOverridden(type)) {
            throw new IllegalArgumentException("Vanilla parser did not produce an overridden value.");
        }
        target.copyDataFrom(decoded, candidate -> candidate.equals(type));
    }

    private Map<String, DataComponentType> discoverRuntimeTypes() {
        Map<String, DataComponentType> result = new LinkedHashMap<>();
        discoverStaticTypes(result);
        discoverRegistryTypes(result);
        return Collections.unmodifiableMap(result);
    }

    private void discoverStaticTypes(Map<String, DataComponentType> destination) {
        try {
            Class<?> typesClass = Class.forName(DATA_COMPONENT_TYPES_CLASS, true, getClass().getClassLoader());
            for (Field field : typesClass.getFields()) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                Object value = field.get(null);
                addType(destination, value);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            LOGGER.warning("Static Paper data component discovery failed, falling back to registry discovery only: "
                    + message(exception));
        }
    }

    private void discoverRegistryTypes(Map<String, DataComponentType> destination) {
        try {
            Class<?> registryClass = Class.forName("org.bukkit.Registry", true, getClass().getClassLoader());
            Object registry = registryClass.getField(DATA_COMPONENT_REGISTRY_FIELD).get(null);
            if (registry instanceof Iterable<?> iterable) {
                for (Object value : iterable) {
                    addType(destination, value);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            LOGGER.warning("Registry-based Paper data component discovery failed, component support may be incomplete: "
                    + message(exception));
        }
    }

    private void addType(Map<String, DataComponentType> destination, Object value) {
        if (!(value instanceof DataComponentType type) || type.getKey() == null) {
            return;
        }
        destination.put(type.getKey().toString(), type);
    }

    private String message(Throwable throwable) {
        String message = throwable == null ? null : throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }
}
