package emaki.jiuwu.craft.cooking.service.display;

import emaki.jiuwu.craft.cooking.model.StationCoordinates;
import emaki.jiuwu.craft.cooking.model.StationType;
import emaki.jiuwu.craft.corelib.display.DisplayKey;

public final class CookingDisplayKeys {

    public static final String NAMESPACE = "emakicooking";

    private CookingDisplayKeys() {
    }

    public static DisplayKey of(StationType stationType, StationCoordinates coordinates, String displayKey) {
        return new DisplayKey(NAMESPACE, group(stationType, coordinates), displayKey);
    }

    public static String group(StationType stationType, StationCoordinates coordinates) {
        return stationType.folderName() + ":" + coordinates.runtimeKey();
    }

    public static String typePrefix(StationType stationType) {
        return stationType.folderName() + ":";
    }
}
