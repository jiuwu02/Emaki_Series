package emaki.jiuwu.craft.cooking.model;

import java.util.function.Consumer;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public record StationInteraction(Player player,
        Block block,
        boolean leftClick,
        boolean rightClick,
        boolean mainHand,
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
