package emaki.jiuwu.craft.station.gui;

import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.api.item.ConfiguredItemDefinition;
import emaki.jiuwu.craft.corelib.api.item.ItemComponentPatch;

/**
 * Last-resort item definitions for states a layout did not describe.
 *
 * <p>These are only reached when a layout omits the corresponding {@code virtual_items} entry. They exist so a
 * missing entry shows an honest "this state happened" icon instead of an empty slot, which would read as "there
 * is nothing here" and hide the actual condition.
 *
 * <p>They are deliberately plain English and unstyled: a layout that cares about wording should supply its own
 * entry, and a fallback that looked designed would discourage that.
 */
public final class StationRenderFallbacks {

    private StationRenderFallbacks() {
    }

    /**
     * Builds the icon for a queue purchase that cannot currently be made.
     *
     * @param reason the refusal reason key
     * @return the fallback definition
     */
    public static ConfiguredItemDefinition purchaseUnavailable(String reason) {
        return new ConfiguredItemDefinition("GRAY_DYE", 1, Map.of(
                "minecraft:custom_name", ItemComponentPatch.set("<gray>Cannot buy queue slots</gray>"),
                "minecraft:lore", ItemComponentPatch.set(List.of("<dark_gray>" + reason + "</dark_gray>"))));
    }
}
