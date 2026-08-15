package emaki.jiuwu.craft.station.gui;

import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.api.item.ConfiguredItemDefinition;
import emaki.jiuwu.craft.corelib.api.item.ItemComponentPatch;

public final class StationRenderFallbacks {

    private StationRenderFallbacks() {
    }

    public static ConfiguredItemDefinition purchaseUnavailable(String reason) {
        return new ConfiguredItemDefinition("GRAY_DYE", 1, Map.of(
                "minecraft:custom_name", ItemComponentPatch.set("<gray>Cannot buy queue slots</gray>"),
                "minecraft:lore", ItemComponentPatch.set(List.of("<dark_gray>" + reason + "</dark_gray>"))));
    }
}
