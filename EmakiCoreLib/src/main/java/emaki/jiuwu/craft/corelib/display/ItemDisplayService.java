package emaki.jiuwu.craft.corelib.display;

/**
 * 物品展示实体服务。
 *
 * <p>与 {@link TextDisplayService} 结构一致，额外提供一段抬升加绕轴旋转再回落的变换动画。
 */
public interface ItemDisplayService {

    /** 创建或更新一个物品展示实体。 */
    void upsert(ItemDisplaySpec spec);

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

    /**
     * 对某个分组内的全部条目播放一段抬升 + 绕轴旋转 + 回落的动画。
     *
     * @param namespace     命名空间
     * @param group         分组
     * @param anchor        动画调度所用的位置锚点，用于确定区域线程
     * @param heightOffset  抬升高度
     * @param rotationAxis  旋转轴，{@code x} / {@code y} / {@code z}
     * @param rotationDegrees 旋转角度
     * @param durationTicks 总时长(tick)，前后两段各占一半
     */
    void playTransformAnimation(String namespace,
            String group,
            org.bukkit.Location anchor,
            double heightOffset,
            String rotationAxis,
            double rotationDegrees,
            int durationTicks);

    /** {@return 该分组是否正在播放动画} */
    boolean isAnimating(String namespace, String group);

    /** 释放全部条目与内部任务。 */
    void shutdown();

    /** {@return 后端名称，{@code bukkit} 或 {@code packet}} */
    String backendName();
}
