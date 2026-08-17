package emaki.jiuwu.craft.strengthen.enhancement.pity;

import java.util.Map;
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
 * <p><strong>不持久化到磁盘。</strong> 计数在服务器重启后归零，这对首期「保底」语义是可接受的降级：
 * 它只会让玩家少吃一次垫刀损失，不会造成数值膨胀。做成物理持久化需要为 item 作用域选定 PDC 写入点、
 * 为 player 作用域选定存档格式，属于独立工作项，不在本轮范围内。
 *
 * <p><strong>线程：</strong>基于 {@link ConcurrentHashMap}，可从任意线程调用。返回的 {@link PityState}
 * 是副本，调用方的修改不会影响表内状态，必须显式 {@link #save} 写回。
 */
public final class InMemoryPityStateStore implements PityStateStore {

    private final Map<String, PityState> states = new ConcurrentHashMap<>();

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
    }

    @Override
    public void remove(@NotNull String scope, @NotNull String group, @NotNull String key) {
        states.remove(composite(scope, group, key));
    }

    @Override
    public boolean exists(@NotNull String scope, @NotNull String group, @NotNull String key) {
        return states.containsKey(composite(scope, group, key));
    }

    /** 清空全部计数。用于插件重载时避免陈旧计数跨配置版本存活。 */
    public void clear() {
        states.clear();
    }

    /** {@return 当前记录数，供调试命令观察} */
    public int size() {
        return states.size();
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
