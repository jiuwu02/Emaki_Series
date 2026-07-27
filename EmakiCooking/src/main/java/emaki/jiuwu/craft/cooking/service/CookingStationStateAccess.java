package emaki.jiuwu.craft.cooking.service;

import java.util.Map;
import java.util.concurrent.CompletionStage;

import emaki.jiuwu.craft.cooking.model.StationCoordinates;
import emaki.jiuwu.craft.cooking.model.StationType;


interface CookingStationStateAccess {

    StationType stationType();


    Map<String, Object> snapshot(StationCoordinates coordinates);

    CompletionStage<Void> replace(StationCoordinates coordinates, Map<String, Object> committedState);

    CompletionStage<Void> delete(StationCoordinates coordinates);
}
