package emaki.jiuwu.craft.corelib.item;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.corelib.text.Texts;
import net.kyori.adventure.text.Component;

public final class ItemSourceService {

    private final Map<String, ItemSourceResolver> resolvers = new LinkedHashMap<>();
    private volatile List<ItemSourceResolver> orderedResolvers = List.of();

    public ItemSourceService() {
        registerResolver(new VanillaItemSourceResolver());
    }

    public void registerResolver(@Nullable ItemSourceResolver resolver) {
        if (resolver == null || Texts.isBlank(resolver.id())) {
            return;
        }
        resolvers.put(Texts.normalizeId(resolver.id()), resolver);
        refreshCache();
    }

    public void unregisterResolver(@Nullable String resolverId) {
        if (Texts.isBlank(resolverId)) {
            return;
        }
        resolvers.remove(Texts.normalizeId(resolverId));
        refreshCache();
    }

    @Nullable
    public ItemSource identifyItem(@Nullable ItemStack itemStack) {
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

    @Nullable
    public ItemStack createItem(@Nullable ItemSource source, int amount) {
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

    public boolean isAvailable(@Nullable ItemSource source) {
        if (source == null || source.getType() == null) {
            return false;
        }
        for (ItemSourceResolver resolver : orderedResolvers) {
            if (!resolver.supports(source)) {
                continue;
            }
            if (resolver.isAvailable(source)) {
                return true;
            }
        }
        return false;
    }

    public String displayName(@Nullable ItemSource source) {
        if (source == null || source.getType() == null || Texts.isBlank(source.getIdentifier())) {
            return "";
        }
        if (source.getType() == ItemSourceType.VANILLA) {
            String vanillaName = vanillaDisplayName(source);
            if (Texts.isNotBlank(vanillaName)) {
                return vanillaName;
            }
        }
        for (ItemSourceResolver resolver : orderedResolvers) {
            if (!resolver.supports(source)) {
                continue;
            }
            String displayName = resolver.displayName(source);
            if (Texts.isNotBlank(displayName)) {
                return displayName;
            }
        }
        return fallbackDisplayName(source);
    }

    private void refreshCache() {
        List<ItemSourceResolver> values = new ArrayList<>(resolvers.values());
        values.sort(Comparator.comparingInt(ItemSourceResolver::priority).reversed()
                .thenComparing(resolver -> Texts.normalizeId(resolver.id())));
        orderedResolvers = values.isEmpty() ? List.of() : List.copyOf(values);
    }

    private String vanillaDisplayName(ItemSource source) {
        Material material = resolveMaterial(source.getIdentifier());
        if (material == null) {
            return "";
        }
        String translationKey = translationKey(material);
        return Texts.isBlank(translationKey) ? "" : MiniMessages.serialize(Component.translatable(translationKey));
    }

    private String translationKey(Material material) {
        if (material == null) {
            return "";
        }
        try {
            if (material.isItem()) {
                return material.getItemTranslationKey();
            }
            if (material.isBlock()) {
                return material.getBlockTranslationKey();
            }
            return material.getTranslationKey();
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private String fallbackDisplayName(ItemSource source) {
        String shorthand = ItemSourceUtil.toShorthand(source);
        return Texts.isBlank(shorthand) ? source.getIdentifier() : shorthand;
    }

    private Material resolveMaterial(String identifier) {
        return ItemSourceUtil.resolveVanillaMaterial(identifier);
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
            return new ItemSource(ItemSourceType.VANILLA, itemStack.getType().name().toLowerCase(java.util.Locale.ROOT));
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
            return ItemSourceUtil.resolveVanillaMaterial(identifier);
        }
    }
}

