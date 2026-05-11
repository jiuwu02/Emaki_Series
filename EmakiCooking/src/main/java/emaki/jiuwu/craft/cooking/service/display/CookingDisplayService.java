package emaki.jiuwu.craft.cooking.service.display;

import emaki.jiuwu.craft.cooking.model.StationCoordinates;
import emaki.jiuwu.craft.cooking.model.StationType;

public interface CookingDisplayService {

    void upsert(CookingDisplaySpec spec);

    void remove(StationType stationType, StationCoordinates coordinates, String displayKey);

    void removeStation(StationType stationType, StationCoordinates coordinates);

    void removeStationType(StationType stationType);

    /**
     * 对指定 station 的所有展示实体播放翻炒动画（抛起 + 旋转 + 回落）。
     *
     * @param stationType     工位类型
     * @param coordinates     工位坐标
     * @param heightOffset    抛起高度（格）
     * @param rotationAxis    旋转轴 (x/y/z)
     * @param rotationDegrees 旋转角度（度）
     * @param durationTicks   动画总时长（tick）
     */
    void playStirAnimation(StationType stationType, StationCoordinates coordinates,
                           double heightOffset, String rotationAxis,
                           double rotationDegrees, int durationTicks);

    /**
     * 检查指定 station 是否正在播放动画。
     */
    boolean isAnimating(StationType stationType, StationCoordinates coordinates);

    void shutdown();

    String backendName();
}
