package emaki.jiuwu.craft.corelib.item;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceKind;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceProvider;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class ItemSourceRegistry {

    private static final ItemSourceRegistry SYSTEM = new ItemSourceRegistry();

    private final Object writeLock = new Object();
    private final Map<String, Binding> bindings = new LinkedHashMap<>();

    private volatile List<Binding> orderedBindings = List.of();

    private ItemSourceRegistry() {
    }

    public static ItemSourceRegistry system() {
        return SYSTEM;
    }

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
        Set<String> declared;
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
