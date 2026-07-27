package emaki.jiuwu.craft.corelib.display;

/**
 * 发包后端所需的运行期参数。
 *
 * <p>做成窄接口而不是具体配置类，是为了让各模块用自己的配置源提供这些值：
 * EmakiCooking 用它的 {@code display_entities.*}，CoreLib 默认实例用 {@code display.*}。
 * 真实体后端不读这些值。
 */
public interface DisplayRuntimeSettings {

    /** {@return 虚拟实体的可见距离(格)} */
    double viewDistanceBlocks();

    /** {@return 可见性重算间隔(tick)} */
    int refreshIntervalTicks();

    /** {@return 使用给定常量值的实现} */
    static DisplayRuntimeSettings of(double viewDistanceBlocks, int refreshIntervalTicks) {
        double distance = Math.max(1D, viewDistanceBlocks);
        int interval = Math.max(1, refreshIntervalTicks);
        return new DisplayRuntimeSettings() {
            @Override
            public double viewDistanceBlocks() {
                return distance;
            }

            @Override
            public int refreshIntervalTicks() {
                return interval;
            }
        };
    }
}
