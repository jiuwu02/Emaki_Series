package emaki.jiuwu.craft.cooking.service.display;

import emaki.jiuwu.craft.cooking.model.StationCoordinates;
import emaki.jiuwu.craft.cooking.model.StationType;

/**
 * 工位文本展示实体服务。与 {@link CookingDisplayService}（物品展示）并行，
 * 负责在工位上方渲染运行态文本（结果、过程、下一步引导）。
 *
 * <p>实现需跟随主配置 {@code display_entities.backend}：
 * <ul>
 *     <li>{@code bukkit}：真实 {@link org.bukkit.entity.TextDisplay}</li>
 *     <li>{@code packet_events}：PacketEvents 虚拟 {@code TEXT_DISPLAY} 发包</li>
 * </ul>
 */
public interface CookingTextDisplayService {

    /**
     * 创建或更新一个文本展示实体。{@code spec.text()} 为空时等价于移除。
     */
    void upsert(CookingTextDisplaySpec spec);

    /**
     * 移除指定工位下某个 displayKey 对应的文本实体。
     */
    void remove(StationType stationType, StationCoordinates coordinates, String displayKey);

    /**
     * 移除指定工位坐标下的全部文本实体。
     */
    void removeStation(StationType stationType, StationCoordinates coordinates);

    /**
     * 移除某类工位的全部文本实体（reload 时清理用）。
     */
    void removeStationType(StationType stationType);

    /**
     * 关闭服务并清理全部文本实体。
     */
    void shutdown();

    /**
     * 当前后端名称（bukkit / packet_events）。
     */
    String backendName();
}
