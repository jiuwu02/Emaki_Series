package emaki.jiuwu.craft.cooking;

import emaki.jiuwu.craft.cooking.model.StationBreakContext;
import emaki.jiuwu.craft.cooking.model.StationInteraction;
import emaki.jiuwu.craft.corelib.api.furniture.FurnitureEvent;
import emaki.jiuwu.craft.corelib.api.furniture.FurnitureEventListener;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;

final class CookingFurnitureEventSubscriber implements FurnitureEventListener {

    private final CookingStationListener stationListener;

    CookingFurnitureEventSubscriber(CookingStationListener stationListener) {
        this.stationListener = stationListener;
    }

    @Override
    public void onFurnitureEvent(FurnitureEvent event) {
        ItemSourceRef source = toSource(event);
        if (source == null || event.anchor() == null) {
            return;
        }
        switch (event.type()) {
            case INTERACT, HIT -> {
                if (event.player() == null) {
                    return;
                }
                stationListener.dispatchInteraction(new StationInteraction(
                        event.player(),
                        event.anchor(),
                        event.leftClick(),
                        event.rightClick(),
                        event.mainHand(),
                        event.cancelConsumer(),
                        source
                ));
            }
            case BREAK -> stationListener.dispatchBreak(new StationBreakContext(
                    event.player(),
                    event.anchor(),
                    event.cancelConsumer(),
                    source
            ));
            case PLACE -> {

            }
        }
    }

    private static ItemSourceRef toSource(FurnitureEvent event) {
        String rawId = event.rawId();
        return rawId == null || rawId.isBlank()
                ? null
                : ItemSourceRef.orNull(event.source(), rawId);
    }
}
