package emaki.jiuwu.craft.corelib.item;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceKind;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceProvider;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.text.Texts;

/**
 * Maps shorthand prefixes onto the providers that claim them.
 *
 * <p>The table is entirely provider-driven. There used to be a hard-coded list of seventeen prefixes
 * here plus a reserved-word short circuit for {@code emakiitem-} / {@code ei-}, which meant CoreLib
 * recognised an item source whose implementation lived in another plugin, and registered parsers never
 * even saw those two prefixes. Now every prefix arrives with the provider that can actually resolve it,
 * so the ownership rule is enforced by the structure rather than by convention.
 *
 * <p>Matching is by <strong>longest prefix first</strong>. Without that, {@code ei-} would swallow
 * {@code eci-} and EcoItems entries would be handed to EmakiItem.
 *
 * <p>This is a singleton because {@link ItemSourceUtil}'s static entry points must reach it. Binding a
 * prefix that is already bound to the <em>same</em> kind is idempotent rather than a failure, so a
 * secondary {@link ItemSourceService} instance re-registering the built-in vanilla provider does not
 * break; binding it to a different kind is a hard failure naming the first owner.
 */
public final class ItemSourceRegistry {

    private static final ItemSourceRegistry SYSTEM = new ItemSourceRegistry();

    private final Object writeLock = new Object();
    private final Map<String, Binding> bindings = new LinkedHashMap<>();

    /** Prefixes ordered longest first; rebuilt on every bind/unbind, read without a lock. */
    private volatile List<Binding> orderedBindings = List.of();

    private ItemSourceRegistry() {
    }

    /** {@return the shared registry backing {@link ItemSourceUtil}} */
    public static ItemSourceRegistry system() {
        return SYSTEM;
    }

    /**
     * Binds a provider's shorthand prefixes.
     *
     * @param kind the provider's kind
     * @param provider the provider
     * @param ownerName owning plugin name, for diagnostics; may be empty for CoreLib built-ins
     * @return {@code null} on success, otherwise a stable reason key describing the conflict
     */
    String bind(ItemSourceKind kind, ItemSourceProvider provider, String ownerName) {
        if (kind == null || provider == null) {
            return "itemsource.register.missing_provider";
        }
        List<String> prefixes = normalizedPrefixes(provider);
        if (prefixes.isEmpty()) {
            return "itemsource.register.no_prefix";
        }
        synchronized (writeLock) {
            for (String prefix : prefixes) {
                Binding existing = bindings.get(prefix);
                if (existing != null && !existing.kind().equals(kind)) {
                    return Texts.isBlank(existing.ownerName())
                            ? "itemsource.register.duplicate_prefix"
                            : "itemsource.register.duplicate_prefix_owned_by:" + existing.ownerName()
                                    + ":" + prefix;
                }
            }
            for (String prefix : prefixes) {
                bindings.put(prefix, new Binding(prefix, kind, provider, Texts.toStringSafe(ownerName)));
            }
            rebuildOrder();
            return null;
        }
    }

    /**
     * Removes every prefix bound to {@code kind}.
     *
     * @param kind the kind being revoked
     */
    void unbind(ItemSourceKind kind) {
        if (kind == null) {
            return;
        }
        synchronized (writeLock) {
            boolean removed = bindings.entrySet().removeIf(entry -> entry.getValue().kind().equals(kind));
            if (removed) {
                rebuildOrder();
            }
        }
    }

    /**
     * Parses shorthand text into a reference.
     *
     * <p>Prefixed text goes to the claiming provider, which normalises the identifier itself. Bare text
     * such as {@code IRON_INGOT} falls through to the vanilla reading, which stays in CoreLib because it
     * is the catch-all path rather than any one plugin's item source.
     *
     * @param shorthand the shorthand text
     * @return the reference, or {@code null} when nothing claims it and it is not a vanilla material id
     */
    public ItemSourceRef parseShorthand(String shorthand) {
        if (Texts.isBlank(shorthand)) {
            return null;
        }
        String text = Texts.trim(shorthand);
        String lower = Texts.lower(text);
        for (Binding binding : orderedBindings) {
            if (!lower.startsWith(binding.prefix())) {
                continue;
            }
            String identifier = text.substring(binding.prefix().length());
            if (Texts.isBlank(identifier)) {
                return null;
            }
            String normalized = normalizeIdentifier(binding.provider(), identifier);
            return Texts.isBlank(normalized) ? null : ItemSourceRef.orNull(binding.kind(), normalized);
        }
        return ItemSourceUtil.parseVanillaShorthand(text);
    }

    /**
     * Writes a reference back into shorthand text.
     *
     * @param ref the reference
     * @return the shorthand, or {@code null} when no provider claims the kind
     */
    public String toShorthand(ItemSourceRef ref) {
        if (ref == null) {
            return null;
        }
        for (Binding binding : orderedBindings) {
            if (!binding.kind().equals(ref.kind())) {
                continue;
            }
            try {
                String shorthand = binding.provider().toShorthand(ref);
                if (Texts.isNotBlank(shorthand)) {
                    return shorthand;
                }
            } catch (LinkageError | RuntimeException failure) {
                return null;
            }
            return null;
        }
        return null;
    }

    /**
     * {@return whether any provider claims {@code kind}}
     *
     * @param kind the kind to test
     */
    public boolean claims(ItemSourceKind kind) {
        if (kind == null) {
            return false;
        }
        for (Binding binding : orderedBindings) {
            if (binding.kind().equals(kind)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeIdentifier(ItemSourceProvider provider, String identifier) {
        try {
            return provider.normalizeIdentifier(identifier);
        } catch (LinkageError | RuntimeException failure) {
            return null;
        }
    }

    private static List<String> normalizedPrefixes(ItemSourceProvider provider) {
        java.util.Set<String> declared;
        try {
            declared = provider.shorthandPrefixes();
        } catch (LinkageError | RuntimeException failure) {
            return List.of();
        }
        if (declared == null || declared.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>(declared.size());
        for (String prefix : declared) {
            if (Texts.isNotBlank(prefix)) {
                normalized.add(Texts.lower(Texts.trim(prefix)));
            }
        }
        return List.copyOf(normalized);
    }

    private void rebuildOrder() {
        List<Binding> ordered = new ArrayList<>(bindings.values());
        ordered.sort((left, right) -> {
            int byLength = Integer.compare(right.prefix().length(), left.prefix().length());
            return byLength != 0 ? byLength : left.prefix().compareTo(right.prefix());
        });
        orderedBindings = List.copyOf(ordered);
    }

    private record Binding(String prefix, ItemSourceKind kind, ItemSourceProvider provider, String ownerName) {
    }
}
