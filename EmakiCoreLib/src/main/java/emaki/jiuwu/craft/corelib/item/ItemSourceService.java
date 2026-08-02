package emaki.jiuwu.craft.corelib.item;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceKind;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceProbeResult;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceProbeState;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceProvider;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRegistration;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.corelib.text.Texts;
import net.kyori.adventure.text.Component;

/**
 * The one registry of item source providers, keyed by {@link ItemSourceKind}.
 *
 * <p>Shaped after {@code StageRegistry}: entries carry a {@link Plugin} owner, a duplicate kind is a
 * hard failure naming the first owner rather than a silent overwrite, revocation is available per
 * handle and per owner, and a failed registration yields an inactive handle instead of {@code null}.
 *
 * <p>Registering a provider also binds its shorthand prefixes into {@link ItemSourceRegistry}, because
 * a provider whose prefixes lived elsewhere would be unreachable from config. Both halves are installed
 * and revoked together.
 */
public final class ItemSourceService {

    private final Map<String, RegisteredProvider> providers = new LinkedHashMap<>();
    private final AtomicLong registrationSequence = new AtomicLong();
    private volatile List<ItemSourceProvider> orderedProviders = List.of();

    /** Creates a service already carrying CoreLib's built-in vanilla provider. */
    public ItemSourceService() {
        registerProvider(null, new VanillaItemSourceProvider());
    }

    /**
     * Registers a provider and binds its shorthand prefixes.
     *
     * @param owner the owning plugin, or {@code null} for CoreLib's own built-ins
     * @param provider the provider implementation
     * @return a revocable handle; an inactive handle when the kind or a prefix is already taken
     */
    public synchronized ItemSourceRegistration registerProvider(@Nullable Plugin owner,
            @Nullable ItemSourceProvider provider) {
        if (provider == null) {
            return ItemSourceRegistration.unavailable(null, "itemsource.register.missing_provider");
        }
        ItemSourceKind kind;
        try {
            kind = provider.kind();
        } catch (RuntimeException failure) {
            return ItemSourceRegistration.unavailable(null, "itemsource.register.missing_kind");
        }
        if (kind == null) {
            return ItemSourceRegistration.unavailable(null, "itemsource.register.missing_kind");
        }
        RegisteredProvider existing = providers.get(kind.key());
        if (existing != null) {
            if (existing.provider() == provider) {
                return new Handle(this, kind, existing.generation(), true, "");
            }
            return ItemSourceRegistration.unavailable(kind,
                    Texts.isBlank(existing.ownerName())
                            ? "itemsource.register.duplicate_kind"
                            : "itemsource.register.duplicate_kind_owned_by:" + existing.ownerName()
                                    + ":" + kind.key());
        }
        String prefixConflict = ItemSourceRegistry.system().bind(kind, provider,
                owner == null ? "" : owner.getName());
        if (prefixConflict != null) {
            return ItemSourceRegistration.unavailable(kind, prefixConflict);
        }
        long generation = registrationSequence.incrementAndGet();
        providers.put(kind.key(), new RegisteredProvider(provider, owner,
                owner == null ? "" : owner.getName(), generation));
        refreshCache();
        return new Handle(this, kind, generation, true, "");
    }

    /**
     * Revokes every provider owned by {@code owner}.
     *
     * @param owner the owning plugin
     * @return how many providers were revoked
     */
    public synchronized int revokeAll(@Nullable Plugin owner) {
        if (owner == null) {
            return 0;
        }
        int removed = 0;
        for (Map.Entry<String, RegisteredProvider> entry : List.copyOf(providers.entrySet())) {
            if (entry.getValue().owner() != owner) {
                continue;
            }
            providers.remove(entry.getKey());
            ItemSourceRegistry.system().unbind(entry.getValue().provider().kind());
            removed++;
        }
        if (removed > 0) {
            refreshCache();
        }
        return removed;
    }

    /**
     * {@return the provider owning {@code kind}, or {@code null} when none is registered}
     *
     * @param kind the kind to look up
     */
    public @Nullable ItemSourceProvider providerOf(@Nullable ItemSourceKind kind) {
        if (kind == null) {
            return null;
        }
        RegisteredProvider registered = providers.get(kind.key());
        return registered == null ? null : registered.provider();
    }

    /** {@return every registered provider, highest priority first} */
    public List<ItemSourceProvider> providers() {
        return orderedProviders;
    }

    /**
     * Recognises an existing stack.
     *
     * @param itemStack the stack to inspect
     * @return the reference, or {@code null} when no provider claims it
     */
    public @Nullable ItemSourceRef identifyItem(@Nullable ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return null;
        }
        for (ItemSourceProvider provider : orderedProviders) {
            ItemSourceRef ref = safeIdentify(provider, itemStack);
            if (ref != null) {
                return ref;
            }
        }
        return null;
    }

    /**
     * Builds an item.
     *
     * @param ref the reference to build
     * @param amount the stack amount
     * @return the item, or {@code null} when no provider can build it
     */
    public @Nullable ItemStack createItem(@Nullable ItemSourceRef ref, int amount) {
        if (ref == null) {
            return null;
        }
        for (ItemSourceProvider provider : orderedProviders) {
            if (!safeSupports(provider, ref)) {
                continue;
            }
            ItemStack created = safeCreate(provider, ref, amount);
            if (created != null) {
                return created;
            }
        }
        return null;
    }

    /**
     * @param ref the reference to test
     * @return whether it resolves right now
     */
    public boolean isAvailable(@Nullable ItemSourceRef ref) {
        return probe(ref).ready();
    }

    /**
     * Probes shorthand text, separating "no plugin claims this prefix" from "the item does not exist".
     *
     * @param shorthand the shorthand text
     * @return the outcome; never {@code null}
     */
    public ItemSourceProbeResult probeShorthand(@Nullable String shorthand) {
        if (Texts.isBlank(shorthand)) {
            return ItemSourceProbeResult.of(ItemSourceProbeState.INVALID_SOURCE, null, "",
                    "The item source shorthand is blank.");
        }
        try {
            ItemSourceRef ref = ItemSourceUtil.parseShorthand(shorthand);
            if (ref != null) {
                return probe(ref);
            }
            // No provider claimed the prefix, and the vanilla fallback rejected it too. This is the
            // distinction the old design could not express: it is not a missing item, it is a missing
            // plugin, and the message shown to a server owner has to say so.
            return ItemSourceProbeResult.of(ItemSourceProbeState.PROVIDER_MISSING, null, "",
                    "No installed plugin supplies the item source \"" + Texts.trim(shorthand) + "\".");
        } catch (LinkageError exception) {
            return ItemSourceProbeResult.of(ItemSourceProbeState.INCOMPATIBLE, null, "", detail(exception));
        } catch (RuntimeException exception) {
            return ItemSourceProbeResult.of(ItemSourceProbeState.RESOLUTION_ERROR, null, "", detail(exception));
        }
    }

    /**
     * Probes a reference.
     *
     * @param ref the reference to probe
     * @return the outcome; never {@code null}
     */
    public ItemSourceProbeResult probe(@Nullable ItemSourceRef ref) {
        if (ref == null) {
            return ItemSourceProbeResult.of(ItemSourceProbeState.INVALID_SOURCE, null, "",
                    "The item source kind and identifier are required.");
        }
        ItemSourceProbeResult firstFailure = null;
        for (ItemSourceProvider provider : orderedProviders) {
            if (!safeSupports(provider, ref)) {
                continue;
            }
            ItemSourceProbeResult result;
            try {
                result = provider.probe(ref);
            } catch (LinkageError exception) {
                result = ItemSourceProbeResult.of(ItemSourceProbeState.INCOMPATIBLE, ref,
                        provider.kind().key(), detail(exception));
            } catch (RuntimeException exception) {
                result = ItemSourceProbeResult.of(ItemSourceProbeState.RESOLUTION_ERROR, ref,
                        provider.kind().key(), detail(exception));
            }
            if (result == null) {
                result = ItemSourceProbeResult.of(ItemSourceProbeState.RESOLUTION_ERROR, ref,
                        provider.kind().key(), "The provider returned no probe result.");
            }
            if (result.ready()) {
                return result;
            }
            if (firstFailure == null) {
                firstFailure = result;
            }
        }
        return firstFailure == null ? ItemSourceProbeResult.providerMissing(ref) : firstFailure;
    }

    /**
     * Resolves a unified display name.
     *
     * @param ref the reference to name
     * @return MiniMessage text, or an empty string when nothing could be resolved
     */
    public String displayName(@Nullable ItemSourceRef ref) {
        if (ref == null) {
            return "";
        }
        String explicitItemStackName = createdItemCustomDisplayName(ref);
        if (Texts.isNotBlank(explicitItemStackName)) {
            return explicitItemStackName;
        }
        if (ref.vanilla()) {
            String vanillaName = vanillaDisplayName(ref);
            if (Texts.isNotBlank(vanillaName)) {
                return vanillaName;
            }
        }
        for (ItemSourceProvider provider : orderedProviders) {
            if (!safeSupports(provider, ref)) {
                continue;
            }
            String displayName = safeDisplayName(provider, ref);
            if (Texts.isNotBlank(displayName)) {
                return displayName;
            }
        }
        String itemStackName = createdItemDisplayName(ref);
        if (Texts.isNotBlank(itemStackName)) {
            return itemStackName;
        }
        return fallbackDisplayName(ref);
    }

    private void refreshCache() {
        List<ItemSourceProvider> values = new ArrayList<>(providers.size());
        for (RegisteredProvider registered : providers.values()) {
            values.add(registered.provider());
        }
        values.sort(Comparator.comparingInt(ItemSourceProvider::priority).reversed()
                .thenComparing(provider -> provider.kind().key()));
        orderedProviders = values.isEmpty() ? List.of() : List.copyOf(values);
    }

    private synchronized boolean revokeIfMatches(ItemSourceKind kind, long generation) {
        RegisteredProvider registered = providers.get(kind.key());
        if (registered == null || registered.generation() != generation) {
            return false;
        }
        providers.remove(kind.key());
        ItemSourceRegistry.system().unbind(kind);
        refreshCache();
        return true;
    }

    private static boolean safeSupports(ItemSourceProvider provider, ItemSourceRef ref) {
        try {
            return provider.supports(ref);
        } catch (LinkageError | RuntimeException failure) {
            return false;
        }
    }

    private static ItemSourceRef safeIdentify(ItemSourceProvider provider, ItemStack itemStack) {
        try {
            return provider.identify(itemStack);
        } catch (LinkageError | RuntimeException failure) {
            return null;
        }
    }

    private static ItemStack safeCreate(ItemSourceProvider provider, ItemSourceRef ref, int amount) {
        try {
            return provider.create(ref, amount);
        } catch (LinkageError | RuntimeException failure) {
            return null;
        }
    }

    private static String safeDisplayName(ItemSourceProvider provider, ItemSourceRef ref) {
        try {
            return provider.displayName(ref);
        } catch (LinkageError | RuntimeException failure) {
            return null;
        }
    }

    private static String detail(Throwable throwable) {
        if (throwable == null) {
            return "Unknown resolution failure";
        }
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }

    private String vanillaDisplayName(ItemSourceRef ref) {
        Material material = ItemSourceUtil.resolveVanillaMaterial(ref.identifier());
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

    private String createdItemCustomDisplayName(ItemSourceRef ref) {
        ItemStack itemStack = createItem(ref, 1);
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

    private String createdItemDisplayName(ItemSourceRef ref) {
        ItemStack itemStack = createItem(ref, 1);
        return itemStack == null || itemStack.getType().isAir() ? "" : ItemTextBridge.effectiveNameText(itemStack);
    }

    private String fallbackDisplayName(ItemSourceRef ref) {
        String shorthand = ItemSourceUtil.toShorthand(ref);
        return Texts.isBlank(shorthand) ? ref.identifier() : shorthand;
    }

    /** CoreLib's own vanilla provider. Priority {@code 0} so every other provider outranks it. */
    private static final class VanillaItemSourceProvider implements ItemSourceProvider {

        @Override
        public ItemSourceKind kind() {
            return ItemSourceKind.VANILLA;
        }

        @Override
        public int priority() {
            return 0;
        }

        @Override
        public java.util.Set<String> shorthandPrefixes() {
            return java.util.Set.of("minecraft-", "mc-", "v-");
        }

        @Override
        public String canonicalShorthandPrefix() {
            return "minecraft-";
        }

        @Override
        public String normalizeIdentifier(String identifier) {
            return ItemSourceUtil.normalizeVanillaIdentifier(identifier);
        }

        @Override
        public boolean supports(ItemSourceRef ref) {
            return ref != null && ref.vanilla();
        }

        @Override
        public ItemSourceRef identify(ItemStack itemStack) {
            if (itemStack == null || itemStack.isEmpty()) {
                return null;
            }
            return ItemSourceRef.orNull(ItemSourceKind.VANILLA,
                    itemStack.getType().name().toLowerCase(java.util.Locale.ROOT));
        }

        @Override
        public ItemStack create(ItemSourceRef ref, int amount) {
            if (!supports(ref)) {
                return null;
            }
            Material material = ItemSourceUtil.resolveVanillaMaterial(ref.identifier());
            return material == null ? null : new ItemStack(material, Math.max(1, amount));
        }
    }

    private record RegisteredProvider(ItemSourceProvider provider, Plugin owner, String ownerName,
            long generation) {
    }

    private static final class Handle implements ItemSourceRegistration {

        private final ItemSourceService service;
        private final ItemSourceKind kind;
        private final long generation;
        private final boolean successful;
        private final String reasonKey;

        private volatile boolean active;

        private Handle(ItemSourceService service, ItemSourceKind kind, long generation,
                boolean successful, String reasonKey) {
            this.service = service;
            this.kind = kind;
            this.generation = generation;
            this.successful = successful;
            this.reasonKey = reasonKey;
            this.active = successful;
        }

        @Override
        public boolean successful() {
            return successful;
        }

        @Override
        public ItemSourceKind kind() {
            return kind;
        }

        @Override
        public String reasonKey() {
            return reasonKey;
        }

        @Override
        public boolean active() {
            return active;
        }

        @Override
        public void close() {
            if (!active) {
                return;
            }
            active = false;
            service.revokeIfMatches(kind, generation);
        }
    }
}
