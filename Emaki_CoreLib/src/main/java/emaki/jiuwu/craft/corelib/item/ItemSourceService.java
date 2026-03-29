package emaki.jiuwu.craft.corelib.item;

import emaki.jiuwu.craft.corelib.text.Texts;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.inventory.ItemStack;

public final class ItemSourceService {

    private final Map<String, ItemSourceResolver> resolvers = new LinkedHashMap<>();
    private volatile List<ItemSourceResolver> orderedResolvers = List.of();

    public ItemSourceService() {
        registerResolver(new VanillaItemSourceResolver());
    }

    public void registerResolver(ItemSourceResolver resolver) {
        if (resolver == null || Texts.isBlank(resolver.id())) {
            return;
        }
        resolvers.put(normalizeId(resolver.id()), resolver);
        refreshCache();
    }

    public void unregisterResolver(String resolverId) {
        if (Texts.isBlank(resolverId)) {
            return;
        }
        resolvers.remove(normalizeId(resolverId));
        refreshCache();
    }

    public ItemSource identifyItem(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return null;
        }
        for (ItemSourceResolver resolver : orderedResolvers) {
            ItemSource source = resolver.identify(itemStack);
            if (source != null) {
                return source;
            }
        }
        return null;
    }

    public ItemStack createItem(ItemSource source, int amount) {
        if (source == null || source.getType() == null || Texts.isBlank(source.getIdentifier())) {
            return null;
        }
        for (ItemSourceResolver resolver : orderedResolvers) {
            if (!resolver.supports(source)) {
                continue;
            }
            ItemStack created = resolver.create(source, amount);
            if (created != null) {
                return created;
            }
        }
        return null;
    }

    private void refreshCache() {
        List<ItemSourceResolver> values = new ArrayList<>(resolvers.values());
        values.sort(Comparator.comparingInt(ItemSourceResolver::priority).reversed()
            .thenComparing(resolver -> normalizeId(resolver.id())));
        orderedResolvers = values.isEmpty() ? List.of() : List.copyOf(values);
    }

    private String normalizeId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private static final class VanillaItemSourceResolver implements ItemSourceResolver {

        @Override
        public String id() {
            return "corelib_vanilla";
        }

        @Override
        public int priority() {
            return 0;
        }

        @Override
        public boolean supports(ItemSource source) {
            return source != null && source.getType() == ItemSourceType.VANILLA;
        }

        @Override
        public ItemSource identify(ItemStack itemStack) {
            if (itemStack == null || itemStack.getType().isAir()) {
                return null;
            }
            return new ItemSource(ItemSourceType.VANILLA, itemStack.getType().name());
        }

        @Override
        public ItemStack create(ItemSource source, int amount) {
            if (!supports(source)) {
                return null;
            }
            Material material = resolveMaterial(source.getIdentifier());
            return material == null ? null : new ItemStack(material, Math.max(1, amount));
        }

        private Material resolveMaterial(String identifier) {
            if (Texts.isBlank(identifier)) {
                return null;
            }
            String normalized = identifier.trim().toLowerCase(Locale.ROOT);
            NamespacedKey key = normalized.contains(":")
                ? NamespacedKey.fromString(normalized)
                : NamespacedKey.minecraft(normalized);
            return key == null ? null : Registry.MATERIAL.get(key);
        }
    }
}
