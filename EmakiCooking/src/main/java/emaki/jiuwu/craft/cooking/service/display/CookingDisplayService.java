package emaki.jiuwu.craft.cooking.service.display;

import emaki.jiuwu.craft.cooking.model.StationCoordinates;
import emaki.jiuwu.craft.cooking.model.StationType;
import emaki.jiuwu.craft.corelib.display.ItemDisplayService;

public final class CookingDisplayService {

    private final ItemDisplayService delegate;

    public CookingDisplayService(ItemDisplayService delegate) {
        this.delegate = delegate;
    }

    public void upsert(CookingDisplaySpec spec) {
        if (spec == null) {
            return;
        }
        delegate.upsert(spec.toCoreSpec());
    }

    public void remove(StationType stationType, StationCoordinates coordinates, String displayKey) {
        if (stationType == null || coordinates == null || displayKey == null) {
            return;
        }
        delegate.remove(CookingDisplayKeys.of(stationType, coordinates, displayKey));
    }

    public void removeStation(StationType stationType, StationCoordinates coordinates) {
        if (stationType == null || coordinates == null) {
            return;
        }
        delegate.removeGroup(CookingDisplayKeys.NAMESPACE, CookingDisplayKeys.group(stationType, coordinates));
    }

    public void removeStationType(StationType stationType) {
        if (stationType == null) {
            return;
        }
        delegate.removeGroupPrefix(CookingDisplayKeys.NAMESPACE, CookingDisplayKeys.typePrefix(stationType));
    }

    public void playStirAnimation(StationType stationType,
            StationCoordinates coordinates,
            double heightOffset,
            String rotationAxis,
            double rotationDegrees,
            int durationTicks) {
        if (stationType == null || coordinates == null) {
            return;
        }
        delegate.playTransformAnimation(
                CookingDisplayKeys.NAMESPACE,
                CookingDisplayKeys.group(stationType, coordinates),
                coordinates.location(0.5D, 0.5D, 0.5D),
                heightOffset,
                rotationAxis,
                rotationDegrees,
                durationTicks
        );
    }

    public boolean isAnimating(StationType stationType, StationCoordinates coordinates) {
        if (stationType == null || coordinates == null) {
            return false;
        }
        return delegate.isAnimating(
                CookingDisplayKeys.NAMESPACE, CookingDisplayKeys.group(stationType, coordinates));
    }

    public void shutdown() {
        delegate.shutdown();
    }

    public String backendName() {
        return delegate.backendName();
    }
}
