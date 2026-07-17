package emaki.jiuwu.craft.cooking.service;

import java.util.Map;
import java.util.concurrent.CompletionStage;

import emaki.jiuwu.craft.cooking.model.StationCoordinates;
import emaki.jiuwu.craft.cooking.model.StationType;

/** Runtime-owned bridge used by completion recovery to compare and durably replace station state. */
interface CookingStationStateAccess {

    StationType stationType();

    /** Returns the current logical serialized state, or {@code null} when no station state exists. */
    Map<String, Object> snapshot(StationCoordinates coordinates);

    CompletionStage<Void> replace(StationCoordinates coordinates, Map<String, Object> committedState);

    CompletionStage<Void> delete(StationCoordinates coordinates);
}
