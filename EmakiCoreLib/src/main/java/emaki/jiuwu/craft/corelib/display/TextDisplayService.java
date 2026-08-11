package emaki.jiuwu.craft.corelib.display;

/**
 * 文本展示实体服务。
 *
 * <p>所有方法都是幂等的：{@code upsert} 会按 {@link DisplayKey} 创建或更新，
 * 文本为空时等价于移除。实现可能是真实体或发包虚拟实体，由后端决定。
 *
 * <p>调用约定：涉及位置的操作应在对应区域线程发起；实现内部会用
 * {@code ExecutionDispatcher} 切换到正确线程，因此调用方无需自行调度。
 */
public interface TextDisplayService {

    /** 创建或更新一个文本展示实体。{@code spec} 文本为空时移除该条目。 */
    void upsert(TextDisplaySpec spec);

    /** 移除单个条目。 */
    void remove(DisplayKey key);

    /** 移除某个分组下的全部条目。 */
    void removeGroup(String namespace, String group);

    /**
     * 移除命名空间内分组名以指定前缀开头的全部条目。
     *
     * <p>用于按更粗的维度批量清理，例如同一类工位的所有坐标。
     */
    void removeGroupPrefix(String namespace, String groupPrefix);

    /** 移除某个命名空间下的全部条目。 */
    void removeNamespace(String namespace);

    /** 释放全部条目与内部任务。 */
    void shutdown();

    /** {@return 后端名称，{@code bukkit} 或 {@code packet}} */
    String backendName();
}
