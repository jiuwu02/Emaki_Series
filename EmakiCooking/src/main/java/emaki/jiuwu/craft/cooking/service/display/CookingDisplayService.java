package emaki.jiuwu.craft.cooking.service.display;

import emaki.jiuwu.craft.cooking.model.StationCoordinates;
import emaki.jiuwu.craft.cooking.model.StationType;

public interface CookingDisplayService {

    void upsert(CookingDisplaySpec spec);

    void remove(StationType stationType, StationCoordinates coordinates, String displayKey);

    void removeStation(StationType stationType, StationCoordinates coordinates);

    void removeStationType(StationType stationType);

    void playStirAnimation(StationType stationType, StationCoordinates coordinates,
                           double heightOffset, String rotationAxis,
                           double rotationDegrees, int durationTicks);

    boolean isAnimating(StationType stationType, StationCoordinates coordinates);

    void shutdown();

    String backendName();
}
