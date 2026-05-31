package emaki.jiuwu.craft.cooking.service.display;

import emaki.jiuwu.craft.cooking.model.StationCoordinates;
import emaki.jiuwu.craft.cooking.model.StationType;

public interface CookingTextDisplayService {

    void upsert(CookingTextDisplaySpec spec);

    void remove(StationType stationType, StationCoordinates coordinates, String displayKey);

    void removeStation(StationType stationType, StationCoordinates coordinates);

    void removeStationType(StationType stationType);

    void shutdown();

    String backendName();
}
