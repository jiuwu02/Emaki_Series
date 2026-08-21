package emaki.jiuwu.craft.strengthen.enhancement.pity;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class InMemoryPityStateStore implements PityStateStore {

    private final Map<String, PityState> states = new ConcurrentHashMap<>();
    private final Path persistenceFile;
    private volatile boolean dirty;

    public InMemoryPityStateStore() {
        this(null);
    }

    public InMemoryPityStateStore(@Nullable Path persistenceFile) {
        this.persistenceFile = persistenceFile;
    }

    @Override
    public @Nullable PityState load(@NotNull String scope, @NotNull String group, @NotNull String key) {
        PityState stored = states.get(composite(scope, group, key));
        return stored == null ? null : stored.copy();
    }

    @Override
    public void save(@NotNull String scope, @NotNull String group, @NotNull String key, @NotNull PityState state) {
        String composite = composite(scope, group, key);
        if (composite.isEmpty() || state == null) {
            return;
        }
        states.put(composite, state.copy());
        dirty = true;
    }

    @Override
    public void remove(@NotNull String scope, @NotNull String group, @NotNull String key) {
        states.remove(composite(scope, group, key));
        dirty = true;
    }

    @Override
    public boolean exists(@NotNull String scope, @NotNull String group, @NotNull String key) {
        return states.containsKey(composite(scope, group, key));
    }

    public void clear() {
        states.clear();
        dirty = true;
    }

    public synchronized void loadFromDisk() {
        if (persistenceFile == null || !Files.isRegularFile(persistenceFile)) {
            dirty = false;
            return;
        }
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(persistenceFile, StandardOpenOption.READ)) {
            properties.load(input);
        } catch (IOException exception) {
            dirty = false;
            return;
        }
        for (String encoded : properties.stringPropertyNames()) {
            String composite = decodeKey(encoded);
            if (Texts.isBlank(composite)) {
                continue;
            }
            String[] parts = composite.split("\\|", 3);
            if (parts.length != 3) {
                continue;
            }
            String[] values = properties.getProperty(encoded, "").split(",", 3);
            if (values.length != 3) {
                continue;
            }
            try {
                int counter = Math.max(0, Integer.parseInt(values[0]));
                long lastTrigger = Math.max(0L, Long.parseLong(values[1]));
                boolean triggered = Boolean.parseBoolean(values[2]);
                states.put(composite, new PityState(counter, lastTrigger, triggered));
            } catch (NumberFormatException ignored) {
            }
        }
        dirty = false;
    }

    public synchronized void saveToDisk() {
        if (!dirty) {
            return;
        }
        if (persistenceFile == null) {
            dirty = false;
            return;
        }
        if (persist()) {
            dirty = false;
        }
    }

    public int size() {
        return states.size();
    }

    public boolean isDirty() {
        return dirty;
    }

    public boolean persistent() {
        return persistenceFile != null;
    }

    public synchronized boolean flushToDisk() {
        if (!dirty) {
            return true;
        }
        if (persistenceFile == null) {
            dirty = false;
            return true;
        }
        if (persist()) {
            dirty = false;
            return true;
        }
        return false;
    }

    public @NotNull Map<String, PityState> snapshot() {
        Map<String, PityState> copy = new LinkedHashMap<>();
        states.forEach((composite, state) -> copy.put(composite, state.copy()));
        return Map.copyOf(copy);
    }

    public int removeGroup(@NotNull String group) {
        String normalized = Texts.lower(group);
        if (Texts.isBlank(normalized)) {
            return 0;
        }
        int removed = 0;
        for (String composite : Set.copyOf(states.keySet())) {
            String[] parts = composite.split("\\|", 3);
            if (parts.length != 3) {
                continue;
            }
            if (parts[1].equals(normalized) || parts[1].startsWith(normalized + "#")) {
                states.remove(composite);
                removed++;
            }
        }
        if (removed > 0) {
            dirty = true;
        }
        return removed;
    }

    private synchronized boolean persist() {
        if (persistenceFile == null) {
            return false;
        }
        try {
            Path parent = persistenceFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Properties properties = new Properties();
            states.forEach((key, state) -> properties.setProperty(
                    encodeKey(key), state.getCounter() + "," + state.getLastTriggerTime() + "," + state.isTriggered()));
            Path temporary = persistenceFile.resolveSibling(persistenceFile.getFileName() + ".tmp");
            try (OutputStream output = Files.newOutputStream(temporary,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                properties.store(output, "EmakiStrengthen pity state");
            }
            try {
                Files.move(temporary, persistenceFile, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, persistenceFile, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static String encodeKey(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String decodeKey(String value) {
        try {
            return new String(Base64.getUrlDecoder().decode(value), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            return "";
        }
    }

    private static String composite(String scope, String group, String key) {
        String normalizedScope = Texts.lower(scope);
        String normalizedGroup = Texts.lower(group);
        String normalizedKey = Texts.toStringSafe(key);
        if (Texts.isBlank(normalizedGroup) || Texts.isBlank(normalizedKey)) {
            return "";
        }
        return normalizedScope + "|" + normalizedGroup + "|" + normalizedKey;
    }
}
