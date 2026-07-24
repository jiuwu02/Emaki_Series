package emaki.jiuwu.craft.corelib.item;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.corelib.text.Texts;
import net.kyori.adventure.text.Component;

public final class ItemSourceService {

    private final Map<String, RegisteredResolver> resolvers = new LinkedHashMap<>();
    private final AtomicLong registrationSequence = new AtomicLong();
    private volatile List<ItemSourceResolver> orderedResolvers = List.of();

    public ItemSourceService() {
        registerResolver(new VanillaItemSourceResolver());
    }

    public void registerResolver(@Nullable ItemSourceResolver resolver) {
        registerResolverInternal(resolver);
    }

    public synchronized ResolverRegistration registerResolverHandle(@Nullable ItemSourceResolver resolver) {
        RegisteredResolver registered = registerResolverInternal(resolver);
        if (registered == null) {
            return new ResolverRegistration("", null, -1L, false);
        }
        return new ResolverRegistration(
                Texts.normalizeId(registered.resolver().id()),
                registered.resolver(),
                registered.generation(),
                true
        );
    }

    private synchronized RegisteredResolver registerResolverInternal(@Nullable ItemSourceResolver resolver) {
        if (resolver == null || Texts.isBlank(resolver.id())) {
            return null;
        }
        String resolverId = Texts.normalizeId(resolver.id());
        long generation = registrationSequence.incrementAndGet();
        RegisteredResolver registered = new RegisteredResolver(resolver, generation);
        resolvers.put(resolverId, registered);
        refreshCache();
        return registered;
    }

    public synchronized void unregisterResolver(@Nullable String resolverId) {
        if (Texts.isBlank(resolverId)) {
            return;
        }
        resolvers.remove(Texts.normalizeId(resolverId));
        refreshCache();
    }

    public synchronized boolean unregisterResolver(@Nullable ItemSourceResolver resolver) {
        if (resolver == null || Texts.isBlank(resolver.id())) {
            return false;
        }
        String resolverId = Texts.normalizeId(resolver.id());
        RegisteredResolver registered = resolvers.get(resolverId);
        if (registered == null || registered.resolver() != resolver) {
            return false;
        }
        resolvers.remove(resolverId);
        refreshCache();
        return true;
    }

    @Nullable
    public ItemSource identifyItem(@Nullable ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
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
        return probe(source).ready();
    }

    public ItemSourceProbe probeShorthand(@Nullable String shorthand) {
        if (Texts.isBlank(shorthand)) {
            return invalidSource(null, "The item source shorthand is blank.");
        }
        try {
            ItemSource source = ItemSourceUtil.parseShorthand(shorthand);
            return source == null
                    ? invalidSource(null, "The item source shorthand is invalid: " + Texts.trim(shorthand))
                    : probe(source);
        } catch (LinkageError exception) {
            return ItemSourceProbe.of(ItemSourceProbeStatus.INCOMPATIBLE, null, "", detail(exception));
        } catch (RuntimeException exception) {
            return ItemSourceProbe.of(ItemSourceProbeStatus.RESOLUTION_ERROR, null, "", detail(exception));
        }
    }

    public ItemSourceProbe probe(@Nullable ItemSource source) {
        if (source == null || source.getType() == null || Texts.isBlank(source.getIdentifier())) {
            return invalidSource(source, "The item source type and identifier are required.");
        }
        ItemSourceProbe firstFailure = null;
        for (ItemSourceResolver resolver : orderedResolvers) {
            boolean supported;
            try {
                supported = resolver.supports(source);
            } catch (LinkageError exception) {
                firstFailure = firstFailure == null
                        ? ItemSourceProbe.of(ItemSourceProbeStatus.INCOMPATIBLE, source, resolver.id(), detail(exception))
                        : firstFailure;
                continue;
            } catch (RuntimeException exception) {
                firstFailure = firstFailure == null
                        ? ItemSourceProbe.of(ItemSourceProbeStatus.RESOLUTION_ERROR, source, resolver.id(), detail(exception))
                        : firstFailure;
                continue;
            }
            if (!supported) {
                continue;
            }
            ItemSourceProbe result;
            try {
                result = resolver.probe(source);
            } catch (LinkageError exception) {
                result = ItemSourceProbe.of(ItemSourceProbeStatus.INCOMPATIBLE, source, resolver.id(), detail(exception));
            } catch (RuntimeException exception) {
                result = ItemSourceProbe.of(ItemSourceProbeStatus.RESOLUTION_ERROR, source, resolver.id(), detail(exception));
            }
            if (result == null) {
                result = ItemSourceProbe.of(
                        ItemSourceProbeStatus.RESOLUTION_ERROR,
                        source,
                        resolver.id(),
                        "The resolver returned no probe result."
                );
            }
            if (result.ready()) {
                return result;
            }
            if (firstFailure == null) {
                firstFailure = result;
            }
        }
        return firstFailure == null
                ? ItemSourceProbe.of(
                ItemSourceProbeStatus.RESOLVER_MISSING,
                source,
                "",
                "No registered resolver supports item source type " + source.getType() + "."
        )
                : firstFailure;
    }

    public String displayName(@Nullable ItemSource source) {
        if (source == null || source.getType() == null || Texts.isBlank(source.getIdentifier())) {
            return "";
        }
        String explicitItemStackName = createdItemCustomDisplayName(source);
        if (Texts.isNotBlank(explicitItemStackName)) {
            return explicitItemStackName;
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
        String itemStackName = createdItemDisplayName(source);
        if (Texts.isNotBlank(itemStackName)) {
            return itemStackName;
        }
        return fallbackDisplayName(source);
    }

    private void refreshCache() {
        List<ItemSourceResolver> values = new ArrayList<>(resolvers.size());
        for (RegisteredResolver registered : resolvers.values()) {
            values.add(registered.resolver());
        }
        values.sort(Comparator.comparingInt(ItemSourceResolver::priority).reversed()
                .thenComparing(resolver -> Texts.normalizeId(resolver.id())));
        orderedResolvers = values.isEmpty() ? List.of() : List.copyOf(values);
    }

    private synchronized boolean unregisterResolverIfMatches(String resolverId, long generation) {
        RegisteredResolver registered = resolvers.get(resolverId);
        if (registered == null || registered.generation() != generation) {
            return false;
        }
        resolvers.remove(resolverId);
        refreshCache();
        return true;
    }

    private ItemSourceProbe invalidSource(ItemSource source, String detail) {
        return ItemSourceProbe.of(ItemSourceProbeStatus.INVALID_SOURCE, source, "", detail);
    }

    private String detail(Throwable throwable) {
        if (throwable == null) {
            return "Unknown resolution failure";
        }
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
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
        } catch (RuntimeException _) {
            return "";
        }
    }

    private String createdItemCustomDisplayName(ItemSource source) {
        ItemStack itemStack = createItem(source, 1);
        if (itemStack == null || itemStack.getType().isAir()) {
            return "";
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (!ItemTextBridge.hasCustomName(itemMeta)) {
            return "";
        }
        String displayName = MiniMessages.serialize(ItemTextBridge.customName(itemMeta));
        return Texts.isBlank(displayName) ? "" : displayName;
    }

    private String createdItemDisplayName(ItemSource source) {
        ItemStack itemStack = createItem(source, 1);
        return itemStack == null || itemStack.getType().isAir() ? "" : ItemTextBridge.effectiveNameText(itemStack);
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
        public boolean isAvailable(ItemSource source) {
            return supports(source) && resolveMaterial(source.getIdentifier()) != null;
        }

        @Override
        public ItemSource identify(ItemStack itemStack) {
            if (itemStack == null || itemStack.isEmpty()) {
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

    public final class ResolverRegistration implements AutoCloseable {

        private final String resolverId;
        private final ItemSourceResolver resolver;
        private final long generation;
        private final boolean registered;
        private final AtomicBoolean closed = new AtomicBoolean();

        private ResolverRegistration(
                String resolverId,
                ItemSourceResolver resolver,
                long generation,
                boolean registered) {
            this.resolverId = resolverId;
            this.resolver = resolver;
            this.generation = generation;
            this.registered = registered;
        }

        public String resolverId() {
            return resolverId;
        }

        public ItemSourceResolver resolver() {
            return resolver;
        }

        public boolean registered() {
            return registered;
        }

        public boolean unregister() {
            return registered && closed.compareAndSet(false, true)
                    && unregisterResolverIfMatches(resolverId, generation);
        }

        @Override
        public void close() {
            unregister();
        }
    }

    private record RegisteredResolver(ItemSourceResolver resolver, long generation) {
    }
}
