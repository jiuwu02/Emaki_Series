package emaki.jiuwu.craft.corelib.script.js.registration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.text.Texts;

public final class JavaScriptRegistrationTracker {

    private final Map<Key, Entry> entries = new LinkedHashMap<>();
    private final List<Map<String, Object>> recentErrors = new ArrayList<>();

    public synchronized boolean register(Plugin owner,
            String scriptPath,
            JavaScriptRegistrationType type,
            String id,
            long durationMillis,
            Runnable unregisterCallback,
            Map<String, Object> metadata) {
        String ownerKey = ownerKey(owner);
        String normalizedScript = normalizeScript(scriptPath);
        String normalizedId = Texts.normalizeId(id);
        if (Texts.isBlank(normalizedId) || type == null) {
            recordError(normalizedScript, type, normalizedId, "register", "Registration id or type is blank.");
            return false;
        }
        Key key = new Key(type, normalizedId);
        Entry existing = entries.get(key);
        if (existing != null) {
            if (!existing.owner.equals(ownerKey) || !existing.scriptPath.equals(normalizedScript)) {
                recordError(normalizedScript, type, normalizedId, "register",
                        "Registration id already exists from " + existing.owner + ":" + existing.scriptPath + ".");
                return false;
            }
            safeUnregister(existing);
        }
        entries.put(key, new Entry(ownerKey,
                normalizedScript,
                normalizedId,
                type,
                System.currentTimeMillis(),
                Math.max(0L, durationMillis),
                unregisterCallback,
                metadata == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(metadata)),
                ""));
        return true;
    }

    public synchronized void unregisterScript(String scriptPath) {
        String normalizedScript = normalizeScript(scriptPath);
        for (Key key : List.copyOf(entries.keySet())) {
            Entry entry = entries.get(key);
            if (entry != null && entry.scriptPath.equals(normalizedScript)) {
                safeUnregister(entry);
                entries.remove(key);
            }
        }
    }

    public synchronized void unregisterOwner(Plugin owner) {
        String ownerKey = ownerKey(owner);
        if (Texts.isBlank(ownerKey)) {
            return;
        }
        for (Key key : List.copyOf(entries.keySet())) {
            Entry entry = entries.get(key);
            if (entry != null && entry.owner.equals(ownerKey)) {
                safeUnregister(entry);
                entries.remove(key);
            }
        }
    }

    public synchronized void unregisterAll() {
        for (Entry entry : List.copyOf(entries.values())) {
            safeUnregister(entry);
        }
        entries.clear();
    }

    public synchronized List<JavaScriptRegistrationSnapshot> snapshots() {
        return entries.values().stream()
                .map(Entry::snapshot)
                .sorted(Comparator.comparing(JavaScriptRegistrationSnapshot::scriptPath)
                        .thenComparing(snapshot -> snapshot.type().name())
                        .thenComparing(JavaScriptRegistrationSnapshot::id))
                .toList();
    }

    public synchronized List<JavaScriptRegistrationSnapshot> snapshotsByScript(String scriptPath) {
        String normalizedScript = normalizeScript(scriptPath);
        return snapshots().stream()
                .filter(snapshot -> snapshot.scriptPath().equals(normalizedScript))
                .toList();
    }

    public synchronized List<String> scripts() {
        return entries.values().stream()
                .map(entry -> entry.scriptPath)
                .filter(Texts::isNotBlank)
                .distinct()
                .sorted()
                .toList();
    }

    public synchronized void recordError(String scriptPath, JavaScriptRegistrationType type, String id, String phase, String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("script", normalizeScript(scriptPath));
        error.put("type", type == null ? "" : type.name().toLowerCase(java.util.Locale.ROOT));
        error.put("id", Texts.normalizeId(id));
        error.put("phase", Texts.toStringSafe(phase));
        error.put("message", Texts.toStringSafe(message));
        error.put("time", System.currentTimeMillis());
        recentErrors.add(error);
        while (recentErrors.size() > 20) {
            recentErrors.remove(0);
        }
    }

    public synchronized List<Map<String, Object>> recentErrors() {
        return List.copyOf(recentErrors);
    }

    private void safeUnregister(Entry entry) {
        if (entry == null || entry.unregisterCallback == null) {
            return;
        }
        try {
            entry.unregisterCallback.run();
        } catch (RuntimeException exception) {
            recordError(entry.scriptPath, entry.type, entry.id, "unregister", exception.getMessage());
        }
    }

    private static String ownerKey(Plugin owner) {
        return owner == null ? "" : owner.getName();
    }

    private static String normalizeScript(String scriptPath) {
        return Texts.toStringSafe(scriptPath).replace('\\', '/');
    }

    private record Key(JavaScriptRegistrationType type, String id) {
        private Key {
            id = Texts.normalizeId(id);
        }
    }

    private record Entry(String owner,
            String scriptPath,
            String id,
            JavaScriptRegistrationType type,
            long registeredAtMillis,
            long registrationDurationMillis,
            Runnable unregisterCallback,
            Map<String, Object> metadata,
            String lastError) {

        private JavaScriptRegistrationSnapshot snapshot() {
            return new JavaScriptRegistrationSnapshot(owner,
                    scriptPath,
                    id,
                    type,
                    registeredAtMillis,
                    registrationDurationMillis,
                    lastError,
                    metadata);
        }
    }
}
