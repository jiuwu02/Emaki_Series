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
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.text.Texts;

/**
 * 进程内保底计数存储。
 *
 * <p>按 {@code scope + group + key} 三段定位一份 {@link PityState}。{@code scope} 区分 item / player，
 * {@code group} 隔离不同配方，{@code key} 是该作用域下的实体标识（物品实例 ID 或玩家 UUID）。
 *
 * <p>默认构造仍可作为纯内存实现；传入持久化文件时，状态会在启动时加载，并在生命周期显式
 * 调用 {@link #saveToDisk()} 时原子写盘。运行时 {@link #save}、{@link #remove} 和 {@link #clear()}
 * 只更新内存并标记 dirty，不在强化热路径执行文件 I/O。文件只保存已归一化的
 * {@code scope + group + owner key}，不依赖 CoreLib 或其他业务模块的私有存储。
 *
 * <p><strong>线程：</strong>基于 {@link ConcurrentHashMap}，可从任意线程调用。返回的 {@link PityState}
 * 是副本，调用方的修改不会影响表内状态，必须显式 {@link #save} 写回。
 */
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
        // 返回副本：否则调用方递增计数后即便不 save 也已生效，save 语义会形同虚设。
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

    /** 清空全部计数。用于插件重载时避免陈旧计数跨配置版本存活。 */
    public void clear() {
        states.clear();
        dirty = true;
    }

    /** 从 owner 数据文件加载已有计数；损坏或不可读文件会安全降级为空存储。 */
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
                // 单条损坏记录不应阻塞其他 owner/group 的加载。
            }
        }
        dirty = false;
    }

    /** 将当前 owner-scoped 状态原子写入磁盘；仅由生命周期调用，避免热路径同步 I/O。 */
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

    /** {@return 当前记录数，供调试命令观察} */
    public int size() {
        return states.size();
    }

    /** {@return 是否存在尚未写入持久化文件的内存变更} */
    public boolean isDirty() {
        return dirty;
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
            // 持久化失败不影响本次强化；dirty 保持为 true，等待下一次生命周期 flush。
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
