package emaki.jiuwu.craft.cooking.model;

import java.util.function.Consumer;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public record StationBreakContext(Player player,
        Block block,
        Consumer<Boolean> cancelConsumer,
        ItemSourceRef stationSource) {

    public StationBreakContext(Player player,
            Block block,
            Consumer<Boolean> cancelConsumer) {
        this(player, block, cancelConsumer, null);
    }

    public void cancel() {
        cancel(true);
    }

    public void cancel(boolean value) {
        if (cancelConsumer != null) {
            cancelConsumer.accept(value);
        }
    }
}
