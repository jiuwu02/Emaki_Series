package emaki.jiuwu.craft.cooking.service.display;

import emaki.jiuwu.craft.cooking.model.StationCoordinates;
import emaki.jiuwu.craft.cooking.model.StationType;
import emaki.jiuwu.craft.corelib.display.TextDisplayService;

public final class CookingTextDisplayService {

    private final TextDisplayService delegate;

    public CookingTextDisplayService(TextDisplayService delegate) {
        this.delegate = delegate;
    }

    public void upsert(CookingTextDisplaySpec spec) {
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

    public void shutdown() {
        delegate.shutdown();
    }

    public String backendName() {
        return delegate.backendName();
    }
}
