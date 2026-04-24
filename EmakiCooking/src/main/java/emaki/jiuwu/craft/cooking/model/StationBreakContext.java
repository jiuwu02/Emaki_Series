package emaki.jiuwu.craft.cooking.model;

import java.util.function.Consumer;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public record StationBreakContext(Player player,
        Block block,
        Consumer<Boolean> cancelConsumer) {

    public void cancel() {
        cancel(true);
    }

    public void cancel(boolean value) {
        if (cancelConsumer != null) {
            cancelConsumer.accept(value);
        }
    }
}
